# BackHealth — Android App (Phase 1)

> **Spine Health Companion for Desk Workers & Lumbar Disc Recovery**  
> Focus: Lumbar spine (L4-L5, L5-S1) decompression, posture resets, and active movement breaks.

---

## 🌟 Features (Phase 1)

1. **Intelligent Repeating Alarm Cycle**:
   - **20-Minute Alarm**: Triggers a 2–3 minute micro-exercise routine (1 random exercise from Set 1 + 1 from Set 2). Once marked Done, starts the 40-minute walk countdown.
   - **40-Minute Alarm**: Triggers a 3–5 minute walking break. Tapping *"I'm Walking"* runs a 5-minute countdown. When finished, starts the 20-minute exercise countdown.
2. **Lumbar-Specific Exercise Sets**:
   - **Set 1: Lower Body & Core Pump (7 exercises)**:
     - Slow Standing March (30s)
     - Calf Raises (20 reps)
     - Glute Squeezes (10 reps × 3s hold)
     - Standing Wall Calf Stretch (15s / side)
     - Standing Quad Stretch (15s / side)
     - Standing Hip Flexor Stretch (30s / side)
     - Standing Ankle Circles (10 reps / side)
   - **Set 2: Upper Body & Posture Reset (7 exercises)**:
     - Standing Chin Tucks (10 reps)
     - Exaggerated Backward Shoulder Rolls (5 reps)
     - Standing Shoulder Blade Squeezes (10 reps × 3s hold)
     - Standing Upper Trap Stretch (15s / side)
     - Doorway Chest Opener (15s / side)
     - Vertical Overhead Reach (10s)
     - Wall Angels (10 reps)
3. **Full-Screen Lockscreen Alarms**:
   - Wakes screen and displays over lockscreen with audio and vibration alerts.
4. **Snooze Support**:
   - Snoozes re-alert every 2 minutes until the break is completed.
5. **Mid-Day Rest / Nap Mode**:
   - Options for 20m, 30m, 40m, 1h, and 1.5h. All alarms are muted during rest; the cycle automatically resumes as soon as rest expires.
6. **Sleep Window**:
   - Configurable wake-up time and bedtime (e.g. 07:00 AM – 10:00 PM). Alarms never sound while you sleep.
7. **Exercise Library & Customization**:
   - Browse all 14 exercises with clinical coaching cues and warnings.
   - Toggle off any exercise that feels painful or uncomfortable.
8. **Streak & History Tracking**:
   - Tracks daily routines, total exercises and walks completed, and daily streaks.

---

## 🏗️ Architecture & Tech Stack

- **Platform**: Android Native (Kotlin)
- **Min SDK**: API 26 (Android 8.0) | **Target & Compile SDK**: API 35 (Android 15)
- **UI**: XML Layouts + View Binding + Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel) + Repository Pattern
- **Async & State**: Kotlin Coroutines & `StateFlow`
- **Database**: Room Database (`AppDatabase`)
- **Alarm Engine**: `AlarmManager` (Exact Alarms) + `AlarmCycleService` (Foreground Service)
- **Resilience**: `BootReceiver` (restarts cycle upon device reboot)

---

## 🚀 How to Open and Run

1. Open **Android Studio** (Ladybug or later recommended).
2. Choose **File -> Open...** and select `d:\dev\back-health`.
3. Allow Gradle to sync.
4. Run on an Android device or emulator running Android 8.0 to Android 15.
