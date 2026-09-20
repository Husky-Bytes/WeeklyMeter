# Isolated Android runtime smoke test

This test APK is **not part of the app release**. It instruments the signed release app on a fresh emulator, replaces only non-secret display/style preferences with synthetic fixtures, and never signs in or provides credentials.

Run only on the task's isolated Android emulator, `emulator-5580`, with AVD name `WeeklyMeter060`. The PowerShell runner refuses any other serial, verifies `ro.kernel.qemu=1`, and checks the AVD name before installation. Do not run it against a personal device or an emulator containing real account data.

Prerequisites:

- Build and install WeeklyMeter 0.6.0 on that fresh emulator.
- Turn off emulator Wi-Fi/mobile data and unlock/wake its screen.
- Grant its overlay app-op in this emulator only. This simulates the user's special-access grant; it does not test the permission prompt.
- Use the same existing signing directory as the app build. The script passes the password file to Android's signer without reading or printing it.

Example from the task workspace:

```powershell
.\work\github-publish\WeeklyMeter\test-android-runtime.ps1 `
  -AppBuildDirectory .\work\build-v060 `
  -SigningDirectory .\work\signing `
  -OutputDirectory .\work\runtime-v060-new `
  -Run
```

The test checks actual Android activity creation, settings preview rendering, window attachment, independent preferences, cache-to-bitmap updates, resizing, drag persistence, long-press dismissal, reopening and the signed-out manual-refresh path. The screen-off/unlock checks invoke the actual service receiver callback, not a physical device lock. They verify its Android window detach/reattach behavior but are not Samsung One UI, battery-saving or live-network validation.
