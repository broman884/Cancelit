# Firebase setup for CancelIt

CancelIt's AI features run on **Firebase AI Logic**, and reported Coach replies are saved in **Cloud Firestore**. There's no server of your own to deploy and no Gemini API key to handle: Firebase keeps access to Gemini on Google's side and only answers requests that **Firebase App Check** confirms come from the genuine app.

Until `app/google-services.json` exists, the app builds without Firebase and its AI features stay off. Everything else in the app works without Firebase.

## 1. Create the project

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and create a project called **CancelIt**. When it asks about Google Analytics, turn it off: CancelIt doesn't use it, and the privacy policy says so.
2. Upgrade the project to the **Blaze** (pay-as-you-go) plan by linking a Cloud Billing account. This puts Gemini on its paid tier, where Google doesn't use prompts to improve its products, as the privacy policy promises. On the free Spark plan that promise would be false.
3. In the Google Cloud console, open **Billing → Budgets & alerts** and set a small monthly budget (for example $5) so you get an email if spending grows.
4. If the billing account belongs to someone with Google AI Pro, they can claim the monthly Google Developer Program Cloud credits for it. Those credits can pay for Gemini API use.

## 2. Turn on Firebase AI Logic

1. In the Firebase console, open **AI Logic** and click **Get started**.
2. Choose the **Gemini Developer API**.
3. There's no key to copy. Never put a Gemini API key in the app or in `local.properties`.

## 3. Add the Android app

1. On the project overview, click **Add app → Android**. Enter the package name `com.gndstudious.cancelit` exactly (it must match `applicationId` in `app/build.gradle.kts`). Never copy a second entry into `google-services.json` by hand: Firebase and Play Integrity only accept the package the Firebase app was registered with.
2. Download `google-services.json` and put it in `app/`, next to `app/build.gradle.kts`.
3. Skip the "add the SDK" steps: the code already has them.

`google-services.json` holds public settings, not secrets, but `.gitignore` keeps it out of git anyway.

## 4. App Check

1. Open **Security → App Check → Apps** and register the Android app with **Play Integrity**. It asks for your app signing key's SHA-256 fingerprint, which you'll find on Play Console's **App integrity → App signing** page once the app exists there.
2. **Testing a debug build before that:** debug builds use a debug provider instead of Play Integrity.
   1. Install the debug APK, answer the age question as 18 or older, and turn on an AI feature (for example Smart keyword detection with one blocked word).
   2. In Logcat, find the line that starts with `Enter this debug secret into the allow list in the Firebase Console`, and copy the token after it.
   3. In **App Check → Apps**, open the app's menu, choose **Manage debug tokens** and add it.
   4. Keep that token private: it lets any device pass App Check. Don't ship a debug build.
3. **Turn on enforcement.** In **Security → App Check → APIs**, click **Enforce** for **Firebase AI Logic** and for **Cloud Firestore**. Firebase has required enforcement for AI Logic since November 2, 2026. It can take up to 15 minutes to take effect.

Firebase also recommends "limited-use tokens" for replay protection. CancelIt doesn't turn them on yet: they'd need a fresh Play Integrity check for every AI call, and Play Integrity's default daily quota would run out quickly. Revisit this if you raise that quota.

## 5. Cloud Firestore (reported Coach replies)

Google Play requires a way to report AI replies without leaving the app. The Coach's **Report** link saves the reply and the user's optional reason in a `reports` collection.

1. Open **Build → Firestore Database → Create database**. Choose **production mode** and a location close to your users (for example `eur3` for Europe or `nam5` for the US). You can name the location in the privacy policy (section 10 has a placeholder).
2. Open the **Rules** tab, replace everything with the rules below, and click **Publish**:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       // Reported Coach replies: the app can add one. Nobody can read, change or delete them
       // through the app. Review them in the Firebase console.
       match /reports/{report} {
         allow create: if request.resource.data.keys().hasOnly(['reply', 'reason', 'at', 'expireAt'])
           && request.resource.data.reply is string
           && request.resource.data.reply.size() <= 4000
           && request.resource.data.reason is string
           && request.resource.data.reason.size() <= 1000
           && request.resource.data.at == request.time
           && request.resource.data.expireAt is timestamp;
       }
     }
   }
   ```

3. **Delete reports after 90 days** (the privacy policy promises this). In the Google Cloud console, open **Firestore → Time-to-live → Create policy**. Enter the collection group `reports` and the timestamp field `expireAt`. Google usually deletes expired documents within 24 hours of the date.
4. **Reviewing reports:** Firebase console → **Firestore Database → Data → reports**.

If Firestore isn't set up or refuses a report, the app opens an email to `SUPPORT_EMAIL` instead.

## 6. Check the Firebase API key

The key in `google-services.json` is meant to be public, so lock down what it can do. In the Google Cloud console, open **APIs & Services → Credentials** and click **Android key (auto created by Firebase)**:

- **API restrictions:** make sure **Firebase AI Logic API** is in the list and **Generative Language API is not** (Firebase strongly recommends this). Leave the other Firebase entries as they are.
- **Before release:** under **Application restrictions**, choose **Android apps** and add `com.gndstudious.cancelit` with the SHA-1 fingerprints of your debug key and your app signing key. If a fingerprint is missing, that build's AI requests will fail.

## 7. Limits

- **Per-user limit.** Firebase AI Logic allows 100 requests a minute per user by default. The app caps itself far lower (6 a minute for live keyword checks), so you can lower Firebase's limit, for example to 20: Google Cloud console → **APIs & Services → Firebase AI Logic API → Manage → Quotas & System Limits**, filter for **Generate content requests**, then **Edit quota**.
- **Per-phone caps** in the app (`AiFeature` and `DAILY_TOKEN_BUDGET` in `ai/GeminiClient.kt`) stop any one phone from making more than about 510 AI calls a day in a release build (the Coach: 15 messages a day). Debug builds allow far more, so testing doesn't hit them.

## 8. `local.properties`

```
SUPPORT_EMAIL=you@example.com
PRIVACY_POLICY_URL=https://example.com/cancelit/privacy
TERMS_URL=https://example.com/cancelit/terms
```

There's no AI setting here.

## 9. Delete old Gemini keys

CancelIt no longer uses any Gemini API key. In [Google AI Studio](https://aistudio.google.com) → **API keys**, delete every key that was ever in an APK, a zip, `local.properties` or a chat.

## 10. Test it

1. Build and install the debug APK, with `app/google-services.json` in place and the debug token registered (step 4).
2. Answer the age question as 18 or older, turn on **Smart keyword detection** and add a blocked word. Within about a minute the word should show "N related terms".
3. Send the Coach a message, then tap **Report** under its reply. The report should appear in Firestore → `reports`.
4. If nothing works, filter Logcat by `CancelIt-AI`. Common causes:
   - the debug token isn't registered (App Check refuses the request)
   - the project isn't on Blaze, or AI Logic isn't set up ("genai config not found")
   - the Firebase API key's restrictions block Firebase AI Logic API

Firebase recommends separate Firebase projects for development and production once the app is live. That's optional for a first release.
