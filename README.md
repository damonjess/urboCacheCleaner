# TurboCacheCleaner

**TurboCacheCleaner** is an Android utility application designed to help users scan, track, and automate cache clearing across installed applications, as well as diagnose system performance bottleneck signals without root or deceptive claims.

---

## Key Features

### 1. Automated "Turbo Clean" (Accessibility Service)
- **Automated Cache Clearing:** Uses Android's `AccessibilityService` (`CacheClearAccessibilityService`) to navigate to each app's System Settings page and automatically trigger the "Clear cache" action across queued applications.
- **Smart Watchdog & Timeout Recovery:** Prevents frozen screens or missing buttons from blocking the cleaning queue by timing out and advancing to the next app automatically.
- **Transition Protection:** Debounces and locks UI input during intermediate window transitions to avoid flickering or stray button taps.
- **Multi-OEM Support:** Recognizes common storage and cache label variants across stock Android, MagicOS / EMUI, Samsung One UI, and other custom Android skins.

### 2. App Cache Scanner & Real-Time Status Tracking
- **Cache Size Detection:** Uses Android's native `StorageStatsManager` to inspect cache usage per application.
- **Visual Progress Tracker:** Displays scanned apps in a real-time list with status indicators (`Pending`, `Cleaning...`, `Cleaned`) as the automated clean progresses.

### 3. Honest Device Health Diagnostics ("Why is my phone slow?")
- **No-Root Diagnostics:** Evaluates genuine system signals provided by Android APIs (`DeviceHealthChecker`):
  - **Free RAM & Low Memory Warnings**
  - **Free Storage Capacity**
  - **Battery Temperature & Thermal Throttling Status** (Android 10+)
  - **Battery Saver Mode Detection**
  - **Battery Optimization Exemption Check**
- **Transparent Reporting:** Explains verifiable causes of system slowdown in plain language without making fake claims or using deceptive "RAM booster" gimmicks.

### 4. Honor / MagicOS Optimization Assistant
- **Guided Setup Walkthrough:** Provides step-by-step guidance (`HonorRepairAssistant`) for aggressive background battery optimization settings on Honor and Huawei devices.
- **Direct Settings Jump Links:** Directs users to battery optimization exemptions, app launch management, storage settings, and software updates.

---

## Technical Details

- **Target SDK:** Modern Android (API 33+)
- **Language:** Kotlin
- **Key APIs & Frameworks:**
  - `AccessibilityService` for automated UI interaction
  - `StorageStatsManager` / `StorageManager` for cache size calculations
  - `ActivityManager`, `PowerManager`, and `BatteryManager` for hardware diagnostic signals
  - `Coroutines` for async background processing

---

## How to Use

1. **Permissions:**
   - Grant **Usage Access** when prompted to allow cache size scanning via `StorageStatsManager`.
   - Enable **TurboCacheCleaner Accessibility Service** in Android Settings to allow automated cache clearing.
2. **Scan:** Tap **Scan Apps** to view a breakdown of apps and their current cache size.
3. **Clean:** Tap **TURBO CLEAN** to start automated cache clearing across all queued apps.
4. **Diagnostics:** Tap **Why is my phone slow?** or **Honor Optimization Checklist** for diagnostic reports and system setting guidance.
