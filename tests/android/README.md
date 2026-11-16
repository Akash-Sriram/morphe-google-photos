# Device integration probe

`SecurityProbe.java` is a separate test-only instrumentation APK, not part of the
patch bundle. It must be signed with the same **test** certificate as the patched
`app.morphe.android.apps.photos` APK. Never distribute the probe with a release.

The default mode publishes four synthetic PNGs, checks their exact bytes and
MediaStore state, tests malicious filenames, CSV privacy/UTC/formula escaping,
diagnostic redaction including a synthetic crash, and read-only provider access.
Probe-created media and fixtures are deleted before instrumentation finishes.
It adds a synthetic crash entry to the test app's diagnostic sessions; it does
not crash the process. It does not modify existing photos or cloud albums.

The optional `cloud` mode reads up to 200 remote-media URLs from the active
account's local database, reports only their hostnames, and reads at most 4096
bytes of one existing cloud media item into memory. It tries anonymously, then
with the active account token if required. No media or tokens are printed or
saved. Use this mode only with the device owner's authorization. Network access
must be enabled for both the target and instrumentation packages on GrapheneOS.

Build with JDK 17+, SDK platform 36, and Android build tools:

1. Compile `SecurityProbe.java` against `android-36/android.jar` using `javac`.
2. Package class files in a JAR and convert it with `d8 --min-api 24`.
3. Link `AndroidManifest.xml` using `aapt2 link -I android.jar`.
4. Add `classes.dex` to that APK and sign it with the patched APK's test keystore.
5. Install using `adb install -t probe.apk`.

Run:

```sh
adb shell am instrument -w app.morphe.securityprobe/app.morphe.securityprobe.SecurityProbe
adb shell am instrument -w -e mode cloud app.morphe.securityprobe/app.morphe.securityprobe.SecurityProbe
```

`INSTRUMENTATION_CODE: -1` and a PASS/successful cloud result indicate success.
Android's shell command exit status alone is insufficient. The number of local
checks varies with the number of existing diagnostic sessions.

After testing, uninstall **only** `app.morphe.securityprobe`. Launch and manually
verify the patched app again. These probes do not establish correct backup
quota accounting, EXIF handling of creations, Locked Folder authentication,
Google sign-in POST redirects, or compatibility with other Photos/Android versions.
