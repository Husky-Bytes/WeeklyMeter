# WeeklyMeter 0.6.3

## 한국어

**플로팅 위젯이 캡처를 차단하던 설정을 수정했습니다.** 플로팅 창의 `FLAG_SECURE`만 제거했습니다. [Android 공식 설명](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)

- 플로팅 위젯의 사용량·날짜가 **스크린샷·화면 녹화에 포함될 수 있습니다.** 공유 전 확인하거나 위젯을 닫아 주세요.
- 앱의 **메인·계정 연결 화면은 계속 보호**됩니다. 그 화면 위에서는 캡처가 제한될 수 있으므로 홈 화면이나 일반 앱 화면에서 확인하세요.
- 다른 앱이나 기기의 캡처 제한을 해제하는 기능은 아닙니다.
- 이동·탭 새로고침·길게 눌러 닫기·잠금 시 숨김, 꾸미기·로그인·조회 주기·권한은 그대로입니다.

기존 앱을 삭제하지 않고 같은 서명의 `WeeklyMeter-0.6.3.apk`로 업데이트합니다. 설정과 로그인 정보를 지우는 변경은 없습니다.

**검증:** 0.6.3 Windows 전체 빌드·정렬·동일 서명, 로컬 실행 검사 15,026개 및 별도 아이콘 49개·XML 18개 검사를 통과했습니다. Android 15 에뮬레이터 99개 검사에서 실제 창의 보호 플래그 제거·메인 보호 유지와 기존 동작을 확인했습니다. 실제 OS 캡처·화면 녹화, S25 Ultra / One UI와 실계정 동작은 미검증입니다. [상세 검사 기록](TEST-RESULTS.txt) · [보안 범위](SECURITY.md)

## English

Removed **capture protection from the floating window only** to address screen-capture restrictions while the widget is visible. Previously this window also used Android's `FLAG_SECURE`. [Android reference](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)

- The floating widget's usage and dates **may appear in screenshots and screen recordings**. Check before sharing or close the widget first.
- The app's **main/account screen remains protected**. Capture can still be restricted over that screen; check from the home screen or an ordinary app instead.
- This does not bypass another app's or device's capture restrictions.
- Movement, tap-to-refresh, hold-to-close, lock-screen hiding, styling, authentication, intervals, and permissions are unchanged.

Install the same-signed `WeeklyMeter-0.6.3.apk` over the existing app without uninstalling it. This change does not erase settings or login data.

**Verification:** The 0.6.3 Windows build, alignment, same-certificate signing, 15,026 host checks, and separate 49 icon / 18 XML checks passed. Another 99 Android 15 emulator checks verified the actual floating window's capture flag removal, main-screen protection, and retained behavior. OS screenshots, screen recording, physical S25 Ultra / One UI, and real-account operation remain unverified. [Detailed results](TEST-RESULTS.txt) · [Security scope](SECURITY.md)
