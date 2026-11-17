# Google Play Accessibility API declaration: suggested text

For Play Console > App content > Sensitive permissions and APIs > Accessibility API. CancelIt is **not** an accessibility tool for people with disabilities, so it does not set `isAccessibilityTool`. Answer as a non-accessibility-tool app.

## 1. Purpose of using the AccessibilityService API

Select: **App functionality**

## 2. Describe how your app uses the API (paste into the description field)

> CancelIt is a digital wellbeing app that helps users limit their own use of distracting apps. With the user's consent, its AccessibilityService:
> (1) detects which app is in the foreground, to enforce the per-app time limits and lock periods the user sets, by returning to the home screen and showing a full-screen notice;
> (2) reads text the user types in other apps (never password fields), to detect words the user has added to their own block list and close that app;
> (3) if the user turns on Browser safety (off by default), reads visible text in browsers to pause the browser when explicit-content terms appear;
> (4) during a Study mode session the user starts, reads short on-screen text snippets to check the user stays on their chosen topic.
> All actions follow fixed rules the user configures. When an AI classifier only suspects that typed or on-screen text matches a blocked topic, the app shows a question and acts only if the user taps "Close app"; AI never takes actions on its own. Phone, emergency and SMS apps are never locked. The app shows a separate prominent disclosure, and asks for consent, before directing the user to enable the service, and the service ignores all events until the user has agreed.

## 3. Does your app collect or share personal and sensitive user data via the API?

**Yes, collected (not shared).** Only for opt-in features used by adults (18+):

- Short typed text (Smart keyword detection) and short on-screen text snippets (Study mode) are sent over HTTPS through Google's Firebase AI Logic to Google's Gemini API to classify the topic. They are not stored by the developer. Because the app uses the Gemini API's free service, Google may use them to improve its products, and the in-app texts and privacy policy say so.
- Select data types: **App activity > Other user-generated content**, **App activity > App interactions**, **Web browsing > Web browsing history**. These match the Data safety form.

## 4. Prominent disclosure

Where it appears in the app: tapping **Turn on** next to "Accessibility access" on the Focus tab opens the full-screen **Accessibility permission** screen (`AccessibilityDisclosureScreen` in ui/screens/OnboardingScreens.kt). It is separate from the privacy policy and requires tapping **Agree and open Settings**. **Not now** declines.

## 5. Video (required), about 60 to 90 seconds

Record on a real phone with a fresh install (screen recording on, no other apps visible).

1. Open CancelIt. Show the splash, accept the privacy policy and answer the age question.
2. On the Focus tab, tap **Turn on** next to "Accessibility access".
3. Show the **Accessibility permission** screen and scroll through all four sections.
4. **Non-consent path:** tap **Not now**. Show that you're back in the app and Accessibility is still off.
5. **Consent path:** tap **Turn on** again, then **Agree and open Settings**. In Settings, open CancelIt and switch the service on. Show Android's own confirmation dialog and accept.
6. Return to CancelIt and show the status is now on.
7. **Core feature:** add a blocked word on the List tab (for example "casino"). Open Chrome, type the word in the search bar, and show the app closing with the "Keyword blocker" screen.
8. Optional: show the AI question ("Close app" / "I'm fine") for a related term, to show AI never acts on its own.

Upload the video unlisted (for example to YouTube) and paste the link.

## 6. Other apps

The service never taps, types or sends anything inside other apps. Its only actions are returning to the home screen and drawing CancelIt's own full-screen notice. CancelIt doesn't use notification access (NotificationListenerService).
