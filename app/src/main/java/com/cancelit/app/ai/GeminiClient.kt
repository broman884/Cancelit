package com.cancelit.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.cancelit.app.BuildConfig
import com.cancelit.app.data.AGE_ADULT
import com.cancelit.app.data.FOCUS_PREFS
import com.cancelit.app.data.KEY_AGE_GROUP
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.UsageMetadata
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// The one place that talks to Gemini. Every feature goes through GeminiClient.generate(), which
// adds what keeps the token bill small and bounded:
//
//  - BUDGETS: each feature has an hourly and a daily call cap, and all features share a daily
//    token cap per device. Over a cap, calls return null and the caller falls back exactly as
//    it does when offline. Tune the numbers in AiFeature and DAILY_TOKEN_BUDGET.
//  - LOWEST THINKING LEVEL: Gemini 3 Flash thinks at "medium" unless told otherwise, and thinking
//    tokens are billed as output. Every call asks for the lowest level its model accepts
//    (THINKING_LEVELS).
//  - BACK-OFF: after a quota, server or network error, calls pause for a while instead of
//    retrying on every keystroke. A model that isn't available is skipped for the rest of the
//    process.
//  - METERING: the token counts Gemini reports are added up per day, and debug builds log each
//    call under the tag "CancelIt-AI", so the real cost per hour of use can be measured.
//  - NO KEY IN THE APP, IN ANY BUILD: requests go through Firebase AI Logic, which keeps the
//    Gemini key on Google's side and only answers requests carrying a valid Firebase App Check
//    token. Anything inside an APK can be extracted, so a key never goes in one. (The Firebase
//    settings in google-services.json are public by design and can't call Gemini on their own.)
//  - ADULTS ONLY: Gemini's terms require users to be 18+, so nothing is sent until the user has
//    confirmed their age (FocusStore.setAgeGroup). Firebase App Check is only set up then too,
//    so the app never contacts Google for App Check on behalf of anyone younger.
// ---------------------------------------------------------------------------------------------

private const val AI_TAG = "CancelIt-AI"
internal const val AI_USAGE_PREFS = "cancelit_ai_usage"

/** Shared by every feature, per device per day. A safety net, not the normal limit: the
 *  per-feature caps and local filters keep a typical day far below it. Debug builds get far
 *  more, so testing doesn't run into it; release builds keep the limit the cost estimates use. */
private val DAILY_TOKEN_BUDGET = if (BuildConfig.DEBUG) 1_000_000 else 120_000

/** How long a model that answered "high demand" is skipped before it's tried again. */
private const val BUSY_MODEL_MS = 2 * 60_000L

private const val BACKOFF_START_MS = 30_000L
private const val BACKOFF_MAX_MS = 15 * 60_000L

/** How long a reported Coach reply is kept in Firestore (a TTL policy on `expireAt` deletes it). */
private const val REPORT_KEEP_MS = 90L * 24 * 60 * 60 * 1000

/** Cheap, fast models for everything paid for again and again (live keyword checks, Study checks
 *  and the Coach), cheapest first. If one is unavailable the next is used automatically. Both
 *  support structured (JSON) output. */
internal val LITE_MODELS = listOf("gemini-3.1-flash-lite", "gemini-3.5-flash-lite")

/** Models for the related-terms lists: made once per keyword, and they need wide knowledge of
 *  titles, names and slang, which the Lite models have less of. */
internal val FULL_MODELS = listOf("gemini-3.8-flash", "gemini-3.6-flash")

/**
 * The lowest thinking level each model accepts, from the "Thinking" page of the Firebase AI Logic
 * docs. A model missing here runs at its own default. A model that rejects its level is skipped
 * like an unavailable one, with an error in Logcat.
 */
private val THINKING_LEVELS = mapOf(
    "gemini-3.1-flash-lite" to ThinkingLevel.MINIMAL,
    "gemini-3.5-flash-lite" to ThinkingLevel.MINIMAL,
    "gemini-3.8-flash" to ThinkingLevel.LOW,
    "gemini-3.6-flash" to ThinkingLevel.MINIMAL
)

/**
 * Per-device call caps for each feature. Release builds use the first pair, which keeps a phone
 * at about 510 calls a day at most; debug builds use the much higher second pair, so testing
 * doesn't keep hitting them. The Coach's is the tightest: every message costs, and a long
 * conversation resends its context each time.
 */
internal enum class AiFeature(perHour: Int, perDay: Int, debugPerHour: Int, debugPerDay: Int) {
    KEYWORD_CHECK(perHour = 60, perDay = 300, debugPerHour = 120, debugPerDay = 600),
    RELATED_TERMS(perHour = 16, perDay = 32, debugPerHour = 50, debugPerDay = 200), // four calls per new keyword
    STUDY_CHECK(perHour = 20, perDay = 160, debugPerHour = 100, debugPerDay = 500),
    COACH(perHour = 10, perDay = 15, debugPerHour = 120, debugPerDay = 600);

    val callsPerHour = if (BuildConfig.DEBUG) debugPerHour else perHour
    val callsPerDay = if (BuildConfig.DEBUG) debugPerDay else perDay
}

internal object GeminiClient {

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var focusPrefs: SharedPreferences? = null
    @Volatile private var appContext: Context? = null
    /** google-services.json is in this build, so Firebase (and with it the AI) can work. */
    @Volatile private var firebaseConfigured = false
    /** App Check is set up. Only ever true once the user has said they're 18 or older. */
    @Volatile private var appCheckReady = false
    @Volatile private var ai: FirebaseAI? = null
    private val lock = Any()

    /** Successful calls in the last hour, per feature (in memory; a restart only resets this). */
    private val recentCalls = HashMap<AiFeature, ArrayDeque<Long>>()
    /** Daily counters, mirrored to prefs when [init] has been called. */
    private var day = ""
    private var tokensToday = 0
    private val callsToday = HashMap<AiFeature, Int>()

    @Volatile private var failures = 0
    @Volatile private var pausedUntil = 0L

    private val deadModels = ConcurrentHashMap.newKeySet<String>()

    /** Models Google said were overloaded, and until when they're skipped. */
    private val busyUntil = ConcurrentHashMap<String, Long>()

    private fun isBusy(model: String): Boolean = System.currentTimeMillis() < (busyUntil[model] ?: 0L)

    /** Call once from each Android entry point (activity, accessibility service). Without it,
     *  budgets still work but reset when the process restarts. */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(lock) {
            if (prefs != null) return
            val app = context.applicationContext
            appContext = app
            focusPrefs = app.getSharedPreferences(FOCUS_PREFS, Context.MODE_PRIVATE)
            // Only checks the build's settings; App Check itself waits for ensureAppCheck().
            firebaseConfigured = FirebaseOptions.fromResource(app) != null
            prefs = app.getSharedPreferences(AI_USAGE_PREFS, Context.MODE_PRIVATE)
            day = "" // reload today's counters from prefs
            rollDay()

            // Debug builds only: set App Check up at launch, so the debug token to register in
            // the Firebase console shows in Logcat straight away. AI calls still wait for the
            // 18+ answer (canCall). Release builds wait for that answer before App Check too.
            if (BuildConfig.DEBUG && firebaseConfigured && !appCheckReady) {
                appCheckReady = installAppCheck(app)
            }
        }
    }

    /**
     * Sets up App Check the first time a request needs it, and only for a user who said they're
     * 18 or older. Returns whether it's ready. If setup fails, AI stays off until the app restarts.
     */
    private fun ensureAppCheck(): Boolean {
        if (appCheckReady) return true
        val context = appContext ?: return false
        if (!firebaseConfigured || !adultConfirmed()) return false
        synchronized(lock) {
            if (!appCheckReady) {
                appCheckReady = installAppCheck(context)
                if (!appCheckReady) firebaseConfigured = false
            }
            return appCheckReady
        }
    }

    /** False when google-services.json is missing, so App Check (and the AI) can't work. */
    private fun installAppCheck(context: Context): Boolean = try {
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
        if (app == null) {
            Log.w(AI_TAG, "Firebase isn't configured (no google-services.json); AI features are off")
            false
        } else {
            FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(appCheckProviderFactory())
            true
        }
    } catch (e: Exception) {
        note("App Check setup failed: ${e.javaClass.simpleName} (${e.message})")
        false
    }

    /** Firebase AI Logic on the Gemini Developer API, created once App Check is ready. */
    private fun aiService(): FirebaseAI? {
        ai?.let { return it }
        if (!ensureAppCheck()) return null
        synchronized(lock) {
            ai?.let { return it }
            return try {
                Firebase.ai(backend = GenerativeBackend.googleAI()).also { ai = it }
            } catch (e: Exception) {
                note("Firebase AI Logic setup failed: ${e.javaClass.simpleName} (${e.message})")
                null
            }
        }
    }

    /** Whether this build can reach Gemini at all: it has the Firebase settings. */
    fun isConfigured(): Boolean = firebaseConfigured

    /** The user confirmed they're 18 or older. */
    fun adultConfirmed(): Boolean = focusPrefs?.getString(KEY_AGE_GROUP, null) == AGE_ADULT

    /** True if a call for [feature] would be allowed right now. Never makes a network call. */
    fun canCall(feature: AiFeature): Boolean {
        if (!isConfigured() || !adultConfirmed()) return false
        // Only pause after several failures in a row, so one hiccup doesn't switch AI off.
        if (failures >= 3 && System.currentTimeMillis() < pausedUntil) return false
        return !overLimit(feature)
    }

    /** True when [feature] has used its hourly or daily calls, or the device its daily tokens.
     *  Lets the caller tell "limit reached" apart from "couldn't connect". */
    fun overLimit(feature: AiFeature): Boolean = synchronized(lock) {
        rollDay()
        if (tokensToday >= DAILY_TOKEN_BUDGET) return true
        if ((callsToday[feature] ?: 0) >= feature.callsPerDay) return true
        val recent = recentCalls.getOrPut(feature) { ArrayDeque() }
        val now = System.currentTimeMillis()
        while (recent.isNotEmpty() && now - recent.first() > 60 * 60_000L) recent.removeFirst()
        recent.size >= feature.callsPerHour
    }

    /**
     * One request to [model]. Returns the reply text (thoughts left out), or null when the call
     * isn't allowed, the model is unavailable, or anything fails. Never throws; every caller is
     * expected to fail open.
     */
    suspend fun generate(
        feature: AiFeature,
        model: String,
        contents: List<Content>,
        maxOutputTokens: Int,
        systemInstruction: String? = null,
        responseSchema: Schema? = null,
        readTimeoutMs: Int = 60_000
    ): String? = withContext(Dispatchers.IO) {
        if (model in deadModels || isBusy(model) || !canCall(feature)) return@withContext null
        val service = aiService() ?: run {
            note("Firebase AI Logic unavailable: check App Check and google-services.json")
            return@withContext null
        }

        val thinkingLevel = THINKING_LEVELS[model]
        val config = generationConfig {
            this.maxOutputTokens = maxOutputTokens
            if (responseSchema != null) {
                responseMimeType = "application/json"
                this.responseSchema = responseSchema
            }
            if (thinkingLevel != null) thinkingConfig = thinkingConfig { this.thinkingLevel = thinkingLevel }
        }
        val generativeModel = service.generativeModel(
            modelName = model,
            generationConfig = config,
            systemInstruction = systemInstruction?.let { instruction -> content { text(instruction) } },
            requestOptions = RequestOptions(readTimeoutMs.toLong())
        )

        try {
            val response = generativeModel.generateContent(contents)
            failures = 0
            pausedUntil = 0L
            record(feature, model, response.usageMetadata)
            val text = response.text?.trim()
            if (text.isNullOrEmpty()) {
                Log.w(AI_TAG, "$model gave no text (finishReason=${response.candidates.firstOrNull()?.finishReason?.name})")
                return@withContext null
            }
            text
        } catch (e: CancellationException) {
            throw e // a newer keystroke cancelled this check; not a failure
        } catch (e: PromptBlockedException) {
            Log.w(AI_TAG, "$model blocked the prompt")
            null
        } catch (e: ResponseStoppedException) {
            // The reply was cut off (length limit or safety). Tokens were still used.
            record(feature, model, e.response.usageMetadata)
            Log.w(AI_TAG, "$model stopped early (finishReason=${e.response.candidates.firstOrNull()?.finishReason?.name})")
            null
        } catch (e: RequestTimeoutException) {
            // This model was too slow this time: skip it for a while (the next model is tried),
            // rather than pausing every AI feature.
            note("$model timed out; skipping it for ${BUSY_MODEL_MS / 60_000} minutes")
            busyUntil[model] = System.currentTimeMillis() + BUSY_MODEL_MS
            null
        } catch (e: QuotaExceededException) {
            note("$model quota exceeded: ${e.message}")
            pause("$model quota exceeded")
            null
        } catch (e: ServerException) {
            val message = e.message.orEmpty()
            note("$model server error: $message")
            when {
                // Firebase AI Logic itself isn't set up: no model will work, so don't blame this one.
                message.contains("genai config", ignoreCase = true) -> {
                    note("Firebase AI Logic isn't set up for this project (console: AI Logic > Get started > Gemini Developer API)")
                    pause("AI Logic not set up")
                }
                // Google is overloaded for this model (common on the free tier at busy times).
                // Only this model is skipped for a while; the others, and every other feature,
                // keep working, instead of all AI pausing.
                message.contains("high demand", ignoreCase = true) || message.contains("overloaded", ignoreCase = true) ||
                        message.contains("unavailable", ignoreCase = true) || message.contains("503") -> {
                    note("$model is busy; skipping it for ${BUSY_MODEL_MS / 60_000} minutes")
                    busyUntil[model] = System.currentTimeMillis() + BUSY_MODEL_MS
                }
                // This model doesn't exist (any more) for this project: try the next one from now on.
                message.contains("models/", ignoreCase = true) || message.contains(model, ignoreCase = true) -> {
                    note("$model not available; skipping it from now on")
                    deadModels += model
                }
                thinkingLevel != null && message.contains("thinking", ignoreCase = true) -> {
                    note("$model rejected its thinking level; fix THINKING_LEVELS. Skipping it from now on")
                    deadModels += model
                }
                else -> pause("$model server error")
            }
            null
        } catch (e: Exception) {
            note("$model failed: ${e.javaClass.simpleName} (${e.message})")
            pause("$model ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Logs an AI problem. Debug builds also keep the latest five in the usage file as
     * "debug_errors", because some phones (vivo, for one) hide apps' log lines; read them with
     * `adb shell run-as com.gndstudious.cancelit cat shared_prefs/cancelit_ai_usage.xml`. They
     * never hold what the user typed: only the error and the model.
     */
    private fun note(message: String) {
        Log.w(AI_TAG, message)
        if (!BuildConfig.DEBUG) return
        val p = prefs ?: return
        synchronized(lock) {
            val earlier = p.getString("debug_errors", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(4)
            p.edit { putString("debug_errors", (earlier + "${clockStamp()} ${message.take(300)}").joinToString("\n")) }
        }
    }

    private fun clockStamp(): String = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())

    /**
     * Saves a user's report about an AI reply to Cloud Firestore (collection `reports`), where
     * the developer reviews it (Google Play's AI-generated content policy asks for reporting
     * inside the app). Firestore checks App Check, and its security rules only allow adding a
     * report, never reading one. Returns false when Firebase isn't set up or the report was
     * refused, so the caller can fall back to email.
     */
    suspend fun reportReply(reply: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        if (!ensureAppCheck()) return@withContext false
        val report = mapOf(
            "reply" to reply.take(4_000),
            "reason" to reason.take(1_000),
            "at" to FieldValue.serverTimestamp(),
            "expireAt" to Timestamp(Date(System.currentTimeMillis() + REPORT_KEEP_MS))
        )
        try {
            Tasks.await(reportsCollection().add(report), 10, TimeUnit.SECONDS)
            true
        } catch (e: TimeoutException) {
            true // offline: Firestore keeps the write and sends it once the phone is back online
        } catch (e: Exception) {
            Log.w(AI_TAG, "Report failed: ${e.javaClass.simpleName}")
            false
        }
    }

    @Volatile private var reports: CollectionReference? = null

    /** The `reports` collection. Firestore's cache stays in memory, so nothing about a report is
     *  left on the phone (and "Delete all my data" has nothing to miss). */
    private fun reportsCollection(): CollectionReference = synchronized(lock) {
        reports ?: Firebase.firestore.apply {
            firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
        }.collection("reports").also { reports = it }
    }

    /** Tries [models] in order until [parse] accepts a reply. */
    suspend fun <T> firstUsable(models: List<String>, call: suspend (String) -> String?, parse: (String) -> T?): T? {
        for (model in models) {
            if (model in deadModels || isBusy(model)) continue
            val parsed = call(model)?.let(parse)
            if (parsed != null) return parsed
        }
        return null
    }

    /** Pauses every AI call for a growing interval after a failure that retrying won't fix. */
    private fun pause(reason: String) {
        val wait = minOf(BACKOFF_START_MS shl failures.coerceAtMost(5), BACKOFF_MAX_MS)
        failures++
        pausedUntil = System.currentTimeMillis() + wait
        Log.w(AI_TAG, "$reason; pausing AI calls for ${wait / 1000}s")
    }

    private fun record(feature: AiFeature, model: String, usage: UsageMetadata?) {
        val tokens = usage?.totalTokenCount?.takeIf { it > 0 }
            ?: ((usage?.promptTokenCount ?: 0) + (usage?.candidatesTokenCount ?: 0) + (usage?.thoughtsTokenCount ?: 0))
        synchronized(lock) {
            rollDay()
            recentCalls.getOrPut(feature) { ArrayDeque() }.addLast(System.currentTimeMillis())
            callsToday[feature] = (callsToday[feature] ?: 0) + 1
            tokensToday += tokens
            prefs?.edit {
                putInt("tokens", tokensToday)
                putInt("calls_${feature.name}", callsToday[feature] ?: 0)
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                AI_TAG,
                "${feature.name} via $model: in=${usage?.promptTokenCount} out=${usage?.candidatesTokenCount} " +
                        "thought=${usage?.thoughtsTokenCount} total=$tokens, today=$tokensToday/$DAILY_TOKEN_BUDGET"
            )
        }
    }

    /** Starts fresh counters when the local date changes. Caller holds [lock]. */
    private fun rollDay() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        if (today == day) return
        day = today
        val p = prefs
        if (p != null && p.getString("day", "") == today) {
            tokensToday = p.getInt("tokens", 0)
            AiFeature.entries.forEach { callsToday[it] = p.getInt("calls_${it.name}", 0) }
        } else {
            tokensToday = 0
            callsToday.clear()
            p?.edit {
                clear()
                putString("day", today)
            }
        }
    }
}

/** A single user turn, for one-shot requests. */
internal fun userTurn(prompt: String): List<Content> = listOf(content(role = "user") { text(prompt) })
