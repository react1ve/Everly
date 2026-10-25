# Recurring (working title)

A rebuild of the core features of a recurring-reminders app, for Android and iOS, in Kotlin Multiplatform + Compose Multiplatform.
Planning docs live in `replica/` (recon, architecture, schema, design, build log).

## Modules

| module | what |
| --- | --- |
| `:core` | pure Kotlin: models + `ScheduleEngine` (rolling schedule, cell states, reminders). Tests in `commonTest`. |
| `:shared` | Compose UI, design system (`app.design`), data layer (`app.data`), DI, navigation. Framework `Shared` for iOS. |
| `:androidApp` | Android host (Activity + Application). |
| `iosApp/` | iOS host, generated with XcodeGen from `project.yml`. |

## Run

```bash
./gradlew :core:jvmTest                      # schedule engine tests (fast)
./gradlew :shared:iosSimulatorArm64Test      # repository tests
./gradlew :androidApp:installDebug           # Android device / emulator

brew install xcodegen                        # once
cd iosApp && xcodegen && open iosApp.xcodeproj   # then Run in Xcode
```

The first launch seeds sample habits (`SEED_SAMPLE_DATA` in `app/di/Koin.kt`). Settings → "Design catalog" shows every primitive in every state.

## Placeholders that must change before release

- `applicationId` / bundle id `com.example.recurring`, app name "Recurring"
- the colour palette in `replica/design/gen_tokens.py` (it's the original's, by request)
- `SEED_SAMPLE_DATA = true`
