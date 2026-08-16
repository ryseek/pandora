# Pandora environment

You are running locally on the user's Android device inside Pandora.

## Runtime

- The userland is Debian Linux for ARM64 (`aarch64`).
- Debian runs through PRoot and shares the Android device's Linux kernel. This is a userspace container, not a virtual machine and not a separate security boundary.
- You normally run as `root`, with `HOME=/root`. General chats use an isolated `/root/chats/<chat-uuid>` working directory, while project chats start in their project directory.
- Standard Debian tools and packages are available. Use `apt-get update` followed by `apt-get install <package>` when an additional system package is needed and network access is available.
- Device resources, battery, storage, and network may be more constrained or intermittent than on a desktop or cloud host. Prefer focused commands and avoid unnecessary background processes.

## Storage and lifecycle

- `/root` is the persistent user workspace. Keep projects, Codex configuration, credentials, and durable output there.
- Pandora's **Repair Linux container** action can replace the Debian system image and remove packages or changes outside `/root`. It preserves `/root`.
- Files elsewhere in the Linux filesystem may be temporary across a repair. Do not place important user work there.
- Pandora workspace backups include the complete persistent `/root`, including account credentials and other sensitive files.

## Execution and safety

- Codex uses `sandbox_mode = "danger-full-access"` because Bubblewrap and Linux user namespaces are not available in this Android/PRoot environment.
- Treat that access carefully: commands can modify the user's entire Pandora workspace. Confirm before destructive or difficult-to-reverse actions unless the user explicitly requested them.
- PRoot emulates filesystem privileges. Some kernel, mount, namespace, systemd, Docker, hardware, and low-level networking operations will not work as they would on a native Linux host.
- There is no conventional desktop session inside the container. Prefer terminal workflows and files that the Android app can expose.

## Working with the user

- Be clear that commands run on the Android device, not on a remote server.
- Put new projects under `/root/projects` unless the user specifies another persistent location. Pandora reserves `/root/chats` for isolated general-chat workspaces.
- Preserve user files and existing configuration. Keep generated artifacts in the relevant project directory so they remain available and are included in backups.
- When delivering a file to the user, create it under `/root` and include an explicit Markdown link to its absolute path in the final response, such as `[report.pdf](/root/project/report.pdf)`. Pandora presents verified local file links as attachments that the user can preview, open, or save to Android storage.

## ADB phone control plugin

- When the user asks you to operate this Android phone and the ADB phone control plugin is enabled, start the visible safety session before issuing device commands:
  `curl -fsS -X POST http://127.0.0.1:8765/v1/control/start`
- Confirm that `http://127.0.0.1:8765/v1/status` reports `"active":true` before using `adb`.
- Use only the `127.0.0.1:5555` ADB transport supplied by Pandora. Ignore any automatic `emulator-5554` entry; do not reconnect or select another transport yourself.
- Use the system `adb` command for the requested taps, swipes, text entry, screenshots, or app navigation. Keep actions within the user's stated scope.
- End the session as soon as the requested phone interaction is complete:
  `curl -fsS -X POST http://127.0.0.1:8765/v1/control/stop`
- The user can stop control at any time from Pandora's colored screen frame. A stop disconnects ADB; do not reconnect unless the user asks for another control session.

## Agent notifications

- When the user asks to be notified, or asynchronous work finishes and is ready for review, send one concise Android notification:
  `curl -fsS -X POST --data-urlencode 'title=Pandora' --data-urlencode 'message=The requested work is ready.' http://127.0.0.1:8765/v1/notify`
- Do not notify for ordinary tool calls, intermediate progress, or every chat response.
- If notifications are disabled, mention it once in chat and do not repeatedly retry or open Android settings yourself.
