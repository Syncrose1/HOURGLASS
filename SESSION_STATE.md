# HOURGLASS — state of the codebase

## What the app is
An on-device productivity timer built with Kotlin and Jetpack Compose, persisting to
Room. Three screens — home, new timer, settings — over one running timer at a time,
plus a bedtime countdown and a session log.

## Architecture

```
core/        pure Kotlin, no Android imports, unit tested
  TimeFormat    duration -> clock/compact strings, signed for overtime
  TimeOfDay     HH:mm value type with parsing and display formatting
  Bedtime       countdown, sleep length, wind-down window
  Timer         TimerKind / TimerRef / ActiveTimer / TimerRecord

data/
  entity/       TaskEntity, QuicksandTaskEntity, TimerSessionEntity, SettingsEntity
  dao/          one DAO per table
  database/     HourglassDatabase (v2, schemas exported to app/schemas)
  repository/   HourglassRepository — the only thing that touches DAOs

di/            AppModule: database, DAOs, the application-scoped CoroutineScope
timer/         TimerController — process singleton, owns the running timer
service/       TimerService + NotificationHelper — render controller state
viewmodel/     HourglassViewModel (home), SettingsViewModel (sleep)
ui/            theme tokens, drawn components, screens, navigation, previews
```

### Where the timer lives
`TimerController` is a `@Singleton`. It holds `StateFlow<ActiveTimer?>`, ticks at 4 Hz
on an application-scoped coroutine, and writes a `TimerRecord` to the settings table on
every state transition (not on every tick). The ViewModel and the service are both
readers. This is deliberate: a timer is not a property of a screen.

Elapsed time is banked as `accumulatedMillis` plus a wall-clock anchor for the current
run, so `elapsedAt(now)` reconstructs the truth after the process has been away — which
is what makes restore-after-reboot work without a boot receiver.

### Identity
Sand timers and quicksand are separate tables whose ids both start at 1, so an id alone
is ambiguous. Everything above the DAOs works in `TimerRef(id, kind)`.

## Verification status
- `core/` is covered by JVM unit tests in `app/src/test/java/com/hourglass/core/`
  (formatting, bedtime arithmetic, time parsing, the record state machine). These run
  with `./gradlew testDebugUnitTest` and have been run green.
- The Android build (`:app:assembleDebug`) has **not** been run since the rewrite —
  the environment it was edited in had no reachable Android SDK. Compile it before
  trusting the UI layer.
- There are no instrumented tests yet.

## Known gaps / next steps
1. **No UI tests.** The Compose layer has previews but no `ui-test-junit4` coverage;
   start/pause/resume/stop on a card is the obvious first test.
2. **Room migration.** The schema moved from v1 to v2 (quicksand gained
   `sessionsCompleted`, sessions gained indices). Since nothing has shipped, the
   database falls back to destructive migration. Write a real `Migration` before the
   first release and drop `fallbackToDestructiveMigration()`.
3. **Session history is written but barely read.** Only the day's total is surfaced.
   The data is there for streaks, per-task trends and overrun feedback.
4. **`specialUse` foreground service type.** Correct for a local timer, but Play
   requires a written justification at submission time; the manifest property carries
   the wording.
5. **No timer editing.** A timer can be created and archived, not renamed or
   re-allocated.
6. **Single locale.** All strings are externalised in `strings.xml` but only `en` exists.
