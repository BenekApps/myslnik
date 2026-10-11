# Decisions

Newest first. Each entry: what was decided and why. The product plan is the
"Darmowa aplikacja CBT po polsku — plan" document (Claude Docs).

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
