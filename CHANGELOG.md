# Changelog

All notable changes to Pandora are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

## 0.1.2 - 2026-08-14

### Added

- Multiline chat composition: the keyboard Enter key now inserts a newline while the composer button sends the message.
- A Stop action in the composer while Codex is generating, backed by the app-server turn interruption protocol.
- Direct installation for APK files shared by the agent, including dedicated Install actions in attachment rows and previews.

### Changed

- Grouped ADB phone-control setup, connection status, and actions inside the plugin card.
- Moved dictation, speech-model, and reading-voice controls to a dedicated Voice settings screen.
- Open saved Codex OSS provider editing from Settings without returning to onboarding.

## 0.1.1 - 2026-08-12

### Added

- Codex OSS onboarding with bring-your-own OpenAI-compatible endpoint, API key, and model identifiers.
- Multiple custom model identifiers with selection from the native chat model picker.
- A quick Settings switch between saved hosted Codex and Codex OSS profiles without signing in or entering credentials again.

### Security

- Custom provider keys are encrypted with Android Keystore and passed to Codex only through its process environment; keys are never written to Codex configuration.

## 0.1.0 - 2026-08-11

### Added

- Native Jetpack Compose workspaces for persistent Codex chats and Linux terminals.
- An ARM64 Debian 13 userspace running through PRoot without root access.
- Durable projects, terminal sessions, workspace backup and restore, and Git clone flows.
- On-device speech recognition and synthesis through downloadable Sherpa-ONNX models.
- A loopback-only agent bridge for visible ADB phone control and opt-in notifications.

### Security

- The PRoot environment is explicitly documented as a compatibility layer, not a security boundary.
- Workspace backups are staged and validated before restore and are documented as sensitive.
- Android platform backup is disabled so private workspaces and session credentials are not copied to cloud backup.
