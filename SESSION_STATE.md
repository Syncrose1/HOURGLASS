# HOURGLASS — Session State & Known Issues

## Intention and Design Goals
HOURGLASS is a lightweight, on-device productivity timer app with a sand/amber/gold aesthetic. It is built with Kotlin and Jetpack Compose and uses Room for local persistence. The primary goals are:

- **Bedtime countdown**: A persistent countdown card on the home screen with natural-language wording (e.g., "20 hours 36 minutes until bedtime") and a circular progress indicator.
- **Task timers**: Regular sand timers created by the user and displayed as glowing `SandTimerBlock` cards on the home screen.
- **Quicksand timers**: Quick/ad-hoc timers shown as muted `QuicksandBlock` cards for short tasks.
- **Single active timer**: Starting a new timer pauses/stops the previous one.
- **Persistent foreground notification**: Active timers should show a foreground-service notification with task name and remaining time.
- **Overtime / overrun banking**: When a timer runs past its target duration, elapsed time is recorded as overtime in session history and task stats.
- **Session history**: Completed sessions are recorded with planned/elapsed/overtime durations.

## Current State
- The app compiles successfully with `:app:assembleDebug`.
- Home screen renders the bedtime countdown, task list, and quicksand section.
- Adding a task via the FAB works and the timer card appears.
- Starting a regular timer works and the foreground notification channel is created and active.
- The notification channel `hourglass_timers` is visible in `dumpsys notification`.

## Known Issues / Unfinished Work
1. **Pause/Resume responsiveness under test**: Tapping Pause while a timer was running did not visibly pause the timer in manual ADB testing. The timer continued counting down and the button remained "Pause". This may be a touch-coordinate timing issue during testing, but it should be investigated by replaying pause/resume in the UI or adding logs in `HourglassViewModel.pauseTimer()` and `resumeTimer()`.
2. **Overtime and long sessions**: The model supports overtime fields and `TimerSessionEntity`, but long-running sessions were not tested because the default task duration is one hour.
3. **Boot restoration**: `BootReceiver` and `TimerService` are declared, but automatic restoration of a running timer after device reboot has not been verified.
4. **Settings persistence**: Settings screen was not exercised in the current session; bedtime and other preferences should be verified.
5. **Quicksand quick-add flow**: Quicksand creation and quick tasks were not manually tested in the current session.

## Aesthetic Notes
- Palette uses warm sand, amber glass, terracotta, teal, rose, indigo, olive, and coral accents with `GlassBackground` cards on a dark sand surface.
- Cards have rounded corners, subtle borders, and a glowing border animation while timers run.
- Typography uses thin display weights for timer durations and small uppercase labels for metadata.
- The hourglass logo and sand visuals reinforce the sand-timer metaphor.
