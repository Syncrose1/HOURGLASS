# HOURGLASS ⏳

A lightweight, on-device productivity timer with a sand aesthetic. Bank your time,
respect your rest.

## Features

### 🏖️ Sand timers
Dedicated timers for the work you planned — "Flashcards, 2 hours", "Question bank,
1 hour". Each one is a card with a drawn sandglass that drains in real time.
Start, pause, resume and stop; only one timer runs at a time, and starting a new one
files the previous session before it begins.

### ⏳ Quicksand
Lightweight timers for whatever comes up. Same machinery, deliberately smaller and
quieter so they never compete with the day's real work.

### ⏱️ Overtime
Running past an allocation is a feature, not an error. The remaining time goes negative
and is shown as `+MM:SS`, the sand turns, and the overrun is recorded against the task.

### 🌙 Bedtime countdown
Set a bedtime and a wake time. The home screen leads with a dusk-gradient card counting
down in plain language, deepening in tone and lighting its ring through the last two
hours before bed. Settings projects the resulting night's sleep and flags a short one.

### 📊 Habit tracking
Every completed run is written to a session log with its planned duration, elapsed time
and overtime. The home screen surfaces the day's total; the log is kept on device for
richer feedback later.

### 🔔 Ongoing notification
A running timer holds a foreground-service notification with the task name, the time
left, and pause/resume and stop actions. Timers survive the app being backgrounded,
swapped out or the device rebooting — the run is journalled and restored on next launch.

## Design

- **One palette.** A single warm neutral ramp carries every surface, border and label;
  accents only ever appear as sand, glow or state. Material You dynamic colour is
  deliberately *not* used — the sand palette is the point.
- **Two voices.** Numbers are thin and wide open; labels are small, bold and
  letter-spaced.
- **Motion means something.** A running card is the only thing on screen that moves:
  it lifts, takes the timer's own colour and gets a slow shimmer around its edge.
- **Full dark theme.** Every colour is a semantic token resolved per theme, so nothing
  is hard-coded to a light background.

`ui/preview/ComponentPreviews.kt` renders each card in both themes and in every state
if you want to look before building.

## Architecture

| Layer | What lives there |
|---|---|
| `core/` | Pure Kotlin: duration formatting, bedtime arithmetic, the timer state machine. No Android imports, fully unit tested. |
| `data/` | Room entities, DAOs, and `HourglassRepository` — the single door onto storage. |
| `timer/` | `TimerController`, a process singleton that owns the one running timer, ticks it, and journals it. |
| `service/` | `TimerService`, a renderer for the controller's state as an ongoing notification. |
| `viewmodel/` | Screen state, assembled from the repository and the controller. |
| `ui/` | Theme tokens, drawn components, screens, navigation. |

Kotlin + Jetpack Compose, MVVM, Hilt for injection, Room for persistence.

## Building

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # run the core unit tests
./gradlew lintDebug            # Android lint
```

Requires JDK 17 and the Android SDK (compileSdk 34). Minimum device API is 26.

## Privacy

All data stays on the device. The app holds no `INTERNET` permission: there are no
accounts, no sync and no analytics.
