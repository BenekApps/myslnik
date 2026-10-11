# Myślnik – notes for Claude

Read this first. The product plan is the Claude Docs document "Darmowa aplikacja CBT po polsku —
plan" (https://claude.ai/artifact/QmgJComfVWJzw6XRcJrmH3); decisions taken since are in
`docs/DECISIONS.md`, next steps in `TODO.md`.

## Working with Mat (the owner)

- Talk to Mat in **Polish**. Code, comments and commits in **English**.
- Production quality. Optimize and improve proactively (performance, size, quality), not only
  what is asked.
- With every new build, give Mat a short Google Play "Co nowego" text and put it in
  `distribution/whatsnew/whatsnew-pl-PL` and `whatsnew-uk` (max 500 characters each,
  uploaded with the release).
- Every new user-facing text goes into both `values/` and `values-uk/`.
- Decisions go to `docs/DECISIONS.md`, tasks to `TODO.md`, user-facing text to resources,
  never into code.
- Never ask for secrets in chat or put them in the repo. They live in GitHub Actions secrets.

## Product rules (from the plan)

- Free, no ads, nothing locked.
- Data only on the phone: Room + SQLCipher, key in the Android Keystore, PIN or fingerprint
  lock. No accounts, server or analytics. **No INTERNET permission** (CI checks it).
- SOS on every screen, including the lock screen. Help numbers only in
  `res/values/helplines.xml`, with the verification date.
- No AI chat, no questionnaires with result interpretation.
- No promises of treatment or diagnosis anywhere (app, store listing, website): that would make
  it a medical device (MDCG 2019-11).
- Every user-facing text is reviewed by a certified CBT therapist. Simple Polish, "Ty" form,
  gender-neutral where possible (see the comment at the top of `strings.xml`).
- Accessibility: TalkBack, 200% font, touch targets of at least 48 dp.

## Build and test

- Local SDK: `~/Android/Sdk` (`local.properties`), JDK 21, Gradle 9.8, AGP 9.4.1 with built-in
  Kotlin 2.2.10, KSP 2.3.12.
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
- Instrumented tests: `ANDROID_SERIAL=<device> ./gradlew :app:connectedDebugAndroidTest`.
  Mat's AVD `Pixel_7` may already be running for another project: start a separate instance
  with `emulator -avd Pixel_7 -read-only -port 5580 -no-window` and set `ANDROID_SERIAL`,
  so tests never touch the other emulator.
- `FLAG_SECURE` is on by default, so screenshots are black until "Ukryj treść w ostatnich
  aplikacjach" is turned off in settings.
- Every Room schema change: bump the version, add a migration, commit the exported schema in
  `app/schemas/`, add a test to `androidTest/.../data/DatabaseTest.kt` (MigrationTest).

## Release (GitHub Actions, `.github/workflows/android.yml`)

- Push to `main` / PRs: lint, unit tests, release bundle, no-INTERNET check, UI tests on an
  emulator. Build errors are posted as a commit comment.
- Release: bump `versionCode` and `versionName` in `app/build.gradle.kts`, update What's new,
  commit, tag `vX.Y.Z` (must equal versionName), push the tag → signed bundle + R8 mapping go
  to Google Play **internal testing**. Mat promotes by hand.
- Secrets Mat sets once (`gh secret set NAME`): `ANDROID_KEYSTORE_BASE64`,
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD` (optional), `PLAY_SERVICE_ACCOUNT_JSON`.
  Key alias: `myslnik`. The first bundle for a new app must be uploaded in Play Console by hand,
  and a new personal account needs a 14-day closed test with 12+ testers.

## State (2026-10-11)

- 1.3.0 (versionCode 2) feature-complete in code, not released. 1.0 was skipped: 1.3 contains
  1.0 (thought record, mood, SOS, PDF, lock, encryption, backup), 1.1 (activities, sleep
  diary, reminders), 1.2 (exposure, experiments, worry time, problem solving) and 1.3
  (knowledge cards, Ukrainian).
- Texts: Polish in `values/strings*.xml`, Ukrainian in `values-uk/` (TranslationsTest keeps
  them in sync). All are drafts waiting for the CBT review; Ukrainian also for a native speaker.
- "Wesprzyj projekt": Play Billing tips (`support_small/medium/large`), consumed after purchase,
  billing telemetry (datatransport) excluded. Products still have to be created in Play Console.
- 53 unit tests and 25 instrumented tests pass locally.
