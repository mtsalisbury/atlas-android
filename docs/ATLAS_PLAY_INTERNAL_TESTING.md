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
4. Upload `app/build/outputs/bundle/playRelease/Atlas-1.14.0-beta.12-play-release.aab`.
5. Accept Google Play App Signing when prompted. The local Atlas key remains the upload key.
6. Create an email tester list using the Google accounts on the Android phones and tablets.
7. Roll out the internal release, copy its opt-in link, and open that link on each test device.
8. Join the test, then install or update Project Atlas from Google Play.

Google Play internal testing supports up to 100 testers. Updates use the normal Play Store installation and update flow.

## Build another signed bundle

On this Mac, from the Android repository:

```sh
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/mikesalisbury/Library/Android/sdk \
./gradlew :app:bundlePlayRelease --console=plain
```

The build reads the signing password from macOS Keychain. It can also use the environment variables `ATLAS_KEYSTORE_PATH`, `ATLAS_KEYSTORE_PASS`, and `ATLAS_KEY_ALIAS_PASS` in an automated build system.

## Optional upload automation

After the first manual release, a Google Play service account can be connected for automated uploads. Store its credentials as `service-account-credentials.json`; that filename is already excluded from Git.
