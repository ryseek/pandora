# Release process

Pandora uses semantic versions and annotated Git tags such as `v0.1.0`.

## Prepare

1. Move the relevant `CHANGELOG.md` entries from `Unreleased` into a dated version.
2. Set `versionName` and increment `versionCode` in `app/build.gradle.kts`.
3. Run `./gradlew test lint assembleDebug` with JDK 17.
4. Build the release variant with `./gradlew :app:assembleRelease`.
5. Sign the release APK with a private Android signing key stored outside this repository.
6. Install and smoke-test the signed APK on an ARM64 device.

## Publish

1. Merge the release commit into `main` and confirm Android CI passes.
2. Create an annotated tag: `git tag -a vX.Y.Z -m "Pandora vX.Y.Z"`.
3. Push `main` and the tag: `git push origin main && git push origin vX.Y.Z`.
4. Create a GitHub release from the tag, copy the matching changelog section,
   and attach the signed APK plus its SHA-256 checksum.
5. Verify the release page, checksum, and installation instructions from a clean browser session.

Never commit a keystore, signing password, GitHub token, or generated signed APK.
