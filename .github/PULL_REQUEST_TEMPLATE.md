## What and why

<!-- One or two sentences. Link the issue if there is one. -->

## How it was verified

- [ ] `./gradlew spotlessCheck lint testDebugUnitTest :core:test :core:koverVerify verifyRoborazziDebug assembleDebug verifyNoInternetPermission :baselineprofile:compileBenchmarkReleaseKotlin`
- [ ] New or changed shell commands are listed in `docs/COMMANDS.md` with a device result (not "verified" without one)
- [ ] New parser behaviour is covered by a recorded fixture
- [ ] Screenshots updated (`./gradlew recordRoborazziDebug`) if the UI changed

## Label

<!-- Exactly one of: feature, fix, maintenance (drives the release notes). -->
