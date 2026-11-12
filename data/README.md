# Patro data

Files the app's **Settings → Sync** downloads. The app reads them from the URL in
`gradle.properties` → `patro.dataUrl` (default: this folder on the `main` branch via
`raw.githubusercontent.com`, which only works if the repo is public).

| File | What |
|---|---|
| `index.json` | Versions of everything below. Sync only downloads files whose version here is higher than the phone's copy. |
| `calendar.json` | BS month lengths, `{"2000":[30,32,...], ...}`. From [sonill/nepali-dates](https://github.com/sonill/nepali-dates) (MIT). Must start at 2000 (the anchor year). |
| `holidays/<bsYear>.json` | Holidays for one BS year. Schema below. |

## Updating holidays (yearly, or when an ad-hoc notice comes out)

1. Edit or add `holidays/<year>.json` from the MoHA notice in the Nepal Gazette.
2. Bump `"version"` in the file **and** the matching number in `index.json`.
3. Run the unit tests (`./gradlew testDebugUnitTest`) — `DataFilesTest` checks every file here
   with the same validation the app runs (each `bs` date must convert to the given `ad` date).
4. Commit to `main`, then tap **Sync** on the phone.

If you also want the new data to ship in the APK (works offline on a fresh install), copy the file
to `app/src/main/assets/holidays/`.

## Holiday entry

```json
{ "bs": "2083-07-25", "ad": "2026-11-11", "np": "भाइटीका", "en": "Bhai Tika",
  "kind": "public", "scope": "national", "major": true }
```

- `kind`: `public` (day off) | `festival` (big festival, not a day off)
- `scope`: `national` | `women` | `valley` | `community`
- `major`: show it even when `scope` isn't `national` (e.g. Teej)

The app shows entries where `scope == "national"` or `major == true`.

## Calendar data

If `calendar.json` changes, bump `calendar.version` in `index.json` and regenerate the copy built
into the app with `python3 scripts/gen_bsdata.py`.
