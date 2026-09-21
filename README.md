# HOURGLASS ⏳

A lightweight productivity timer app with a beautiful sand aesthetic. Bank your time, respect your rest.

## Features

### 🏖️ Sand Timers
- Create dedicated task timers (e.g., "Flashcards - 2 hours", "Question Bank - 1 hour")
- Each timer appears as a vivid sandglass block on the home screen
- **Start**, **Pause**, **Resume**, and **Stop** controls
- Only one timer can run at a time (starting a new one pauses the previous)
- Overtime is clearly indicated when you exceed your allocated time
- Visual sand animation with running glow effects

### ⏳ Quicksand Timers
- Lightweight one-off task timers for miscellaneous activities
- Visually distinct from main sand timers (muted styling, smaller visual)
- Same start/pause/stop controls

### 🌙 Bedtime Countdown
- Set your bedtime in Settings
- Live countdown displayed on home screen in natural language ("2 hours 30 min until bedtime")
- Gentle reminders as bedtime approaches (golden glow when within 2 hours)
- Projected sleep duration calculated from bedtime/wake time settings

### 📊 Habit Tracking
- Automatic tracking of time spent per task across sessions
- Records completed sessions and timestamps
- Data stored locally for future habit feedback features

## Design Philosophy

- **Minimal**: Clean sand-toned palette, uncluttered layout
- **Intuitive**: Immediate visual feedback, natural gestures
- **Fun**: Animated sand glasses, vivid colors, satisfying interactions
- **Motivating**: Glowing blocks that draw you to start your timers

## Technical

- **Built with**: Kotlin + Jetpack Compose
- **Architecture**: MVVM with Hilt dependency injection
- **Persistence**: Room database for tasks, settings, and tracking data
- **Notifications**: Foreground service for persistent timer notification
- **Navigation**: Compose Navigation

## Getting Started

1. Open the project in Android Studio (Hedgehog or later recommended)
2. Sync Gradle
3. Build and run on a device/emulator (API 26+)

## Screens Overview

| Screen | Description |
|--------|-------------|
| **Home** | Bedtime countdown, active sand timers, quicksand timers |
| **Add Task** | Create new timer with name, duration, and colour |
| **Settings** | Configure bedtime, wake time, sleep projection |

## Data Privacy

All data stays on-device. No accounts, no cloud sync, no tracking.
