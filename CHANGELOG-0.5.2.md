# WeeklyMeter 0.5.2

[한국어 안내](README.md) · [English guide](README.en.md) · [릴리스 / Release](https://github.com/Husky-Bytes/WeeklyMeter/releases/tag/v0.5.2)

## 한국어

절전 모드에서 자동 조회가 늦어질 때 확인할 상태와 폰 설정 안내를 추가하는 업데이트입니다. **절전 제한을 우회하거나 모든 갤럭시에서 자동 조회를 보장하는 기능은 아닙니다.**

- **자동 조회 상태 확인**: 절전 모드, 앱의 배터리 최적화 예외·백그라운드 제한, 예약 작업 상태를 읽어 표시합니다. Android에서 제공하지 않는 상태는 확인 불가로 구분합니다.
- **최근 자동 작업 기록**: 시도 시각, 조회 결과, 시스템 중지 사유, 위젯 갱신 게시 결과를 폰 안에 저장합니다. 토큰·인증 코드·HTTP 내용·원본 로그를 저장하거나 외부로 보내는 진단 기능은 아닙니다. 위젯 게시 성공은 Android에 갱신을 전달했다는 뜻이며 실제 홈 화면 표시를 관찰한 결과는 아닙니다.
- **사용자가 선택하는 배터리 설정**: 안내에서 시스템 설정을 열어 이 앱의 배터리 제한 없음 또는 최적화 예외를 직접 선택할 수 있습니다. 삼성 절전·초절전 앱 목록도 폰 설정에서 직접 확인합니다. 앱이 설정을 몰래 바꾸거나 삼성 목록을 읽어 해제하지 않습니다.
- **조회 방식 유지**: 기존 JobScheduler의 15 / 30 / 60분 주기를 유지합니다. 새 알람·백그라운드 상주 서비스·추가 권한·추가 알림·별도 HTTP 조회는 넣지 않습니다. 상태 창이나 앱을 열기만 해서는 사용량을 조회하지 않습니다. 기존 수동 탭 조회와 로그인 서비스는 그대로입니다.

### 폰에서 확인할 사항

1. 앱의 **자동 조회 상태 · 절전 설정**을 열어 최근 시도·결과와 배터리 제한 표시를 확인합니다. 기록은 이 버전부터 남습니다. **다시 확인**은 조회 요청 없이 상태만 다시 읽습니다.
2. **절전 설정 → 앱 배터리 설정 / 최적화 예외 설정**으로 이동해 **주간 잔여량 / WeeklyMeter**의 앱별 제한을 완화할지 선택합니다. 예외 설정은 배터리 사용을 늘릴 수 있습니다.
3. 삼성 **배터리 → 백그라운드 사용 제한**에서 이 앱을 절전·초절전 목록에서 빼고, 제공되는 경우 **절전 예외 앱 / Never sleeping apps**에 추가합니다. 메뉴 이름과 위치는 One UI 버전에 따라 다릅니다. [삼성 안내](https://www.samsung.com/us/support/galaxy-battery/optimization/)

앱별 예외와 폰 전체 절전 모드는 별개입니다. 삼성은 절전 모드에서 모든 앱의 백그라운드 데이터를 제한할 수 있다고 안내합니다. 이런 제한이 계속 적용되는 구성에서는 예외를 선택해도 자동 조회를 보장할 수 없습니다. [삼성 절전 안내](https://www.samsung.com/us/support/galaxy-battery/optimization/)

**사용자 S25 Ultra의 원인이 삼성 절전 정책이었다고 확정하거나 이번 수정이 실기기에서 해결됐다고 검증한 것은 아닙니다.** 정확한 주기·화면 갱신 시각은 보장하지 않습니다. 0.5.1의 연결 복원·저장 후 위젯 게시 수정도 유지합니다.

### 배포·검증

같은 서명의 `WeeklyMeter-0.5.2.apk`를 기존 앱 위에 업데이트 설치합니다. 패키지는 `dev.yerin.weeklymeter`, versionCode는 **9**입니다. 기존 앱을 먼저 지우지 마세요.

Windows Android 전체 빌드·APK 정렬·서명 확인, **5,556개 실행 검사**, 아이콘 정적 검사 49개와 XML 18개·리소스 검사를 통과했습니다. APK 자산 검사 36개는 서명 전후 실행했지만 합계에는 한 번만 포함했습니다. [상세 검사 기록](TEST-RESULTS.txt)

실계정 로그인·토큰 갱신, 실제 Keystore, APK 설치, 실제 S25 Ultra / One UI 절전 상태 동작은 미검증입니다. 비공식 인증 권한은 사용량 읽기 전용이 아니며 영구 로그인을 보장하지 않습니다. [보안 범위](SECURITY.md)

## English

This update adds status details and phone-setting guidance for delayed automatic refresh in power saving mode. **It does not bypass power restrictions or guarantee automatic refresh on every Galaxy phone.**

- **Read-only background status**: inspect power saving, battery-optimization exemption, app background restriction, and scheduled-job state. Unsupported platform information is shown as unavailable.
- **Recent automatic-work metadata**: record attempt time, outcome, platform stop reason, and widget-publication result locally. This is not token, authorization-code, HTTP-content, or raw-log collection, and diagnostics are not uploaded. Publication success means Android accepted an update, not that the real home screen was visually verified.
- **User-controlled battery settings**: open system settings to choose an app-specific unrestricted/optimization-exempt setting yourself. Check Samsung sleeping/deep-sleep lists in phone settings. The app does not silently change settings or inspect and clear Samsung's lists.
- **Same refresh mechanism**: keep JobScheduler at 15 / 30 / 60 minutes. No new alarms, persistent background service, permissions, notifications, or extra HTTP refreshes are added. Opening the status dialog or app does not fetch usage. Existing manual refresh and browser-login services remain.

### Check on your phone

1. Open **Auto refresh · Battery settings** to inspect recent attempts/results and restrictions. Records start with this version. **Check again** rereads status without requesting usage.
2. Choose **Battery settings → App battery settings / Optimization exceptions** to decide whether to unrestrict **WeeklyMeter / 주간 잔여량** or exempt it. This can increase battery use.
3. In Samsung **Battery → Background usage limits**, remove the app from sleeping/deep-sleep lists and add it to **Never sleeping apps** where available. Menu names and locations vary with One UI. [Samsung guidance](https://www.samsung.com/us/support/galaxy-battery/optimization/)

An app exception is separate from device-wide power saving. Samsung documents that power saving can restrict background data for all apps; an exception cannot guarantee refresh while such a restriction still applies. [Samsung power-saving guidance](https://www.samsung.com/us/support/galaxy-battery/optimization/)

**Samsung power policy has not been established as the cause on the user's S25 Ultra, and this update has not been verified to resolve that device's behavior.** Exact intervals and immediate repainting remain unguaranteed. The 0.5.1 connection-recovery and saved-data publication fixes are retained.

### Distribution and verification

Install the same-signature `WeeklyMeter-0.5.2.apk` over the existing installation. Package: `dev.yerin.weeklymeter`; versionCode: **9**. Do not uninstall first.

The full Windows Android build, APK alignment/signature verification, **5,556 executable checks**, 49 static icon checks, and checks across 18 XML files/resources passed. The 36 APK asset checks ran before and after signing but are counted only once. [Detailed results](TEST-RESULTS.txt)

Real-account login/refresh, real Keystore, installation, and actual S25 Ultra / One UI power-saving behavior remain unverified. Authentication is unofficial and not restricted to read-only usage data; permanent login is not guaranteed. [Security scope](SECURITY.md)

## APK SHA-256

```text
e56ddad5ab55b2510b341100643f8292306fb76bc651ae6143e209e1d5cbd501
```

해시 일치는 파일 동일성 확인이며 안전 보증이 아닙니다. / A matching hash verifies file identity, not app safety.
