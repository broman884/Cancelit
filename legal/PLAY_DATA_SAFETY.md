# Google Play Data safety form: suggested answers

These are suggested answers for Play Console > App content > Data safety, based on what the code does as of this version. You are responsible for the final answers. Recheck them whenever a feature changes.

Play's definitions that matter here:

- **Collected** means data is sent off the device, even if only to your own server or to a service provider. Data that only stays on the phone (the accessibility service's on-device checks, the activity log) is **not** collected and isn't declared.
- **Shared** means it's transferred to a third party. Transfers to a service provider that processes data only on your behalf (Cloud Firestore for reports, App Check) are **not** sharing. The AI features use the Gemini API's **free** service (the Firebase project is on the Spark plan), where Google also uses the data to improve its own products, so the text sent to the AI **is** shared. If you move the project to the Blaze plan (the paid service), that text stops being shared: change those answers to "Shared: No".

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS only) |
| Which of the following methods of account creation does your app support? | **None.** The app has no accounts. |
| Do you provide a way for users to request that their data is deleted? | **Yes.** "Delete all my data" in the app clears everything on the phone. Users can also email gndstudious@gmail.com to delete a report they sent. |

## Data types to declare

For every type below:

- **Collected:** Yes
- **Shared:** Yes, with Google, for the types the AI features send (see the table). Purpose of sharing: App functionality.
- **Processed ephemerally:** No. Google keeps and may use the text sent to the free Gemini service, and reports are kept for 90 days, so don't claim ephemeral.
- **Required or optional:** Optional. Each feature is opt-in or only used when the user chooses.
- **Purpose of collection:** App functionality

| Play category > data type | Why it's collected | Feature | Shared? |
|---|---|---|---|
| **App activity > Other user-generated content** | Short text typed in other apps, Coach messages, and the text of a reported Coach reply plus its reason | Smart keyword detection, Coach, Report | Yes (the AI text; reports aren't shared) |
| **App activity > App interactions** | App names attached to Study mode screen snippets, and the activity summary given to the Coach (lock and check-in events, keyword counts) | Study mode, Coach | Yes |
| **Messages > Other in-app messages** | The user's Coach chat messages | Coach | Yes |
| **Web browsing > Web browsing history** | Study mode's on-screen text can come from a browser page | Study mode | Yes |

**Firebase libraries.** Open Firebase's guide "Prepare for Google Play's data disclosure requirements" (firebase.google.com/docs/android/play-data-disclosure) and add whatever it lists for the three Firebase libraries the app uses: App Check with the Play Integrity provider, Firebase AI Logic and Cloud Firestore. For App Check it listed the Play Integrity token and the Firebase user agent, used for app attestation and abuse prevention, when last checked.

**Things to consider declaring, to be safe:**

- **Health and fitness > Health info.** The Coach doesn't ask for health information, but users may type about urges, sleep or mental health. Some developers declare it whenever free-text chat goes to a server. Declaring it is the conservative choice.

**Not collected** (stays on the phone, so not declared): the installed-apps list, the activity log, location, device IDs, crash logs.

## Security practices section

- Data is encrypted in transit: **Yes**
- Users can request data deletion: **Yes**
- Optional: complete an independent security review (MASA). Not required.

## Privacy policy URL

Use the public URL where you host `PRIVACY_POLICY.md` (for example a GitHub Pages or Google Sites page). Put the same URL in `local.properties` as `PRIVACY_POLICY_URL` so the app links to it.
