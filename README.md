# Myślnik

Darmowa aplikacja na Androida z ćwiczeniami terapii poznawczo-behawioralnej (CBT) po polsku.
Narzędzie samopomocy i do pracy między sesjami, nie terapia ani diagnoza.
Bez konta, bez reklam, bez internetu: wpisy są tylko na telefonie, zaszyfrowane.

A free Android app with CBT exercises in Polish. Self-help and homework between therapy
sessions; not therapy, not a diagnosis, not a medical device. No account, no ads, no network
access: entries stay on the phone, encrypted.

## Version 1.0

- Thought record (Beck, 7 steps, one per screen, hints and examples, draft saved after every step)
- Mood check-in (0–10, emotions, note) with a 7 and 30 day chart
- SOS on every screen, also on the lock screen: help numbers with a call button, 5-4-3-2-1 grounding
- PDF export for the therapist (period and entry types)
- PIN or fingerprint lock, SQLCipher-encrypted database with its key in the Android Keystore
- Password-protected backup file saved wherever the user chooses
- Short explanation of the cognitive model, sources, privacy

Roadmap: 1.1 behavioural activation, sleep diary, reminders · 1.2 exposure ladder, behavioural
experiments, worry time, problem solving · 1.3 psychoeducation cards, Ukrainian.

## Build

Requirements: JDK 21, Android SDK with platform 37.

```bash
./gradlew :app:assembleDebug              # debug APK
./gradlew :app:testDebugUnitTest          # unit tests (JVM)
./gradlew :app:connectedDebugAndroidTest  # UI, database and PDF tests on a device/emulator
./gradlew :app:lintDebug
```

CI (`.github/workflows/android.yml`) runs all of the above on every push, checks that the app
has no `INTERNET` permission, and on a `vX.Y.Z` tag uploads a signed bundle to Google Play
internal testing.

## Project layout

```
app/src/main/java/com/benekapps/myslnik/
  data/        Room + SQLCipher database, repository, settings (DataStore), backup, security
  domain/      emotions, cognitive distortions, help numbers, crisis thresholds, date ranges
  export/      PDF for the therapist (android.graphics.pdf.PdfDocument)
  lock/        app lock timing
  ui/          Jetpack Compose screens
app/src/main/res/values/strings.xml    all user-facing text (Polish), reviewed by a CBT therapist
app/src/main/res/values/helplines.xml  help numbers with their verification date
docs/DECISIONS.md                      why things are the way they are
TODO.md                                what is next
```

## Contributing

Code, comments and commits in English. User-facing text in Polish, in resources only,
following the language rules at the top of `strings.xml`. Every text change needs review
by a certified CBT therapist before release. Every database schema change needs a migration
and a migration test.

## License

[GPL-3.0](LICENSE)
