# Legal drafts

Drafts written from what the app's code does. They are **not legal advice**. Have a lawyer in your country review them before you publish.

| File | Use it for |
|---|---|
| `PRIVACY_POLICY.md` | Host at a public URL. Put that URL in Play Console and in `local.properties` as `PRIVACY_POLICY_URL`. |
| `TERMS_OF_USE.md` | Host at a public URL. Put it in `local.properties` as `TERMS_URL`. |
| `PLAY_DATA_SAFETY.md` | Answers for Play Console > App content > Data safety. |
| `PLAY_ACCESSIBILITY_DECLARATION.md` | Text and video script for the Accessibility API declaration. |

The drafts are filled in for Muhammad Ibrahim, trading as GND Studious, Chennai, India (gndstudious@gmail.com), with no postal address, Indian law and the courts of Chennai, a ₹1,000 liability cap, and an effective date of 10 November 2026. Update the date if you publish later.

**Written for the Firebase Spark plan** (the Gemini API's free service). That choice shows up in three places, and all three must change together if you move to Blaze (the paid service):

- the privacy policy (summary, sections 4, 7, 8 and 14 say Google may use the AI text to improve its products)
- the Data safety answers (the AI text is "shared" on the free service)
- the terms (section 4) and the in-app wording that tell users not to send sensitive information

On the free service, Google's terms don't allow apps offered in the European Economic Area, Switzerland or the United Kingdom, so the policy and terms say CancelIt isn't offered there. **Exclude those countries in Play Console** (Production → Countries / regions).

If you change what the app sends or stores, update the policy, the Data safety form, the in-app privacy screen (`PrivacyPolicyScreen` in ui/screens/OnboardingScreens.kt) and the Accessibility disclosure (`AccessibilityDisclosureScreen`) together. Google checks that they match.
