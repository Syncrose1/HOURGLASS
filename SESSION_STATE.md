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
  Bedtime       countdown, sleep length, wind-down window, past-bedtime test
  Timer         TimerKind / TimerRef / ActiveTimer / TimerRecord
  TimerSand     the eight palette tokens, with legacy-hex parsing
  Insights      session log -> daily totals, task ranking, streak, rates

data/
  entity/       TaskEntity, QuicksandTaskEntity, TimerSessionEntity, SettingsEntity
  dao/          one DAO per table
  database/     HourglassDatabase (v2, schemas exported to app/schemas)
  repository/   HourglassRepository — the only thing that touches DAOs

di/            AppModule: database, DAOs, the application-scoped CoroutineScope
timer/         TimerController — process singleton, owns the running timer
service/       TimerService + NotificationHelper — render controller state
viewmodel/     HourglassViewModel (home), SettingsViewModel, InsightsViewModel
ui/            theme tokens, drawn components, charts, screens, navigation, previews
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

### Colour
The `colour` column stores a `TimerSand` token, not a hex. A sand resolves to a
different step per theme, so a stored hex could only ever be right in one of them.
`TimerSand.parse` still accepts hexes written by older builds and snaps them to a slot.

Both step sets were derived by running candidates through the dataviz skill's palette
validator rather than by eye. The constraint that decided them: at a single lightness,
eight hues cannot clear the colour-vision separation floor — alternating lightness can,
because CVD collapses hue and leaves lightness intact. The light set alternates
L 0.50/0.76, the dark set L 0.49/0.66 inside its narrower band.

## Verification status
- `core/` is covered by 42 JVM unit tests in `app/src/test/java/com/hourglass/core/`
  (formatting, bedtime arithmetic, time parsing, the record state machine, sand
  tokens, insights aggregation). These run with `./gradlew testDebugUnitTest` and have
  been run green.
- The Android build (`:app:assembleDebug`) has **not** been run since the rewrite —
  the environment it was edited in had no reachable Android SDK. Compile it before
  trusting the UI layer.
- There are no instrumented tests yet.

## Known gaps / next steps
1. **No UI tests.** The Compose layer has previews but no `ui-test-junit4` coverage;
   start/pause/resume/stop on a card is the obvious first test.
2. **Room migration.** The schema is at v3 and still falls back to destructive
   migration, which is fine only because nothing has shipped. Write real `Migration`
   objects and drop `fallbackToDestructiveMigration()` before the first release.
3. **`specialUse` foreground service type.** Correct for a local timer, but Play
   requires a written justification at submission time; the manifest property carries
   the wording.
4. **Single locale.** All strings are externalised in `strings.xml` but only `en`
   exists. The weekday initials on the insights chart already come from the device
   locale via `DateFormatSymbols`.
5. **The insights charts have no touch-to-inspect.** Every value is already visible —
   each task row carries its own figure and the best day is labelled — and both charts
   expose per-mark descriptions to screen readers, so this is polish rather than a
   gap. Worth adding if the window ever grows past a week.
6. **Insights covers a fixed 7-day window.** `Insights.from` already takes
   `windowDays`, so a range control is a UI change, not a data one.
7. **Bedtime nudge is passive.** It appears on the home screen when a timer runs past
   bedtime, but only if the app is open. A scheduled reminder would need
   `AlarmManager` or WorkManager and a deliberate decision about how much the app is
   allowed to interrupt.
