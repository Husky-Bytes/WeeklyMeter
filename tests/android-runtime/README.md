# Isolated Android runtime smoke test

This test APK is **not part of the app release**. It instruments the signed release app on a fresh emulator, replaces only non-secret display/style preferences with synthetic fixtures, and never signs in or provides credentials.

Asynchronous checks have bounded waits, and the complete instrumentation run has a 90-second deadline. Failure output identifies the failed assertion or deadline.

Run only on the task's isolated Android emulator, `emulator-5580`, with AVD name `WeeklyMeter060`. The PowerShell runner refuses any other serial, verifies `ro.kernel.qemu=1`, and checks the AVD name before installation. Do not run it against a personal device or an emulator containing real account data.

Prerequisites:

- Build and install WeeklyMeter 0.6.1 on that fresh Android 15 / API 35 emulator. The isolated AVD keeps its existing `WeeklyMeter060` name.
- Turn off emulator Wi-Fi/mobile data and unlock/wake its screen.
- Grant its overlay app-op in this emulator only. This simulates the user's special-access grant; it does not test the permission prompt.
- Use the same existing signing directory as the app build. The script passes the password file to Android's signer without reading or printing it.

Example from the task workspace:

```powershell
.\work\github-publish\WeeklyMeter\test-android-runtime.ps1 `
  -AppBuildDirectory .\work\build-v061 `
  -SigningDirectory .\work\signing `
  -OutputDirectory .\work\runtime-v061-new `
  -Run
```

The test checks actual Android activity creation, settings preview rendering, window attachment, independent preferences, cache-to-bitmap updates, resizing, drag persistence, long-press dismissal, reopening and the signed-out manual-refresh path.

For overall opacity, it renders the actual Android `WidgetRenderer` at 100%, 50% and 0%, identifies background, text and success-feedback pixels, and compares their alpha. RGB comparison allows a small premultiplication-rounding tolerance. It also verifies independent home/floating values and live floating-window repainting at 100 → 50 → 0 → 100 without another usage query. A fully transparent overlay remains an active, closable session; this test does not claim its touch area is disabled.

For custom refresh intervals, it checks real SharedPreferences values and safe fallback for invalid types/ranges. It opens the actual app-owned interval dialog, saves a typed 47-minute value, rejects 14 minutes, checks cancellation and the 30-minute preset, and verifies no account connection or usage query. The dialog is opened through its Activity method; this is not a navigation/discoverability test. It does not wait 47 minutes or establish an exact Android background execution schedule.

The screen-off/unlock checks invoke the actual service receiver callback, not a physical device lock. They verify its Android window detach/reattach behavior but are not Samsung One UI, battery-saving or live-network validation.
