# Patro

A lightweight Nepali calendar for Nothing OS: Bikram Sambat (BS) + Gregorian (AD) dates,
Nepal holidays, Google Calendar events and a dot-matrix home-screen widget.
No ads, no tracking, no network except when you tap **Sync**.

Personal app, sideloaded. Kotlin + Jetpack Compose, minSdk 31, targetSdk 36.

| | |
|---|---|
| Release APK size | **2.2 MiB** (2,316,491 bytes; R8 + resource shrinking; [CI run #3](https://github.com/iampratik32/Nepali-Calendar/actions/runs/43282669525), 12 Nov 2026).) |
| Permissions | `INTERNET`, `READ_CALENDAR`, `WRITE_CALENDAR` |
| Dependencies | AndroidX Activity/Compose (foundation, material3, ui), kotlinx-coroutines. Nothing else. |

## Getting the APK

Every push builds a release APK on GitHub Actions (`.github/workflows/build.yml`):
**Actions → Build → latest run → Artifacts → `patro-release-apk`**. Unzip it and install on the
phone (allow "Install unknown apps" for your browser or file manager).

### Signing (do this once, or updates won't install)

Without signing secrets, CI signs with a throwaway debug key that changes every run, so each new
build would need an uninstall first (losing settings). Create your own key once:

```sh
keytool -genkeypair -v -keystore patro.jks -alias patro -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 patro.jks > patro.jks.b64     # macOS: base64 -i patro.jks -o patro.jks.b64
```

Then in the GitHub repo → Settings → Secrets and variables → Actions, add:

| Secret | Value |
|---|---|
| `PATRO_KEYSTORE_BASE64` | contents of `patro.jks.b64` |
| `PATRO_KEYSTORE_PASSWORD` | keystore password |
| `PATRO_KEY_ALIAS` | `patro` |
| `PATRO_KEY_PASSWORD` | key password |

Keep `patro.jks` somewhere safe (not in the repo — `*.jks` is git-ignored). Locally, Android Studio
builds use `keystore.properties` in the repo root (`storeFile=…`, `storePassword=…`, `keyAlias=…`,
`keyPassword=…`) if present, otherwise the debug key.

### Building locally

Android Studio (or `./gradlew assembleRelease` with the Android SDK installed). Unit tests:
`./gradlew testDebugUnitTest`.

## One-time phone setup

- Keep the **Google Calendar** app installed and enabled (it does the syncing and the reminders).
- Google Calendar → Settings → General → **Share Google Calendar data with other apps**: on.
- In Patro, tap **Allow calendar access** (Today screen or Settings). The app works without it —
  dates and holidays only.
- Add the widgets: long-press the home screen → Widgets → Patro.

## What's in the app

| Area | Where |
|---|---|
| BS ⇄ AD converter (BS 2000–2100), Devanagari formatting | `app/src/main/java/app/patro/bs/` |
| Holiday files, local store, Sync | `…/holidays/` |
| Google Calendar via Calendar Provider (read Instances, create/edit/delete Events + one reminder) | `…/events/` |
| Dot-matrix glyphs (5×7, ०–९ and 0–9, `/ : -`) and renderer | `…/dotmatrix/` |
| Today, Month, day sheet, event editor, Settings | `…/ui/` |
| 2×2 "Date" and 4×2 "Date + next" widgets | `…/widget/` |
| Data that Sync downloads | `data/` (see `data/README.md`) |

### Status against the plan

| # | Phase | State |
|---|---|---|
| 0 | Project setup, R8, CI | Done. CI builds and tests on every push. **Not yet installed on the phone.** |
| 1 | Converter + tests | Done; tests pass (see notes below on 2083-12-31 and the cross-check). |
| 2 | Holiday data + Sync | Code done and tested. **2083 holiday list is a placeholder** — see Open items. |
| 3 | Today / Month / Day sheet | Written; needs checking on the device (jank, look). |
| 4 | Dot-matrix renderer + hero animation | Written; Devanagari glyphs need a look on the real screen. |
| 5 | Calendar read + picker | Written; needs on-device check incl. recurring + all-day. |
| 6 | Event editor | Written; edits/deletes apply to the whole series for repeating events. |
| 7 | Widgets | Written; midnight rollover must be checked on the device. |
| 8 | Polish | Light mode, empty states, licences screen exist; a week of daily use still to do. |

Nothing above phase 2 has run on a phone yet — this was built in a cloud sandbox that can compile
via CI but can't run Android.

## Decisions that differ slightly from the plan

- **Calendar table is compiled in** (`bs/BsData.kt`, 1.2 KB string) instead of `assets/calendar.json`,
  so widgets and tests can convert dates without a `Context`. `filesDir/calendar.json` from Sync still
  overrides it. Regenerate with `python3 scripts/gen_bsdata.py` after editing `data/calendar.json`.
- **Data lives in `data/` of this repo** rather than a separate `patro-data` repo (this session couldn't
  create repos). Sync reads `patro.dataUrl` from `gradle.properties`; `raw.githubusercontent.com` only
  serves public repos, so either make this repo public or move `data/` to a public `patro-data` repo and
  change that one line.
- **"Today" follows the phone's time zone**, not a hard-coded Asia/Kathmandu, so the date stays right
  when travelling. On a phone set to Nepal time they're identical. The widget rolls over at local midnight.
- **AD-first month view** shows Gregorian months (big AD digit, small BS digit in the corner) rather
  than BS months with swapped digits.
- **Second-library cross-check** uses 60 fixtures generated once from
  `io.github.shivathapaa:nepali-date-picker-core-jvm:3.3.0` (`app/src/test/resources/crosscheck.txt`)
  instead of a test dependency (that library pulls in Compose Desktop and a newer Kotlin).

## Open items

- [ ] **Fill in `data/holidays/2083.json` from the MoHA gazette.** This sandbox couldn't reach the
  gazette or the pradhanlaw.com summary, and I didn't want to type holiday dates from memory. The file
  only has Bhai Tika (2083-07-25 = 2026-11-11, from the plan). Send the PDF (or the list) and I'll
  transcribe it; `DataFilesTest` checks every `bs`/`ad` pair against the converter.
- [ ] **2083-12-31 vs the dataset.** The plan's test "2083-12-31 ↔ 2027-04-13" comes from the MoHA
  notice, but the sonill dataset gives Chaitra 2083 only **30** days (its last day of 2083 is
  2083-12-30 = 2027-04-13). Both agree the year ends on 2027-04-13 and 2084 starts on 2027-04-14.
  If the gazette really prints a Chaitra 31, one of 2083's months in the dataset is a day off — worth
  checking against a printed patro before Falgun/Chaitra.
- [ ] Projected years differ between datasets: 4 of 60 cross-check dates (all BS 2088–2090) disagree by
  a day, so the cross-check only covers BS ≤ 2087.
- [ ] Screenshots of Nothing Weather + a Nothing widget for the design pass (corner radius, padding).
- [ ] Confirm the `major: true` festival list.
- [ ] Install on the CMF Phone 2 Pro, then check: widget rollover after midnight, calendar sync to
  calendar.google.com, scrolling smoothness.
- Stretch, not started: Quick Settings tile, lock-screen widget.

## Licences

BS month data: [sonill/nepali-dates](https://github.com/sonill/nepali-dates), MIT — full text ships in
the app (`assets/licences.txt`, Settings → About → Licences). Dot-matrix glyphs are drawn for this app;
Nothing's Ndot/NType fonts are not used.
