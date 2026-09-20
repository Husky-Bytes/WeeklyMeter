# WeeklyMeter

**Your remaining Codex weekly usage, on your home screen or in a floating widget.**

A small Android widget that runs on your phone—no PC or external relay server required. Keep just `n%` or add the reset time and last successful update. Style home-screen widgets and an optional floating widget independently.

[한국어](README.md) · [English](README.en.md)

**[Download Android APK · 0.6.3](https://github.com/Husky-Bytes/WeeklyMeter/releases/download/v0.6.3/WeeklyMeter-0.6.3.apk)** · [Release notes](https://github.com/Husky-Bytes/WeeklyMeter/releases/tag/v0.6.3) · [Report a problem / suggest a feature](https://github.com/Husky-Bytes/WeeklyMeter/issues)

![Illustrative concept showing compact WeeklyMeter widgets and customization possibilities](docs/images/weeklymeter-overview.png)

*AI-generated illustrative concept with synthetic values; not a real-device screenshot or verification of the actual layout.*

*AI로 만든 설명용 콘셉트 이미지입니다. 수치는 가상이며 실제 기기 스크린샷이나 실제 화면 배치 검증 결과가 아닙니다.*

> An unofficial, personal experimental app. The percentage represents the remaining allowance for **one selected Codex seven-day limit**, not a combined allowance for every ChatGPT model. WeeklyMeter is not affiliated with or endorsed by OpenAI. Read the account-safety notes before connecting an account.

## Small widget. Your choice of details.

- **Start at 1×1, resize as needed** — show only the percentage or add selected rows. Actual minimum size and cell allocation depend on your launcher.
- **Float over other apps** — quickly tap a home widget three times, then drag to move it. Give it its own styling, width, and height. Display permission must be granted by you.
- **Inspect power and automatic-refresh status** — view battery restrictions and recent automatic-work metadata, then open phone settings yourself. This does not bypass power policy.
- **Tap to refresh** — a direct refresh path that does not open the app screen. A check mark appears only after valid new data has been saved.
- **Fade the entire widget** — background, text, logo, and feedback together, independently for home and floating widgets. Background-only opacity remains available.
- **Make it fit your home screen** — color wheel, fonts, per-row text sizes, ordering, position, and internal padding.
- **Choose each date/time component** — reset time and last successful update have independent formats. Time only is an option.
- **Pinned preview at the selected size** — selected dp dimensions are mapped to the device density. Large previews scroll inside the pinned area instead of shrinking. Open home or floating customization directly from the main screen.
- **Korean, English, and localized app names** — the default follows your primary system language, with a globe selector for manual choices. No additional permission is needed.
- **An icon shaped by your launcher** — adaptive layers let the phone apply its usual rounded icon shape.
- **Sign in through your phone browser** — enter your password only on the official OpenAI login page. No PC or relay server is required.

## Get started

Targets Android 8.0 and later. Designed with Galaxy S25 Ultra in mind, but actual S25 Ultra / One UI behavior has not yet been verified.

On first launch, the app uses **Korean when your primary system language is Korean, and English otherwise**. It does not inspect country or location and needs no additional permission. Use the **globe at the top → System default / 한국어 / English** to change it. The app name is **주간 잔여량** in Korean and **WeeklyMeter** in English.

1. Download the **APK** above. When updating an existing installation, install the same-signature APK over it without uninstalling the app first.
2. In the app, choose **Sign in with ChatGPT → Open browser**. Enter your password only on the official OpenAI page in your phone browser.
3. Return to the app after signing in. The first connection requests usage once. Select the same weekly limit shown on the official usage screen.
4. Add a WeeklyMeter widget to your home screen. Open **Customize home widget** in the app; changes are saved automatically. **Customize floating widget** sits directly below it at the same level.
5. Tap the widget to refresh. Enable **last successful update** to tell when new data was received even if the percentage stays the same.

Choose **Refresh interval** and enter any whole number from **15 to 10080 minutes (7 days)**, or use the 15 / 30 / 60 minute shortcuts. Out-of-range and fractional values are rejected without closing the input dialog. Home and floating widgets share the interval; scheduling is enabled while a home widget exists or a floating session is active.

Successful refreshes publish usage and the last successful refresh time to both displays. The floating widget does not create separate polling. **Opening the app/floating window or saving interval, language, style, or size does not fetch usage.** Interval changes update scheduling. Android periodic jobs have a 15-minute minimum; battery/network restrictions may delay execution further. Exact timing and real-time updates are not guaranteed.

## Use the floating widget

1. Choose **Show / hide floating widget → Show** in the app. On first use, follow the prompt and grant **Display over other apps** in system settings yourself. Home-screen widgets do not need this permission.
2. You can then **tap the same home widget three times within 0.9 seconds** to show it. The first tap requests the usual refresh; the second and third do not add separate requests.
3. **Tap to refresh**, **drag to move**, or **hold for 0.6 seconds to close**. You can also close it from the app's floating menu or service notification.
4. Choose **Customize floating widget** directly on the main screen to adjust fonts, colors, rows, dates, and feedback independently of home widgets. There is no nested show/hide menu to enter first. The pinned preview's **Resize** button opens the width/height settings.

The default is **128×96dp**; width is **48–360dp** and height **48–300dp**. The preview maps the **selected dimensions** to the device density. The actual floating window may be smaller when constrained by available screen space. Resetting floating appearance does not change home-widget settings.

**Background & opacity → Overall opacity** affects background, text, logo, and feedback together: **100% keeps the original appearance; 0% is fully transparent**. Use **Background opacity** to change only the backdrop. At 0%, the floating window and its touch area remain active; close it through **Show / hide floating widget → Hide** in the app.

The app does not read other apps' contents; it displays its own saved usage. The overlay hides while the screen is off or locked and returns after unlocking if the session is still running. Temporary hiding does not restart the automatic-refresh interval. It does not reopen automatically after reboot or process death. Closing the floating session cancels automatic scheduling if no home widgets remain.

A foreground service and notification are maintained while the floating session runs. On Android 13+, no separate notification permission is requested, so the notification may be absent from the drawer while the system's active-app list still shows the service. Phone policy may restrict display or execution. **Actual overlay rendering, touch, and lock-screen return on S25 Ultra remain unverified.**

Since 0.6.3, the floating window itself no longer requests capture protection. **Its usage numbers and dates may appear in screenshots or screen recordings.** Check before sharing or close the widget first. The app's **main/account screen remains capture-protected**, so capture may still be restricted over that screen. This does not disable another app's or device's capture protection.

## When automatic refresh is delayed in power saving

Open **Auto refresh · Battery settings** for power saving, battery-optimization exemption, app background restriction, scheduled-job state, and recent automatic attempts/results. **Check again** only reads status; it does not fetch usage. Unsupported information is shown as unavailable; a scheduled job does not mean execution at an exact time.

Choose **Battery settings → App battery settings** or **Optimization exceptions**, then select Unrestricted / Don't optimize for this app yourself. Check Samsung sleeping/deep-sleep lists and add it to Never sleeping apps where available. One UI menu names/locations vary; this can increase battery use. [Samsung guidance](https://www.samsung.com/us/support/galaxy-battery/optimization/)

An app exception does not disable device-wide power saving. Android scheduling or manufacturer background-data restrictions can still prevent refresh. **The app neither changes settings for you nor bypasses restrictions.** [Android resource limits](https://developer.android.com/topic/performance/power/power-details) · [Setup and release notes](CHANGELOG-0.5.2.md)

Recent attempt times, outcomes, stop reasons, and publication results are local metadata recorded from 0.5.2 onward. A missing record or missing completion alone cannot establish the cause. They do not collect or upload tokens, authorization codes, HTTP contents, or raw logs. Publication success means an update was passed to Android, not that the actual home screen was visually observed changing. The cause and resolution of the user's S25 Ultra power-saving behavior have not been verified on the device.

## Before connecting your account

Browser login and encrypted storage are implemented, but **the authentication tokens are not restricted to read-only usage access**. This is an unofficial authentication implementation using an internal usage endpoint, so service changes may break it. A successful build and valid APK signature do not establish that connecting your account is safe.

The app code only performs authentication exchange/refresh and usage requests. It does not send chats or model-generation requests, and contains no advertising or analytics SDK. However, no live-account before/after comparison of usage limits or charges has been performed. Tokens are encrypted with Android Keystore and stored outside backups. Automatic token refresh is attempted, but permanent login cannot be guaranteed.

**Real-account login/token refresh, credential storage in Android Keystore, installation on a physical phone, and S25 Ultra / One UI rendering, battery restrictions, reboot and long-term behavior remain unverified.** See the version-specific local/emulator coverage in the [security scope (Korean)](SECURITY.md) and [recorded checks (English)](TEST-RESULTS.txt).

## Detailed behavior

<details>
<summary>Fonts, colors, dates, and layout options</summary>

- Independently show or hide the percentage, reset time, last successful update time, and ChatGPT identifier.
- Set each row's font, size, weight, color, alignment, vertical position, and order.
- Four bundled fonts accompany the default font: Nanum Gothic, Jua, Nanum Myeongjo, and Nanum Gothic Coding. No additional font download is needed. Text sizes range from 6 to 96sp in 0.5sp steps.
- Configure year, month, day, weekday, hour, minute, second, and AM/PM separately for both time rows. Choose date only, time only, or neither, plus 12/24-hour time, leading zeros, date separator, one/two lines, and optional labels.
- Pick colors using a color wheel, brightness, HEX input, or recent colors. Overall and background opacity each range from 0 to 100%. Existing styles default to 100% overall opacity, so upgrading alone does not change their appearance. Corners and automatic fitting are adjustable too.
- The pinned preview sits above four descriptive cards: Text & dates, Layout & spacing, Background & opacity, and Refresh feedback. A separate element picker replaces the second tab row. Redundant explanations were removed, and Korean wording is concise and polite.
- Disable automatic spacing to adjust horizontal/vertical internal padding from 0 to 32dp and row spacing from 0 to 16dp, in 0.5dp steps. Excessive padding is limited to preserve content space at 1×1. This does not remove margins reserved outside the widget by the launcher.
- Choose no ChatGPT identifier, text, logo, or both. Logo size is adjustable; its original shape and clear space are preserved in black/white. It does not indicate an official app.
- Home previews offer widget sizes reported by the launcher. If size information is unavailable, they are explicitly labeled **estimated 1×1 / 2×1**, not measured home-widget dimensions. The preview does not reproduce launcher cells, outer margins, or wallpaper.
- Both preview modes map selected dp dimensions to device density. Only the pinned viewport height is capped for the available screen/keyboard space; overflowing content scrolls horizontally or vertically without shrinking. The preview remains above the settings list and displays synthetic data without fetching usage.

Normally, only selected content appears. Missing valid usage or expired cache is shown as `—%`; a missing source date is `—`, never an invented current time. Hiding all date components removes that row.

</details>

<details>
<summary>Refresh feedback, scheduling, and percentage calculation</summary>

A widget tap shows acknowledgement, then running feedback. A success check appears only when valid new data for the selected limit has been saved. An exclamation mark indicates an error; dots indicate waiting or request throttling, not success. Error details can be viewed in the app.

Completion feedback defaults to **1.0 second**. Independently for home and floating widgets, set 0.1–10.0 seconds in 0.1-second steps using the slider/direct input, or disable feedback. Exact disappearance timing is not guaranteed: process death, battery restrictions, or launcher delays can retain the image. Expiry is checked again on the next render.

Manual taps use an explicit widget `PendingIntent` to start a short foreground service directly, without the job queue or opening an app Activity. Stored connection information is restored independently. A temporary system notification or running-app indicator may appear. Automatic scheduling uses `JobScheduler`. This background path has not been tested on a real S25 Ultra / One UI device.

In 0.5.1, the scheduled worker first reconciles saved credentials, then publishes saved usage to all widgets before job completion. One widget's failure is isolated from others, and rendering is ordered so an older overlapping render cannot replace newer content. This does not add HTTP usage requests or exact alarms. The missed-update paths were reproduced using Android test doubles, not on the user's S25 Ultra.

Usage requests occur only after a widget tap, the app's explicit refresh button, an automatic job, or initial login completion. Opening/returning to the app and changing settings do not request usage. Failed requests and taps do not replace the last successful update time with the current time. Repeated taps are coalesced; a 10-second limit and server retry delays are respected.

The app calculates `100 - used percent` from a Codex limit of exactly seven days (604800 seconds / 10080 minutes). A missing selected limit is not silently replaced with another. Values are the last successful result. Known reset time, maximum seven-day cache age, and clock-backwards checks can expire the value; the next render then shows `—%`. Immediate expiry rendering is not guaranteed.

A 55-second cooperative cancellation watchdog and a 70-second service cap are not strict total HTTPS response deadlines. See the [security scope (Korean)](SECURITY.md) for network and process limitations.

</details>

<details>
<summary>Browser login and session retention</summary>

External-browser login uses PKCE S256 and a random `state`. A temporary listener on `127.0.0.1:1455` receives the callback on the phone itself, only during login. It is not a PC or external server. There is no in-app password UI, WebView login, cookie extraction, or token-paste workflow.

Tokens are encrypted with Android Keystore AES-GCM, saved outside backups, and refreshed during usage requests. Server expiry/revocation, cleared app data, damaged keys, or forced process termination during token rotation may require signing in again. The ten-minute login waiting period is not the lifetime of a saved session.

This is an unofficial Android implementation based on the public Codex authentication flow. Its tokens are not restricted to read-only usage access. It does not defend against a rooted/compromised OS or code running with the app's own privileges. Usage displayed on the home screen is visible to people nearby.

</details>

<details>
<summary>Language, app-name, and icon differences between Android versions</summary>

System default considers only the primary system language. English first and Korean second still selects English. Changing language redraws existing widget data without querying the account, and preserves selected date elements, fonts, and styling.

Android 13+ integrates with per-app languages. Automatic mode pins the resolved single language in Android to avoid choosing a secondary Korean language. Android Settings may therefore show Korean or English while the app's globe selector still shows System default. To return to automatic selection, choose System default in the app's globe menu.

The name inside the app follows the selected language. Depending on Android and the launcher, home-screen labels may follow the system language or remain cached instead of following the app's manual choice. Manual selection does not guarantee a launcher-name change; actual One UI behavior has not been verified.

The launcher masks the adaptive foreground/background into the phone's circle, rounded square, or other shape. An Android 13+ monochrome resource is also supplied for supported launchers' themed icons. The app does not force one corner shape on every phone. Actual S25 Ultra icon/themed-icon rendering remains unverified.

</details>

## Version and verification

0.6.3 removes `FLAG_SECURE` from the floating window only. The previous flag could cause screen capture restrictions while the overlay was visible. Protection for the main/account screen, lock-screen hiding, movement, refresh, customization, authentication, and permissions remain unchanged. [Full changelog · Korean / English](CHANGELOG-0.6.3.md) · [Android capture protection reference](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)

The **0.6.3 APK passed its Windows Android build, alignment, same-certificate signing verification, and 15,026 host executable checks**, plus 49 separate static icon checks and checks across 18 XML files/resources. Separately, **99 Android 15 (API 35) emulator runtime checks** verified capture protection removed from the actual floating window, other window settings and main-screen protection preserved, and retained behavior. Successful OS screenshots or screen recording have not been verified. [Detailed results](TEST-RESULTS.txt)

Physical S25 Ultra / One UI screenshots and screen recording, actual keyboard/finger input, installed home-widget dimensions, and power-saving behavior remain unverified. Host/emulator checks do not establish live-account safety or resolution on a physical device.

<details>
<summary>Build from source / verify the download</summary>

Windows build environment: JDK 21.0.8, Android platform 36, build-tools 35.0.0, Python 3. App metadata: minSdk 26 / targetSdk 35, package `dev.yerin.weeklymeter`, versionCode 13.

```powershell
.\build-apk.ps1 -Project . -BuildDirectory ..\build-current -SigningDirectory ..\private-signing -Sdk D:\Android\Sdk -Jdk 'C:\Program Files\Android\Android Studio\jbr'
```

Replace SDK/JDK paths with your local installation paths and use a fresh `BuildDirectory` for each build. The output is `dist/WeeklyMeter.apk` inside that directory. Updating an existing installation requires the same signing key. An APK signed with your own new key cannot directly replace the published APK. Never include keys, passwords, or account data in source or distribution files.

On Linux, set `ANDROID_HOME` and run `bash build-apk.sh`. A full Linux build and byte-for-byte reproducibility have not been verified. Raw build logs containing personal local paths are excluded from the public distribution.

SHA-256 of the published `WeeklyMeter-0.6.3.apk`:

```text
e73c1eb8a79515037a9a015d4ac8d9f8cc5012a81c4d602e59be595594baf55e
```

A matching hash verifies file identity, not the safety of the app.

</details>

## Feedback welcome

Interested in tiny widgets or customizing your home screen? Share feedback in [Issues](https://github.com/Husky-Bytes/WeeklyMeter/issues). Include your device model, Android / One UI version, app version, steps to reproduce, and expected versus actual behavior. Font rendering, layout, refresh feedback, and behavior after battery-saving periods are useful areas to check.

**Never post passwords, login tokens, authorization codes, login callback URLs, account files, or raw logs.** Screenshots are optional; remove account details, notifications, and other personal information before sharing one. Do not disclose security secrets in public issues.

## Names and fonts

ChatGPT and the OpenAI logo are OpenAI trademarks. The optional widget identifier names the service being queried; it does not imply endorsement or affiliation.

Bundled Nanum Gothic, Jua, Nanum Myeongjo, and Nanum Gothic Coding fonts are distributed under SIL Open Font License 1.1. Original notices and full licenses are retained in the [app notices](app/src/main/assets/NOTICES.txt) and [font directory](app/src/main/assets/fonts). No separate project-wide license has currently been assigned to this repository.
