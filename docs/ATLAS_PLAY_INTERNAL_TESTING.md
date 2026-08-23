# Atlas Android internal testing

Atlas Android is prepared for private Google Play internal testing.

## Release identity

- App name: Project Atlas
- Application ID: `com.layer9i.atlas`
- Release format: Android App Bundle (`.aab`)
- Upload key: `~/.atlas/keys/atlas-upload.jks`
- Upload-key alias: `atlas-upload`
- Password storage: macOS Keychain, service `Project Atlas Android`, account `atlas-upload-key`

Do not add the upload key, its password, or Google service-account credentials to Git. Back up the upload key in a secure password vault or encrypted offline location.

## First Google Play setup

1. Sign in to Google Play Console with the organization account that will own Atlas.
2. Create an app named **Project Atlas** and complete the required app/contact declarations.
3. Open **Testing > Internal testing** and create a release.
4. Upload `app/build/outputs/bundle/playRelease/Atlas-1.14.0-beta.13-play-release.aab` (SHA-256 `5d4a6e7b87961ac218c38d6d68ece800a5ba7c39b8b6b3c8544acb6cede39a96`).
5. Accept Google Play App Signing when prompted. The local Atlas key remains the upload key.
6. Create an email tester list using the Google accounts on the Android phones and tablets.
7. Roll out the internal release, copy its opt-in link, and open that link on each test device.
8. Join the test, then install or update Project Atlas from Google Play.

Google Play internal testing supports up to 100 testers. Updates use the normal Play Store installation and update flow.

## Build another signed bundle

On this Mac, from the Android repository:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME=/Users/mikesalisbury/Library/Android/sdk \
./gradlew :app:bundlePlayRelease --console=plain
```

The build reads the signing password from macOS Keychain. It can also use the environment variables `ATLAS_KEYSTORE_PATH`, `ATLAS_KEYSTORE_PASS`, and `ATLAS_KEY_ALIAS_PASS` in an automated build system.

## Optional upload automation

After the first manual release, a Google Play service account can be connected for automated uploads. Store its credentials as `service-account-credentials.json`; that filename is already excluded from Git.

As of August 22, 2026, beta.14 build 718 produces a signed universal APK. Google Play upload and physical-device verification remain pending because Play service-account credentials and an attached Android device are not currently available.

## Private portal APK

For testing before the Google Play organization account is available, `:app:assemblePlayRelease` produces a signed universal APK. The current portal artifact is `Atlas-1.14.0-beta.14-play-universal.apk`, build 718 (SHA-256 `1e9d0b1a4dd98731204f804e6a2d4e198ec8bd4eff7b1b87652ed8d8aeacb9cb`). It is published under the stable protected-portal name `Atlas-Android.apk`; release binaries remain outside Git. This proves the signed release can be distributed directly, but it does not replace physical-device or later Google Play installation/update testing.
