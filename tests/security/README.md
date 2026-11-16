# Security regression checks

Run from the repository root with JDK 17 or newer:

```sh
sh tests/security/run.sh
```

The tests compile the production URL/HTTP and diagnostic-redaction helpers. HTTP
connections are simulated in memory: no Google account, device, or network is
used. They cover domain spoofing, user-info and port bypasses, HTTPS downgrades,
relative redirects, redirect loops, connection cleanup, valid authenticated and
anonymous downloads, and removal of credentials and private URLs from logs.
The same checks run in the build and release workflows.

Authenticated direct media downloads accept HTTPS Google Photos/media hosts
only, and validate every redirect before connecting. Unknown hosts fail closed;
if Google introduces a new media host, verify its ownership and add regression
coverage before extending the allowlist.

Pixel spoofing, backup profiles, flags, AI models, and Toolkit actions are retained.
The existing all-files permission remains optional and is requested by the
Locked Folder move action; removing it would break that operation for local
camera media. It does not grant access to other apps' private storage.

A successful bundle build and these checks do not replace device verification.
Before using a rebuilt APK with personal media, check Google/MicroG sign-in,
both backup profiles, creation downloads, Toolkit export, and moving a disposable
photo into and out of Locked Folder on the target Android version. Test backups
with disposable files and verify the resulting quota accounting independently.
