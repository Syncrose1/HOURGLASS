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
  SandGrid      the falling-sand automaton: one rule, walls, arbitrary gravity
  HourglassSim  a vessel built from SandGrid, with a timer-metered drain
  TileLayout    how many columns fit, and how much detail a tile can carry
  Desert        sessions -> a dune: height, strata, landmarks

data/
  entity/       TaskEntity, QuicksandTaskEntity, TimerSessionEntity, SettingsEntity
  dao/          one DAO per table
  database/     HourglassDatabase (v2, schemas exported to app/schemas)
  repository/   HourglassRepository — the only thing that touches DAOs

di/            AppModule: database, DAOs, the application-scoped CoroutineScope
timer/         TimerController — process singleton, owns the running timer
service/       TimerService + NotificationHelper — render controller state
work/          DayRemainingWorker — quarter-hourly day countdown
viewmodel/     HourglassViewModel (wall + focus), SettingsViewModel, DesertViewModel
ui/            theme tokens, sand renderer, tiles, screens, navigation, previews
```

### Where the timer lives
`TimerController` is a `@Singleton`. It holds `StateFlow<ActiveTimer?>`, ticks at 4 Hz
on an application-scoped coroutine, and writes a `TimerRecord` to the settings table on
every state transition (not on every tick). The ViewModel and the service are both
readers. This is deliberate: a timer is not a property of a screen.

Elapsed time is banked as `accumulatedMillis` plus a wall-clock anchor for the current
run, so `elapsedAt(now)` reconstructs the truth after the process has been away — which
is what makes restore-after-reboot work without a boot receiver.

### The wall and focus
The home screen is a fixed grid with no scrolling: `TileLayout.shapeFor` picks the
column count whose tiles come closest to the target aspect, and `TileDetail` thins the
tile's contents as it shrinks. Focus is *not* a navigation destination — it is a field
on `HomeState`. That is what lets tapping out of it pause the timer instead of leaving
one running behind a back stack entry.

### The sand
`SandGrid` is a classic falling-sand automaton: a grain moves along gravity, else to
one of the two diagonals either side, chosen at random. The angle of repose, slumping
and draining all emerge from that one rule. Gravity is any of eight directions, so the
accelerometer can steer it.

`HourglassSim` wraps a grid in a vessel. Its waist is **fully walled**: an open neck
would let gravity carry grains through at its own pace, and the glass would then be an
ornament rather than a readout. Everything crosses via `releaseOne`, metered by
`syncTo(progress)` against the timer.

Rendering goes through a bitmap the size of the grid, scaled up with filtering off —
one draw call per frame, and crisp square grains. Thousands of rects would not hold a
frame rate.

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
- `core/` is covered by 92 JVM unit tests in `app/src/test/java/com/hourglass/core/`
  (formatting, bedtime arithmetic and quarter-hour flooring, time parsing, the record
  state machine, sand tokens, insights aggregation, the sand automaton including grain
  conservation and the angle of repose, the metered hourglass drain, tiling, the
  desert). These run with `./gradlew testDebugUnitTest` and have
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
5. **The wall has no tested upper bound.** `TileLayout` keeps fitting tiles forever,
   and past roughly twenty they are glyph-sized. That is intended pressure, not a
   crash, but nobody has looked at fifty.
6. **The sand sim runs only in focus.** Tiles use the cheap drawn glass; running a
   simulation per tile would not hold a frame rate. The dune is drawn, not simulated —
   pouring new sessions onto it with the automaton is the obvious next flourish.
7. **Tilt is always on in focus mode.** It has a dead zone and heavy smoothing, but
   there is no setting to turn it off, and it will be wrong for anyone using the app
   lying down.
8. **The day countdown depends on WorkManager's 15-minute floor.** That happens to be
   exactly the display granularity, so they line up — but the notification can lag a
   step by up to a quarter hour after a reboot or a doze window.
