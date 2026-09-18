# WeeklyMeter

**Your remaining Codex weekly usage, at a glance on your Android home screen.**

A small Android widget that runs on your phone—no PC or external relay server required. Keep just `n%`, add the reset time or last successful update, and style it to fit your home screen.

[한국어](README.md) · [English](README.en.md)

**[Download Android APK · 0.5.1](https://github.com/Husky-Bytes/WeeklyMeter/releases/download/v0.5.1/WeeklyMeter-0.5.1.apk)** · [Release notes](https://github.com/Husky-Bytes/WeeklyMeter/releases/tag/v0.5.1) · [Report a problem / suggest a feature](https://github.com/Husky-Bytes/WeeklyMeter/issues)

![Illustrative concept showing compact WeeklyMeter widgets and customization possibilities](docs/images/weeklymeter-overview.png)

*AI-generated illustrative concept with synthetic values; not a real-device screenshot or verification of the actual layout.*

*AI로 만든 설명용 콘셉트 이미지입니다. 수치는 가상이며 실제 기기 스크린샷이나 실제 화면 배치 검증 결과가 아닙니다.*

> An unofficial, personal experimental app. The percentage represents the remaining allowance for **one selected Codex seven-day limit**, not a combined allowance for every ChatGPT model. WeeklyMeter is not affiliated with or endorsed by OpenAI. Read the account-safety notes before connecting an account.

## Small widget. Your choice of details.

- **Start at 1×1, resize as needed** — show only the percentage or add selected rows. Actual minimum size and cell allocation depend on your launcher.
- **Tap to refresh** — a direct refresh path that does not open the app screen. A check mark appears only after valid new data has been saved.
- **Make it fit your home screen** — color wheel, transparency, fonts, per-row text sizes, ordering, position, and internal padding.
- **Choose each date/time component** — reset time and last successful update have independent formats. Time only is an option.
- **Preview stays visible while editing** — choose from four descriptive cards, then a separate element picker. Styling is shared by all installed widgets.
- **Korean, English, and localized app names** — the default follows your primary system language, with a globe selector for manual choices. No additional permission is needed.
- **An icon shaped by your launcher** — adaptive layers let the phone apply its usual rounded icon shape.
- **Sign in through your phone browser** — enter your password only on the official OpenAI login page. No PC or relay server is required.

## Get started

Targets Android 8.0 and later. Designed with Galaxy S25 Ultra in mind, but actual S25 Ultra / One UI behavior has not yet been verified.

On first launch, the app uses **Korean when your primary system language is Korean, and English otherwise**. It does not inspect country or location and needs no additional permission. Use the **globe at the top → System default / 한국어 / English** to change it. The app name is **주간 잔여량** in Korean and **WeeklyMeter** in English.

1. Download the **APK** above. When updating an existing installation, install the same-signature APK over it without uninstalling the app first.
2. In the app, choose **Sign in with ChatGPT → Open browser**. Enter your password only on the official OpenAI page in your phone browser.
3. Return to the app after signing in. The first connection requests usage once. Select the same weekly limit shown on the official usage screen.
4. Add a WeeklyMeter widget to your home screen. Open **Customize widget** in the app; changes are saved automatically.
5. Tap the widget to refresh. Enable **last successful update** to tell when new data was received even if the percentage stays the same.

Automatic refresh can be scheduled every 15 / 30 / 60 minutes while a widget is installed. Successful automatic refreshes publish both usage and the last successful refresh time to the widget. Version 0.5.1 fixes skipped background connection restoration and dropped widget publication after saving. **Opening the app or changing language/style does not trigger a network refresh.** Android battery and network restrictions may delay scheduled work; this is not a real-time display.

## Before connecting your account

Browser login and encrypted storage are implemented, but **the authentication tokens are not restricted to read-only usage access**. This is an unofficial authentication implementation using an internal usage endpoint, so service changes may break it. A successful build and valid APK signature do not establish that connecting your account is safe.

The app code only performs authentication exchange/refresh and usage requests. It does not send chats or model-generation requests, and contains no advertising or analytics SDK. However, no live-account before/after comparison of usage limits or charges has been performed. Tokens are encrypted with Android Keystore and stored outside backups. Automatic token refresh is attempted, but permanent login cannot be guaranteed.

**Real-account login/token refresh, Android Keystore on a real device, APK installation, and S25 Ultra / One UI rendering, battery restrictions, reboot and long-term behavior remain unverified.** This distribution is for people comfortable evaluating those limitations. See the [security scope (Korean)](SECURITY.md) and [recorded checks (English)](TEST-RESULTS.txt).

## Detailed behavior

<details>
<summary>Fonts, colors, dates, and layout options</summary>

- Independently show or hide the percentage, reset time, last successful update time, and ChatGPT identifier.
- Set each row's font, size, weight, color, alignment, vertical position, and order.
- Four bundled fonts accompany the default font: Nanum Gothic, Jua, Nanum Myeongjo, and Nanum Gothic Coding. No additional font download is needed. Text sizes range from 6 to 96sp in 0.5sp steps.
- Configure year, month, day, weekday, hour, minute, second, and AM/PM separately for both time rows. Choose date only, time only, or neither, plus 12/24-hour time, leading zeros, date separator, one/two lines, and optional labels.
- Pick colors using a color wheel, brightness, HEX input, or recent colors. Adjust background opacity, corners, and automatic fitting.
- The pinned preview sits above four descriptive cards: Text & dates, Layout & spacing, Background, and Refresh feedback. A separate element picker replaces the second tab row. Redundant explanations were removed, and Korean wording is concise and polite.
- Disable automatic spacing to adjust horizontal/vertical internal padding from 0 to 32dp and row spacing from 0 to 16dp, in 0.5dp steps. Excessive padding is limited to preserve content space at 1×1. This does not remove margins reserved outside the widget by the launcher.
- Choose no ChatGPT identifier, text, logo, or both. Logo size is adjustable; its original shape and clear space are preserved in black/white. It does not indicate an official app.
- 1×1 / 2×1 example previews and overflow warnings are provided. Actual home-screen dimensions, background, and text scaling can differ.

Normally, only selected content appears. Missing valid usage or expired cache is shown as `—%`; a missing source date is `—`, never an invented current time. Hiding all date components removes that row.

</details>

<details>
<summary>Refresh feedback, scheduling, and percentage calculation</summary>

A widget tap shows acknowledgement, then running feedback. A success check appears only when valid new data for the selected limit has been saved. An exclamation mark indicates an error; dots indicate waiting or request throttling, not success. Error details can be viewed in the app.

Completion feedback defaults to **1.0 second**. Set 0.1–10.0 seconds in 0.1-second steps using the slider/direct input, or disable feedback. Exact disappearance timing is not guaranteed: process death, battery restrictions, or launcher delays can retain the image. Expiry is checked again on the next render.

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

0.5.1 fixes paths where automatic usage could be skipped before saved credentials were restored, or a widget update could be dropped after usage was saved. Multiple widgets and overlapping renders are also protected. Existing language, icon, customization, login, and tap-to-refresh behavior remain. [Full changelog · Korean / English](CHANGELOG-0.5.1.md)

The full Windows Android build, APK generation/alignment/signature verification, **4,499 executable checks**, adaptive-icon checks, and XML/resource consistency checks passed. These include local logic/file checks and Android test doubles—not successful real-device or live-account tests. [Detailed results and unverified areas](TEST-RESULTS.txt)

<details>
<summary>Build from source / verify the download</summary>

Verified Windows build environment: JDK 21.0.8, Android platform 36, build-tools 35.0.0, Python 3. App metadata: minSdk 26 / targetSdk 35, package `dev.yerin.weeklymeter`, versionCode 8.

```powershell
.\build-apk.ps1 -Project . -BuildDirectory ..\build-current -SigningDirectory ..\private-signing -Sdk D:\Android\Sdk -Jdk 'C:\Program Files\Android\Android Studio\jbr'
```

Replace SDK/JDK paths with your local installation paths and use a fresh `BuildDirectory` for each build. The output is `dist/WeeklyMeter.apk` inside that directory. Updating an existing installation requires the same signing key. An APK signed with your own new key cannot directly replace the published APK. Never include keys, passwords, or account data in source or distribution files.

On Linux, set `ANDROID_HOME` and run `bash build-apk.sh`. A full Linux build and byte-for-byte reproducibility have not been verified. Raw build logs containing personal local paths are excluded from the public distribution.

SHA-256 of the published `WeeklyMeter-0.5.1.apk`:

```text
f4b22b9caa3f1b0d31b1a8a778f32ede1073e0ed321503bbc34d8f45a9699f1c
```

A matching hash verifies file identity, not the safety of the app.

</details>

## Feedback welcome

Interested in tiny widgets or customizing your home screen? Share feedback in [Issues](https://github.com/Husky-Bytes/WeeklyMeter/issues). Include your device model, Android / One UI version, app version, steps to reproduce, and expected versus actual behavior. Font rendering, layout, refresh feedback, and behavior after battery-saving periods are useful areas to check.

**Never post passwords, login tokens, authorization codes, login callback URLs, account files, or raw logs.** Screenshots are optional; remove account details, notifications, and other personal information before sharing one. Do not disclose security secrets in public issues.

## Names and fonts

ChatGPT and the OpenAI logo are OpenAI trademarks. The optional widget identifier names the service being queried; it does not imply endorsement or affiliation.

Bundled Nanum Gothic, Jua, Nanum Myeongjo, and Nanum Gothic Coding fonts are distributed under SIL Open Font License 1.1. Original notices and full licenses are retained in the [app notices](app/src/main/assets/NOTICES.txt) and [font directory](app/src/main/assets/fonts). No separate project-wide license has currently been assigned to this repository.
