# Pandora Android prototype

Pandora is an early Android prototype for a local, persistent AI-agent workspace.

This milestone includes:

- a native Jetpack Compose workspace combining durable Codex chats and Linux terminals;
- a streaming chat interface backed by the native Codex app-server agent harness;
- a real ARM64 Alpine Linux userspace running through PRoot without root;
- a persistent app-private root filesystem;
- a real PTY terminal with ANSI color, cursor, scrollback, selection, and interactive programs;
- a keyboard-safe viewport and pinned Esc/Tab/Ctrl/Alt/navigation key row;
- a persistent terminal font-size setting with preview;
- a foreground Linux-session service that keeps the PTY and network alive while Pandora is briefly backgrounded;
- multiple retained terminal sessions with preserved scrollback, explicit Stop controls, and navigation that leaves processes running;
- pinned, checksum-verified zmx 0.7.0 for named terminal attach/detach, restored terminal state, and detached-session discovery;
- reproducible default packages after install and Repair (`ca-certificates`, `ssl_client`, `lscpu`, `util-linux`, `nodejs`, `npm`, `git`, `ripgrep`);
- a persistent Codex CLI installation under `/root/.local`, preserved across Repair;
- device-aware Codex instructions in persistent `/root/.codex/AGENTS.md`, installed without overwriting user edits;
- a PRoot-compatible Codex full-access default, avoiding unsupported Bubblewrap/user-namespace sandboxing;
- live Codex allowance on Home through the official app-server rate-limit API, with tap-to-refresh and offline/sign-in states;
- durable native Codex chat threads using `thread/list`, `thread/start`, `thread/resume`, and `turn/start`, with streamed agent messages, model selection, and full local workspace tools;
- directory-based projects that group chats by Codex working directory, with existing-folder, new-folder, and Git-clone creation flows;
- a Settings helper that opens Codex browser login and keeps the OAuth exchange active in the background;
- versioned workspace backup and staged restore from Settings, including projects, configuration, credentials, executable bits, and symlinks;
- package installation and network access from the container.

## Build

The project requires JDK 17 and Android SDK 35. It currently targets ARM64 devices only. If Android Studio ships a newer JDK, point `JAVA_HOME` at a JDK 17 installation for command-line builds.

```bash
./gradlew :app:assembleDebug
```

Install the resulting APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or build and install the latest local source on a connected physical device in one step:

```bash
./install-device.sh
```

When multiple physical devices are connected, pass the desired serial from `adb devices`:

```bash
./install-device.sh DEVICE_SERIAL
```

## Runtime model

The APK contains an Alpine ARM64 minirootfs and a PRoot executable. On first container launch, the rootfs is extracted to the app's private files directory. `/root` is stored separately and mounted into the container, so Repair can replace damaged Linux system files without touching user projects, Codex, or Codex configuration. Repair resets non-default packages installed with `apk` and system-level configuration, then reapplies Pandora's versioned default package manifest.

This is a userspace Linux environment sharing Android's kernel, not a VM and not a security boundary. The shell is attached to a native PTY, so terminal sizing, signals, arrow keys, and interactive full-screen TTY programs behave normally.

Codex runs with `sandbox_mode = "danger-full-access"` because Android's PRoot environment cannot provide the user namespaces and `/proc/sys` access required by Bubblewrap. Codex commands can therefore read and modify the complete Pandora Linux workspace without an additional sandbox boundary.

Pandora also installs global Codex guidance at `/root/.codex/AGENTS.md` from the APK on first setup. These instructions give every chat and project basic context about Android, Alpine, PRoot, persistent storage, Repair behavior, and runtime limitations. An existing file is left untouched so users can customize or replace the guidance, while project-level `AGENTS.md` files can add more specific instructions.

Workspace backups are portable ZIP files containing the complete persistent `/root`. They therefore contain sensitive Codex and GitHub sessions and should be stored accordingly. Restore validates and extracts into staging before atomically replacing the current workspace.

## License

Pandora is distributed under GPL-3.0-or-later. The bundled PRoot runtime is derived from the OpenMinis PRoot fork and is licensed under GPL-2.0. Alpine Linux packages retain their individual licenses. See `THIRD_PARTY_NOTICES.md`.
