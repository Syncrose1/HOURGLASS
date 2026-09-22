# HOURGLASS ⏳

A lightweight, on-device productivity timer with a sand aesthetic. Bank your time,
respect your rest.

## Features

### 🧱 One wall, no scrolling
Every timer is a tile on a single screen. Add more and the tiles get smaller — they
never spill into a list you have to scroll. That is the point rather than a limit: a
wall that visibly gets denser is its own argument for having fewer timers, which a
scrolling list never makes.

### 👆 One gesture
Tap a tile and that timer takes the whole screen and starts running. Tap again and it
pauses and hands you back the wall. There are no buttons on a tile and nothing to
adjust while a timer runs — if you want to change something, stop the timer first.
Long-press a tile to edit it, which you can only do from the wall, which means only
when nothing is running.

### 🏖️ Real falling sand
The glass is a cellular automaton, not an animation. Grains fall, pile, slump and find
their angle of repose from one rule applied to every cell. Tilt the phone and the sand
falls the way you tilt it; drag a finger through a pile and it collapses. The drain is
metered by the timer, so what you are watching is the elapsed time itself.

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

### 🏜️ The desert
Forest grows a wood; this pours a dune. Every finished session becomes a visible band
of sand in the colour of the timer that earned it, oldest at the base, so the pile is
a cross-section of how your time was actually spent rather than a score. It grows on a
log scale — the first hour reads as real progress and the five-hundredth still adds
something — and landmarks appear on the slope at thresholds you cross. The only way to
add to it is to run a timer through to the end.

### 🔔 Time's up
A timer that reaches its allocation says so — a one-shot alert on its own notification
channel, plus a haptic if the app is open. It does not stop anything: the timer keeps
running into overtime, because you chose the allocation, you did not agree to be cut
off at it.

### 🌙 Your day ends in 2 hours 15 minutes
A standing notification says how much of your day is left, in plain words, floored to
the quarter hour. The rounding is the feature: "2 hours 13 minutes" sliding to "2 hours
7 minutes" is not a feeling, but "2 hours 15 minutes" holding still and then dropping
to "2 hours" is a loss you notice. Flooring rather than rounding to nearest means the
figure is a promise the clock can keep — you always have at least what it says.

### 🔔 Ongoing notification
A running timer holds a foreground-service notification with the task name, the time
left, and pause/resume and stop actions. Timers survive the app being backgrounded,
swapped out or the device rebooting — the run is journalled and restored on next launch.

## Design

- **One palette.** A single warm neutral ramp carries every surface, border and label;
  accents only ever appear as sand, glow or state. Material You dynamic colour is
  deliberately *not* used — the sand palette is the point.
- **The eight sands are computed, not eyeballed.** Each one is a token that resolves to
  a different step in light and dark — steps chosen for that surface, never flipped
  from the other. Both sets were run through a colour-vision validator: adjacent
  swatches clear ΔE 15 for normal vision and ΔE 15 under protanopia, deuteranopia and
  tritanopia. Colour never carries identity alone regardless — every card and every
  chart row is labelled.
- **Two voices.** Numbers are thin and wide open; labels are small, bold and
  letter-spaced.
- **Motion means something.** A running card is the only thing on screen that moves:
  it lifts, takes the timer's own colour and gets a slow shimmer around its edge.
- **Full dark theme.** Every colour is a semantic token resolved per theme, so nothing
  is hard-coded to a light background.

`ui/preview/ComponentPreviews.kt` renders the wall at three densities, the dune day and
night, and every sand in both themes, if you want to look before building.

## Architecture

| Layer | What lives there |
|---|---|
| `core/` | Pure Kotlin: the sand automaton, the sandglass, duration formatting, bedtime arithmetic, the timer state machine, tiling maths, the desert. No Android imports, 92 unit tests. |
| `data/` | Room entities, DAOs, and `HourglassRepository` — the single door onto storage. |
| `timer/` | `TimerController`, a process singleton that owns the one running timer, ticks it, and journals it. |
| `service/` | `TimerService`, a renderer for the controller's state as an ongoing notification. |
| `viewmodel/` | Screen state, assembled from the repository and the controller. |
| `ui/` | Theme tokens, the sand renderer, tiles, screens, navigation. |

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
