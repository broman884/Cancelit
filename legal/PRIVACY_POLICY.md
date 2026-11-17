# CancelIt Privacy Policy

**Effective date:** 10 November 2026 · **Version:** 1.2

CancelIt is made by Muhammad Ibrahim, trading as GND Studious ("we", "us"), Chennai, India. If you have a question about this policy or your data, email us at gndstudious@gmail.com.

## Summary

Almost everything CancelIt does happens on your phone. CancelIt has no account, no ads, no analytics and no tracking, and we never sell your data or use it for advertising. The optional AI features (18 and over only) use Google's free Gemini service, and Google may use what they send to improve its products, so please don't type sensitive personal information into them.

| Information | Where it goes | How long it's kept |
|---|---|---|
| Your settings, activity log, blocked words and Coach chat | Only your phone | Until you delete it in the app or uninstall CancelIt |
| What the Accessibility service reads (the app in front, on-screen and typed text) | Checked on your phone and not stored | Not stored, apart from the short log entries in section 1 |
| Text sent by the optional AI features (18+ only) | Google, through Firebase AI Logic and the Gemini API's free service | We don't store it. Google may keep it and use it to improve its products (section 4). |
| A Coach reply you report, and the reason you give | Our Google Cloud Firestore database | 90 days |
| An integrity check that the app is genuine | Google (Firebase App Check and Play Integrity) | Under Google's terms |
| An email you send us | Our email inbox | Up to 12 months after we've finished helping you |

## 1. Information that stays on your phone

CancelIt stores the following only in its own private storage on your device. We can't see any of it.

- **Settings:** protected apps, time limits, lock length, blocked words, Study mode topic, the Browser safety and Smart keyword detection switches, your age answer, and the PIN lock (stored only as a salted hash, never the PIN itself).
- **Activity log:** which app or blocked word triggered a lock or check-in, and when. It never records the text you typed or viewed.
- **Usage counters:** how much of each app's time limit you've used and when you last used it, how often a blocked word was triggered, and how much AI the app used today (to stay within its daily limits).
- **Coach chat:** your latest 40 messages.
- **Smart keyword detection:** the related terms the AI listed for your blocked words, and short phrases you confirmed as related.
- **Study mode snippets:** short pieces of on-screen text (up to 140 characters each) collected during a session. They are kept only in the app's memory, never written to storage, and deleted at each topic check (every few minutes) and when the session ends.

You can erase all of this at any time with **Delete all my data** on the Focus tab, or by uninstalling CancelIt.

## 2. The Accessibility service

CancelIt only works through Android's Accessibility service, which you switch on yourself. Before it does anything, CancelIt shows its own explanation and asks for your agreement.

While it's on, CancelIt reads:

- **which app is in front**, to count time against your limits and keep locked apps closed
- **text shown on screen**, to pause a browser showing explicit-content words if you turn on Browser safety (off by default), and during a Study session to check you're on topic
- **text you type in other apps**, to catch your blocked words

Password fields are always skipped. CancelIt never reads phone, emergency or text-messaging apps for blocked words, and never taps, types or sends anything in other apps. This information is checked on your phone and not stored, except for the log entries in section 1. It leaves your phone only for the optional AI features in section 4. It is never used for ads.

## 3. Permissions

- **Accessibility service:** see section 2.
- **Notifications:** to show timers, check-ins and lock countdowns. CancelIt never reads other apps' notifications.
- **Internet:** only for the optional AI features and reports.
- **Checking for certain apps:** CancelIt can check whether common apps (such as YouTube or Instagram) are installed, to show their names in your list. This stays on your phone.

## 4. Optional AI features (18 and over only)

CancelIt asks your age once. If you're under 18, none of the AI features run and nothing in this section is sent.

If you're 18 or older and use these features, CancelIt sends the following to Google's Gemini API:

| Feature | What is sent | When |
|---|---|---|
| Smart keyword detection (off by default) | Short text you type in another app (3 to 100 characters, at most 10 words, and not only everyday words), together with your list of blocked words | After you pause typing, only when the text isn't already recognised on your phone |
| Related terms for a blocked word | The blocked word itself | Four times per word (titles, names, sites and slang, and the topic's adult side), when Smart keyword detection is on |
| Study mode topic check | Your study topic and up to about 1,200 characters of recent on-screen text, each labelled with the name of the app it came from | Every 3 minutes during a session, unless the screen hasn't changed or the topic is clearly on screen |
| Coach | Your recent Coach messages (the latest 12, each shortened) and a summary of your activity: the number of protected apps, your time limits, recent lock and check-in events, your blocked words and how often they were triggered | When you send a Coach message |

CancelIt never sends password fields, your contacts, your location or any device identifier.

**How it travels.** Requests go over an encrypted (HTTPS) connection to Google's Firebase AI Logic service, which passes them to the Gemini API. We don't run a server in between, and we don't store or log the content. Like any internet request, each one also carries your IP address and basic technical details such as the app and Android version.

**How Google uses it.** CancelIt uses the Gemini API's free service. Under Google's terms for that service, Google uses what is sent and the replies to provide, improve and develop its products and machine-learning technologies, and human reviewers may read them. Google says it disconnects the data from our account and project before reviewers see it. Google's Gemini API Additional Terms of Service, the Firebase terms and Google's Privacy Policy apply.

**Please don't send sensitive information.** Because of this, don't type passwords, financial or health details, or other people's private information into apps while Smart keyword detection is on, and don't share them with the Coach. If you'd rather nothing you type leaves your phone, keep Smart keyword detection off and don't use the Coach or Study mode's topic check. Time limits, locks, your own blocked words and check-ins all work without AI.

**Firebase App Check.** To make sure requests come from the genuine CancelIt app, the app uses Google's Firebase App Check with Google Play Integrity. This sends Google an integrity token and basic information about the app and the Firebase software version.

## 5. Reports about Coach replies

If you tap **Report** on a Coach reply, the reply and any reason you write are saved in our Google Cloud Firestore database. Nothing else is attached: not your name, account or device. We use reports to review the Coach's replies and make it safer. They're deleted automatically after 90 days (deletion can take up to a day longer).

If reporting this way isn't available, CancelIt opens an email to us instead, and you can see and edit it before sending. If you send it, we also receive your email address, as with any email (section 6).

## 6. When you contact us

If you email us, we receive your email address and whatever you write. We use it only to reply and to deal with your request. We keep it for up to 12 months after we've finished helping you, unless the law requires us to keep it longer.

## 7. Who receives your information

The only company that receives information from CancelIt is **Google LLC**, through:

- Firebase AI Logic and the Gemini API's free service (the AI features). Google may also use this text for its own purposes, to improve its products (section 4).
- Cloud Firestore (reports), on our behalf.
- Firebase App Check and Google Play Integrity (checking the app is genuine), on our behalf.

We don't use any analytics, advertising or tracking companies. We don't sell your information, rent it, or use it for advertising.

## 8. How long information is kept

- **On your phone:** until you delete it in the app or uninstall CancelIt. Study mode snippets are never saved and are deleted at each topic check.
- **AI requests:** not stored by us. Google keeps and uses them as its terms for the free Gemini service allow (section 4).
- **Reports:** 90 days.
- **Emails to us:** up to 12 months after we've finished helping you.

**Delete all my data** erases everything on your phone. It can't reach a report you already sent, because reports aren't linked to you; see section 9 to have one deleted. We can't delete text that Google has already received through the AI features.

## 9. Your choices and rights

- Turn Smart keyword detection, Study mode or the Coach off at any time.
- Switch the Accessibility service off in your phone's Settings.
- Delete everything on your phone with **Delete all my data**, or uninstall the app.

Depending on where you live (for example under India's Digital Personal Data Protection Act or US state privacy laws), you may have the right to:

- access, correct or delete your personal data
- withdraw your consent at any time, without affecting what was done before
- nominate someone to exercise your rights if you can't
- complain to a data protection authority

We hold no account or profile about you, so the only personal data we might hold is a report or an email you sent. To ask about or delete a report, email gndstudious@gmail.com with roughly when you sent it and the reply's text, so we can find it. We'll answer within 30 days.

**India: grievance officer.** Muhammad Ibrahim, gndstudious@gmail.com.

## 10. Consent

We process data for the AI features and the Accessibility service on the basis of your consent, which you give in the app and can withdraw at any time by turning the feature or permission off. We keep reports to keep the Coach safe.

## 11. Children

CancelIt is meant for adults and isn't directed at children. Its AI features run only for people who confirm they're 18 or older. If you're under 18, nothing CancelIt does leaves your phone. We don't knowingly collect personal information from children. If you believe a child has sent us a report or an email, contact us and we'll delete it.

## 12. Security

- AI requests and reports use HTTPS. Firebase only accepts them when Firebase App Check confirms they come from the genuine app.
- No AI service key is stored in the app.
- Reports can be added from the app but never read from it.
- Your PIN is stored only as a salted hash.
- Everything else stays in CancelIt's private storage on your phone.

No system is perfectly secure. If a security problem ever affects your information, we'll tell you as the law requires.

## 13. Where CancelIt is offered, and international transfers

CancelIt isn't offered in the European Economic Area, Switzerland or the United Kingdom. Google may process AI requests and store reports in countries other than yours, including the United States, under its own safeguards for international transfers.

## 14. US state privacy rights (including California)

In the last 12 months CancelIt has collected these categories of personal information, only as described above:

- text you chose to send to the AI features or in a report
- internet activity information (your IP address and basic technical details, which Google receives with each request)
- your email address, if you emailed us

We don't sell your personal information for money or use it for advertising. Text sent to the AI features is also used by Google to improve its products (section 4). You have the right to know, correct and delete your information, and we won't treat you differently for using these rights. To use them, contact us as in section 9.

## 15. Changes to this policy

If we change this policy, we'll update the date and version above. For significant changes, we'll also tell you in the app before they apply.

## 16. Contact

Muhammad Ibrahim (GND Studious), Chennai, India. Email: gndstudious@gmail.com
