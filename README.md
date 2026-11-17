# CancelIt

An Android-only Jetpack Compose focus app. It has a black-and-white UI, per-app time limits followed by an app lock, an optional browser safety pause for visible explicit-content signals (off by default), a keyword blocker, Study mode check-ins, a local activity report, and an optional AI Coach.

## Run it

1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync.
2. Run on a physical Android device; an emulator cannot accurately exercise app-launch detection.
3. Accept the privacy policy and answer the age question (AI features are 18+ only).
4. In **Focus**, tap **Turn on** for Accessibility. CancelIt shows its own disclosure first, then opens Settings.
5. Choose the apps to protect.

Unit tests (time formatting, saved lists, Smart keyword parsing) run on your computer:

```bash
.\gradlew.bat test
```

## Code layout

All code is in `app/src/main/java/com/cancelit/app/`:

| Folder | What's there |
|---|---|
| `data/` | `FocusStore` (settings and logs), the timing rules in `FocusRules.kt`, and `deleteAllData` |
| `ai/` | `GeminiClient` (every Gemini call), Smart keyword detection, the Coach prompt and the Study topic check |
| `service/` | `FocusAccessibilityService`, its overlays and notifications |
| `ui/` | `DesignSystem.kt` (colours, text styles and shared components), the app shell, the splash, and one file per screen in `ui/screens/` |

Settings live in `cancelit_focus`. The activity log, keyword hits and Coach chat each have their own SharedPreferences file, and every list is saved as JSON. Time used in a protected app is counted in memory and saved every 30 seconds, and Study mode's screen snippets are never saved at all.

## `local.properties` keys

Copy `local.properties.example` to `local.properties` and fill it in. `local.properties` holds secrets and paths specific to your computer, so `.gitignore` keeps it (and build output) out of git. Never share or zip it with the code.

| Key | Needed for |
|---|---|
| `SUPPORT_EMAIL` | Fallback for the Coach's **Report** link when Firestore can't take the report. |
| `PRIVACY_POLICY_URL`, `TERMS_URL` | Links shown on the in-app privacy screen. Required for Play. |
| `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` | Signing release builds. |

## AI setup (Firebase)

AI features go through Firebase AI Logic. There's no server of our own and no Gemini API key in `local.properties` or the app, in any build: anything inside an APK can be extracted. Firebase keeps the key on Google's side and only answers requests that Firebase App Check confirms come from the genuine app. Reported Coach replies are saved in Cloud Firestore.

Everything needs `app/google-services.json`. Until that file exists, the app builds without Firebase and its AI features stay off. Setup steps are in [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

## Before publishing on Google Play

See [legal/README.md](legal/README.md): the privacy policy, terms of use, Data safety answers and Accessibility declaration drafts. Fill in the placeholders and have a lawyer review them.

## Important implementation boundaries

Android does not offer normal apps a permission to permanently disable another app. CancelIt therefore uses a user-enabled Accessibility Service to put a full-screen focus overlay above a protected app when its timer is active. Removing or disabling the service stops enforcement. The service ignores every event until the user has accepted the in-app Accessibility disclosure.

Only rules the user sets act on their own (time limits, their exact blocked words and phrases they confirmed, Browser safety if they turned it on, missed check-ins). Anything the AI infers (related terms, live keyword checks, Study mode topic checks) asks the user first with "Close app" / "I'm fine". Nothing is blocked by default: a topic, explicit or not, is only blocked once the user lists it. Phone, emergency and SMS apps are never locked, and an emergency unlock on the Focus tab clears every lock. The service never taps, types or sends anything inside other apps.

The browser detector only sees text Android exposes through the browser's accessibility tree. Private browsing, inaccessible page layouts, DNS-level sites, and new browser versions can prevent detection. For production-grade web filtering, add an explicit local VPN / DNS filtering component and a transparent allow-list; do not claim universal coverage.

## AI cost controls

Every Gemini call goes through `ai/GeminiClient.kt`. It:

- asks for the lowest thinking level each model accepts (`THINKING_LEVELS`)
- pauses after errors
- enforces per-feature hourly and daily call caps (`AiFeature`)
- enforces a per-device daily token cap (`DAILY_TOKEN_BUDGET`)
- uses much higher caps in debug builds, so testing doesn't run into them; release builds keep the lower ones
- refuses every call unless the user said they're 18 or older

Debug builds log each call's token counts under the Logcat tag `CancelIt-AI`. Model choices are `LITE_MODELS` (everything paid for repeatedly: live keyword checks, Study checks and the Coach) and `FULL_MODELS` (the related-terms lists, made once per keyword and needing wide knowledge). Keep `THINKING_LEVELS` in step with them, and check the models page of the Firebase AI Logic docs for retirement dates. Smart keyword detection answers on the phone first (related terms, learned phrases, an everyday-words filter) and only sends short, unknown text to the AI.
