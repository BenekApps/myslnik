# TODO

## Before the first release (1.0)

- [ ] Find the CBT reviewer and give them every text: `app/src/main/res/values/strings.xml`
      (hints, examples, distortions, crisis card, onboarding) and `helplines.xml`.
- [ ] After the review: reviewer's name (with consent) in "O aplikacji" (`about_review_text`).
- [ ] Check "Myślnik" in Google Play and in the trademark registers (UPRP, EUIPO).
- [ ] Upload keystore for Myślnik (alias `myslnik`) and GitHub secrets, see CLAUDE.md.
- [ ] Play Console: create the app `com.benekapps.myslnik`; the first bundle is uploaded by hand.
- [ ] Privacy policy online: enable GitHub Pages for `docs/` (or another host), link it in
      Play Console. Add a contact e-mail to the policy and the store listing.
- [ ] Data safety form: no data collected or shared. Health apps declaration: Mental and
      Behavioral Health. Target audience: 18+.
- [ ] Store listing (Polish), screenshots (turn off "Ukryj treść…" for them), 512 px icon,
      feature graphic. Ask someone who knows medical law to read the listing and the policy.
- [ ] Closed test: at least 12 testers for 14 days without a break.
- [ ] TalkBack, 200% font and Accessibility Scanner on a real phone.
- [ ] Usability test with 5–8 people (thought record, find SOS, PDF export). People in
      therapy only through their therapist. Ethics committee first if it is part of a thesis.

## 1.1

- [ ] Behavioural activation: activity plan, pleasure and mastery 0–10.
- [ ] Sleep diary with sleep efficiency (unit tests).
- [ ] Exercise reminders with WorkManager (no exact alarms). Needs POST_NOTIFICATIONS.
- [ ] Database version 2 with a migration and a migration test.

## Later

- 1.2: exposure ladder (anxiety 0–100 before/after), behavioural experiments, worry time,
  problem solving.
- 1.3: psychoeducation cards; Ukrainian (add `uk` to `localeFilters`).
- Yearly: target the new Android before 31 August; re-check help numbers.
