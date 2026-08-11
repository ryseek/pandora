---
name: android-phone-control
description: Control the Android device running Pandora through its paired ADB connection. Use when the user asks to operate the phone UI, including opening or navigating apps, tapping, swiping, typing, pressing Android buttons, inspecting the visible UI, or taking a device screenshot.
---

# Android Phone Control

Use Pandora's visible control session for every phone interaction. Keep actions within the user's request and leave the device in a clear, predictable state.

## Control workflow

1. Start a session before running any ADB device command:

   ```sh
   curl -fsS -X POST http://127.0.0.1:8765/v1/control/start
   ```

2. Poll `http://127.0.0.1:8765/v1/status` briefly. Continue only when it reports `"active":true`. If it does not become active, report that phone control is unavailable; do not bypass Pandora or repeatedly reconnect.

3. Run `adb devices` and require `127.0.0.1:5555` to be in the `device` state. Use that exact serial for every device command. Ignore any automatic `emulator-5554` entry; do not connect or select another transport yourself.

4. Inspect before acting. Prefer the UI hierarchy for coordinates:

   ```sh
   adb -s 127.0.0.1:5555 exec-out uiautomator dump /dev/tty
   ```

   Use `adb shell input tap`, `swipe`, `text`, or `keyevent` only as needed for the requested task. Capture screenshots with:

   ```sh
   adb -s 127.0.0.1:5555 exec-out screencap -p > /root/device-screen.png
   ```

5. Stop immediately when the task finishes or cannot continue:

   ```sh
   curl -fsS -X POST http://127.0.0.1:8765/v1/control/stop
   ```

Always run the stop request after an error as well as after success.

## Safety

- Treat disappearance of Pandora's colored frame, an ADB disconnect, or `"active":false` as the user stopping control. Do not reconnect unless the user asks again.
- Do not bypass the lock screen, Android permission prompts, account confirmation, or security warnings.
- Ask before actions that purchase, publish, send, delete, grant permissions, or expose private information unless the user's request already clearly authorizes that exact action.
- Never read, print, copy, modify, or transmit `/root/.android/adbkey` or other pairing credentials. ADB handles authentication automatically.
- Do not leave an interactive ADB shell or background device-control process running.
