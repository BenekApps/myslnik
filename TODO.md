# TODO

## Before the first release (1.3)

- [ ] Find the CBT reviewer and give them every text: `app/src/main/res/values/strings.xml`,
      `strings_exercises.xml`, `strings_cards.xml` and `helplines.xml`. Ask specifically about:
      no crisis card for exposure ratings, the exposure hint (stay until anxiety drops), the
      sleep advice card.
- [ ] Ukrainian: native speaker review of `values-uk/*` (ideally someone who knows CBT terms).
- [ ] Verify Ukrainian-language help lines in Poland (800 108 108, HelpUkraine.GrupaWsparcia.pl,
      116 111 Ukrainian hours, 800 12 12 12) directly with the organisations; add verified
      ones to the Ukrainian SOS screen.
- [ ] After the review: reviewer's name (with consent) in "O aplikacji" (`about_review_text`).
- [ ] Check "Myślnik" in Google Play and in the trademark registers (UPRP, EUIPO).
- [x] Upload keystore for Myślnik (alias `myslnik`) and GitHub secrets, see CLAUDE.md.
- [x] Play Console: create the app `com.benekapps.myslnik`; the first bundle is uploaded by hand.
- [x] Play service account for CI uploads to internal testing (`PLAY_SERVICE_ACCOUNT_JSON`).
- [ ] Privacy policy online: enable GitHub Pages for `docs/` (or another host), link it in
      Play Console. Add a contact e-mail to the policy and the store listing.
- [x] Play Console declarations for internal testing (2026-10-11): data safety "no data",
      health features "stress/mental wellbeing" + "sleep" (no medical category), category
      Zdrowie i fitness, target audience 18+, content rating questionnaire.
- [ ] Before the public release: privacy policy on GitHub Pages with a contact e-mail (Play
      currently links the GitHub file), re-check the declarations.
- [ ] Store listing (Polish, draft in `distribution/listing/`, needs the CBT review), screenshots (turn off "Ukryj treść…" for them), 512 px icon,
      feature graphic. Ask someone who knows medical law to read the listing and the policy.
- [ ] Play Console → Monetize → In-app products: create one-time products `support_small`,
      `support_medium`, `support_large` with names and prices (e.g. 5, 10, 20 zł). Possible only
      after a bundle with the BILLING permission has been uploaded. Test a purchase with a
      license tester account (Play Console → Settings → License testing).
- [ ] Data safety form: payments are handled by Google Play, the app collects nothing.
- [ ] Closed test: at least 12 testers for 14 days without a break.
- [ ] TalkBack, 200% font and Accessibility Scanner on a real phone.
- [ ] Usability test with 5–8 people (thought record, find SOS, PDF export). People in
      therapy only through their therapist. Ethics committee first if it is part of a thesis.
- [ ] Store listing in Ukrainian too (`distribution/whatsnew/whatsnew-uk`).

## Done in code (2026-10-11)

1.1 behavioural activation, sleep diary, reminders · 1.2 exposure ladder, behavioural
experiments, worry time, problem solving · 1.3 psychoeducation cards, Ukrainian.

## Later

- A direct Buy Me a Coffee link in the app only for builds outside Google Play (F-Droid).

- Reminders for single planned activities (now only daily reminders).
- Sleep diary: support for shift work (sleep across noon).
- Yearly: target the new Android before 31 August; re-check help numbers.
