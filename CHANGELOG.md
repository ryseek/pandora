# Changelog

All notable changes to Pandora are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

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
