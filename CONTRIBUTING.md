# Contributing to Pandora

Thanks for helping improve Pandora. Before opening a large pull request, start
with an issue so the approach and Android/runtime implications can be discussed.

## Development setup

You need JDK 17 and Android SDK 35. Pandora currently builds and runs only for
ARM64 Android devices.

```bash
./gradlew test lint assembleDebug
```

Install the debug APK with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pull requests

- Keep changes focused and explain the user-visible behavior.
- Add or update tests for behavior that can be covered on the JVM.
- Run the verification command above before requesting review.
- Update `CHANGELOG.md` under `Unreleased` for user-visible changes.
- Do not commit credentials, signing keys, local SDK paths, downloaded speech
  models, or workspace backups.
- Preserve license and attribution notices when changing bundled or adapted code.

By contributing, you agree that your contribution is licensed under the
GPL-3.0-or-later license used by this repository.
