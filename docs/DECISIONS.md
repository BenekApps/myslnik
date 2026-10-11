# Decisions

Newest first. Each entry: what was decided and why. The product plan is the
"Darmowa aplikacja CBT po polsku — plan" document (Claude Docs).

## 2026-10-11 — Donation link (Buy Me a Coffee)

The link https://buymeacoffee.com/benek.apps is on GitHub (README and `.github/FUNDING.yml`,
which shows a "Sponsor" button), **not** in the app and **not** in the Play listing.
Google Play's Payments policy (section 4) forbids leading users to payment methods other than
Play billing from the app or its listing; the only donation exception is tax-exempt donations
(charities), which a tip to the developer is not. A rejection would be costly for a health
app on its first review. The app already links to the GitHub repository ("Kod źródłowy").
A direct in-app link is fine for builds outside Play (F-Droid, GitHub APK) if they come later.
An in-app "Wesprzyj projekt" would have to use Play billing (see TODO).

## 2026-10-11 — Version 1.3 (1.1, 1.2 and 1.3 at once)

Mat decided to go straight to 1.3 instead of releasing 1.0 first. 1.3 contains everything
planned for 1.1 and 1.2.

**Database version 2** with a Room AutoMigration 1→2 (new tables only) and a migration test
that keeps 1.0 entries. Exercise modules use their DAOs directly (`AppContainer.db`); the
repository keeps the cross-cutting work (backup, delete all).

**Behavioural activation.** Activities per day with an optional time, rated after doing them:
pleasure and sense of achievement ("poczucie sprawstwa"), 0–10. Idea chips for a quick start.

**Sleep diary.** Consensus-sleep-diary fields. Times are stored as minutes from noon of the
night's date, so a night never wraps and DST does not matter (shift work sleeping across noon
is not supported). Efficiency = total sleep / time in bed. Unit-tested in `SleepMathTest`.

**Exposure.** Ladders → steps (expected anxiety 0–100, sorted ascending) → attempts (anxiety
before/after). Deleting a ladder deletes its steps and attempts explicitly in one transaction
(also declared as foreign keys). **No crisis card for exposure ratings**: high anxiety is
expected during exposure, and a "are you in crisis?" card there would work against the
exercise. SOS stays in the top bar. To be confirmed by the CBT reviewer.

**Behavioural experiments.** One form: belief (0–100 %), plan, prediction, obstacles, then
outcome, what was learned and the belief again.

**Worry time.** Worries are noted during the day and reviewed at a fixed time (default 18:00)
with a 15-minute timer shown in whole minutes (a ticking seconds counter can add tension);
each is let go or sent to problem solving (the problem wizard opens
with the worry text).

**Problem solving.** Six-step wizard with the same draft rules as the thought record.

**Reminders.** WorkManager one-time jobs that schedule the next day when they run (no exact
alarm permission, no drift, survive restarts; re-synced at app start). POST_NOTIFICATIONS is
asked only when a reminder is switched on. Notifications are VISIBILITY_PRIVATE with a public
version that says only "Myślnik – Przypomnienie". WorkManager's ACCESS_NETWORK_STATE stays
removed (no reminder uses network constraints), and so do FOREGROUND_SERVICE and its
SystemForegroundService (no long-running work; avoids a Play foreground-service declaration).

**Psychoeducation cards.** 12 cards in `strings_cards.xml`, each linked to an exercise. General
knowledge only, no diagnoses, ends with a "not a substitute for a specialist" note.

**Ukrainian.** Full translation in `values-uk` (needs native and CBT review). In-app language
switch with AppCompat (`MainActivity` is now an `AppCompatActivity`, theme
`Theme.AppCompat.DayNight.NoActionBar`), stored by AppCompat and copied to DataStore for
background work. `generateLocaleConfig` lists the languages in Android's per-app settings.
App bundle language splits are disabled so the switch works for apps installed from Play.
Dates and numbers follow the language of the texts on screen (`content_locale` resource), not
the system locale: on an English phone the texts fall back to Polish and so do the dates.
`TranslationsTest` checks that every Polish text exists in Ukrainian with the same format
arguments and array sizes; lint's MissingTranslation is on.

**Help numbers in Ukrainian.** The same verified Polish numbers, with Ukrainian descriptions
and a note that the conversation may be in Polish. Ukrainian-language lines found online
(800 108 108 Nagle Sami, HelpUkraine.GrupaWsparcia.pl, 116 111 Ukrainian hours, 800 12 12 12)
come from 2022–2023 sources and are **not** listed until verified with the organisations.

**Navigation.** Five tabs: Start, Ćwiczenia, Historia, Wiedza, Ustawienia. Mood moved from a
tab into Ćwiczenia (still one tap from Start via the mood card). History also lists sleep,
experiments and problems. PDF export can include every module.

**Version.** versionCode 2, versionName 1.3.0 (1.0 was never released).

## 2026-10-11 — Version 1.0 foundations

**Name and package.** "Myślnik", `com.benekapps.myslnik` (Mat's choice from the plan's two
proposals). The package cannot change after the first upload. Trademark and Play Store name
checks are still open (TODO.md).

**Open source, GPL-3.0, public repo `BenekApps/myslnik`.** Visible proof of the privacy
claims and an F-Droid option later.

**Android versions.** minSdk 26, targetSdk 36 (Google Play requirement for new versions from
31.08.2026), compileSdk 37 (the platform installed locally). Lint's OldTargetApi is disabled
on purpose.

**No network at all.** No `INTERNET` permission; the manifest removes it in case a library
adds it, and CI fails if it appears in the merged release manifest. Consequence: no
"Wesprzyj projekt" (Play Billing needs the network) in 1.0, as the plan proposed.

**Encrypted database.** Room + SQLCipher Community (`net.zetetic:sqlcipher-android`). A random
256-bit key is passed in SQLCipher's raw-key format (`x'…'`), so no slow key derivation runs
at every start. The key is wrapped with a non-exportable AES-GCM key in the Android Keystore
and stored in `noBackupFilesDir`. If the key file is missing, old database files are deleted
before a new key is made (they could never be opened again).

**No Android backup or device transfer.** `allowBackup=false` plus data extraction rules and
legacy full-backup rules that exclude everything: the Keystore key does not move to a new
phone, so a copied database would be unreadable and the app would break. Users back up with
the password-protected file instead.

**Backup file.** `MYSLNIK1` | iterations | salt | IV | AES-256-GCM ciphertext. Key from the
user's password with PBKDF2-HMAC-SHA256, 600 000 iterations (OWASP 2023). The header is
authenticated (AAD). Inside: versioned JSON (`format`, `version`); older versions must stay
readable, newer ones are refused with a clear message. Restoring replaces all entries after
a confirmation that shows the counts. Minimum password length 8. Forgetting the password
means losing the backup; the app says so and asks for a checkbox.

**App lock.** Optional, offered in onboarding. PIN of 4–8 digits stored as a salted
PBKDF2-HMAC-SHA256 hash (120 000 iterations). Fingerprint via `androidx.biometric`,
`BIOMETRIC_STRONG` only, on top of the PIN. The app locks after 60 s in the background
(process lifecycle), so opening the dialer or share sheet briefly does not lock it; a new
process always starts locked. After 5 wrong PINs: 30 s wait, doubling, at most 15 min,
persisted across restarts. A forgotten PIN cannot be recovered: the only way in is deleting
all entries (with two confirmations); a backup can then be restored.

**SOS without unlocking.** The lock screen, onboarding and the database error screen all have
the SOS button. While locked, the app stays composed underneath (so typed text is not lost)
but is not drawn and its semantics are cleared, so TalkBack cannot read it.

**Hide from recents / block screenshots on by default.** `FLAG_SECURE`, switchable in
settings. Set in `onCreate` before the setting is read.

**Crisis card.** Shown once per screen (thought record or mood entry) when an emotion is rated
90–100 or mood 0–1. It never blocks anything and can be closed. Only the numeric ratings are
used; entry text is never analysed (privacy before crisis detection, as in the plan).

**Help numbers.** One file, `res/values/helplines.xml`, with `helplines_verified_on`. Checked
on 2026-10-11 against gov.pl and 116111.pl: 116 123, 800 70 2222, 112, 800 190 590 and
116 111 are all 24/7. The first three appear in short help cards. 116 111 is for under-18s;
it stays on the SOS screen in case a younger person uses the app.

**PDF.** `android.graphics.pdf.PdfDocument`, A4, no library. Drafts are never exported. The
PDF is not encrypted (the screen says so). Files live in `cache/exports`, are shared through
`FileProvider` and are deleted at the next app start.

**Thought record drafts.** Drafts are rows in the same table (`is_draft`, `draft_step`). They
are saved after every step, when the app goes to the background and when the screen is left
(in an app-wide scope so the save survives the ViewModel). Empty records are never saved.
Editing a finished record saves changes the same way.

**Texts.** All user-facing text in `res/values/strings.xml` (Polish as the default locale,
`tools:locale="pl"`), so the reviewer reads one file. Gender-neutral Polish where possible
(no past-tense verbs about the user, examples quote thoughts). `localeFilters` keeps only
Polish library resources until Ukrainian arrives in 1.3. Emotions and distortions are stored
as keys, so labels can be reworded or translated without touching data.

**Smaller choices.** Manual dependency injection (`AppContainer`) instead of Hilt. `org.json`
(part of Android) instead of kotlinx.serialization for list columns and backups. Own Canvas
chart instead of Vico. Navigation Compose with string routes. `material-icons-core` only.
`kotlinx-serialization-core` is constrained to 1.8.1 because `room-testing` needs it and test
dependencies are aligned to the app's.
