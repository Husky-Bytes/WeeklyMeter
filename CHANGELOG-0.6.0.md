# WeeklyMeter 0.6.0

[한국어 안내](README.md) · [English guide](README.en.md) · [릴리스 / Release](https://github.com/Husky-Bytes/WeeklyMeter/releases/tag/v0.6.0)

## 한국어

다른 앱 위에 표시하는 **선택적 플로팅 위젯**을 추가합니다. 기존 홈 위젯·로그인·자동 조회·절전 상태 안내는 유지합니다.

- **열기**: 앱의 **플로팅 위젯 → 띄우기**, 또는 같은 홈 위젯을 **0.9초 안에 3번** 누릅니다. 첫 탭은 기존 새로고침을 요청하고 나머지 두 탭은 조회를 추가하지 않습니다.
- **조작**: 한 번 누르기 = 새로고침, 끌기 = 이동, **0.6초 길게 누르기 = 닫기**. 앱 메뉴와 서비스 알림에서도 닫을 수 있습니다.
- **따로 꾸미기**: **플로팅 위젯 → 꾸미기·크기**에서 글꼴·행별 크기·색·투명도·날짜 요소·순서·여백·조회 효과를 홈 위젯과 별도로 저장합니다. 기본값 복원도 서로 영향을 주지 않습니다.
- **크기**: 기본 **128×96dp**, 너비 **48~360dp**, 높이 **48~300dp**. 화면 범위를 넘으면 표시 영역에 맞춥니다. 설정 중 고정 미리보기는 선택한 비율을 유지합니다.
- **기존 조회값 공유**: 플로팅 표시는 저장된 값을 사용하며 별도 조회 주기를 만들지 않습니다. 창 열기·꾸미기·이동·크기 변경은 조회하지 않습니다. 자동/수동 조회 성공은 두 위젯에 반영합니다.
- **잠금과 종료**: 화면 꺼짐·잠금 때 숨기고 세션이 유지되면 잠금 해제 후 복귀합니다. 이때 자동 조회 주기를 초기화하지 않습니다. 재부팅·프로세스 종료 후에는 자동으로 띄우지 않습니다. 플로팅을 닫고 홈 위젯도 없으면 자동 예약을 취소합니다.

### 표시 권한과 알림

플로팅을 처음 열 때 시스템의 **다른 앱 위에 표시**를 직접 허용해야 합니다. 홈 위젯만 사용하면 허용할 필요가 없습니다. 다른 앱의 내용을 읽지 않습니다.

이 기능은 `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE` 권한과 실행 세션 동안 유지되는 foreground service를 추가합니다. Android 13 이상에서는 별도 알림 권한을 요청하지 않아 알림창 표시가 시스템 설정에 따라 달라질 수 있습니다. 플로팅을 길게 누르거나 앱에서 닫을 수 있습니다. [보안 범위](SECURITY.md)

### 설치와 검증

같은 서명의 `WeeklyMeter-0.6.0.apk`를 기존 앱 위에 설치합니다. 패키지 `dev.yerin.weeklymeter`, versionCode **10**. 앱을 먼저 삭제할 필요가 없습니다.

Windows Android 전체 빌드·APK 정렬·서명 확인, **12,601개 실행 검사**, 아이콘 정적 검사 49개와 XML 18개·리소스 검사를 통과했습니다. APK 자산 검사 36개는 서명 전후 실행했지만 합계에는 한 번만 포함했습니다. 대체 Android·순수 로직·소스 계약 검사는 실제 오버레이 표시 검증이 아닙니다. [상세 검사 기록](TEST-RESULTS.txt)

별도로 **Android 15(API 35) 에뮬레이터 실행 검사 31개**를 통과했습니다. 배포 APK 설치, 설정·비트맵 미리보기, 실제 창 부착, 가상 캐시 변경 반영, 크기·이동·길게 눌러 닫기·재열기, 로그아웃 상태의 탭 실패 경로를 확인했습니다. 네트워크를 끄고 테스트 권한·가상 데이터를 사용했습니다. 화면 꺼짐/복귀는 수신자 직접 호출 검사이며 실제 잠금·권한 UI·알림창·홈 런처 3회 탭·스크린샷 시각 검수는 미검증입니다.

**실제 S25 Ultra / One UI 플로팅 표시·터치·권한 허용·잠금 복귀·절전 동작과 실계정 로그인은 미검증**입니다. 이 업데이트는 절전 중 자동 조회의 실행을 보장하지 않습니다. 비공식 인증 토큰은 사용량 읽기 전용으로 제한되지 않으며 영구 로그인을 보장하지 않습니다.

## English

Adds an **optional floating widget over other apps**. Existing home widgets, login, automatic refresh, and battery diagnostics remain.

- **Open**: choose **Floating widget → Show**, or tap the same home widget **three times within 0.9 seconds**. The first tap requests the usual refresh; the other two do not add requests.
- **Interact**: tap to refresh, drag to move, **hold for 0.6 seconds to close**. The app menu and service notification also provide a close action.
- **Separate appearance**: **Floating widget → Style & size** saves fonts, row sizes/colors, opacity, date elements, order, spacing, and feedback independently of home widgets. Resetting one does not reset the other.
- **Size**: default **128×96dp**, width **48–360dp**, height **48–300dp**. Display size is constrained to available screen space. The pinned preview preserves the selected aspect ratio.
- **Shared saved usage**: no separate polling schedule. Opening, styling, dragging, or resizing the window does not fetch usage. Successful automatic/manual refreshes update both displays.
- **Lock and lifecycle**: hides while the screen is off/locked and returns on unlock if the session remains alive, without restarting the automatic-refresh interval. It does not reopen after reboot/process death. Closing the floating session cancels scheduling if no home widgets remain.

### Permission and notification

On first use, grant **Display over other apps** yourself in system settings. Home widgets do not require it. The app does not read other apps' contents.

The feature adds `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`, and a foreground service maintained for the session. No separate notification permission is requested on Android 13+, so drawer visibility depends on system settings. Hold the floating widget or use the app menu to close it. [Security scope](SECURITY.md)

### Installation and verification

Install the same-signature `WeeklyMeter-0.6.0.apk` over the existing app. Package `dev.yerin.weeklymeter`, versionCode **10**. Do not uninstall first.

The full Windows Android build, APK alignment/signature verification, **12,601 executable checks**, 49 static icon checks, and checks across 18 XML files/resources passed. The 36 APK asset checks ran before/after signing but are counted once. Android doubles, pure logic, and source contracts do not verify real overlay display. [Detailed results](TEST-RESULTS.txt)

Separately, **31 Android 15 (API 35) emulator runtime checks** passed for release-APK installation, settings/bitmap preview, actual window attachment, synthetic cache changes, resizing/dragging/hold-to-close/reopen, and the signed-out tap failure path. Networking was off and test permission/synthetic data were used. Screen-off/return used direct receiver callbacks, not real lock/unlock. Permission UI, notification drawer, three-tap home-launcher delivery, and screenshot visual QA remain untested.

**Real S25 Ultra / One UI overlay rendering, touch, permission granting, lock-screen return, power-saving behavior, and live-account login remain unverified.** This release does not guarantee refresh under power restrictions. Unofficial authentication tokens are not restricted to read-only usage access; permanent login is not guaranteed.

## APK SHA-256

```text
c851ca1a3465ef6eee432aeff2f40c7e30a6dff86dc947cbc1c1f2e7e6a21954
```

해시 일치는 파일 동일성 확인이며 안전 보증이 아닙니다. / A matching hash verifies file identity, not app safety.
