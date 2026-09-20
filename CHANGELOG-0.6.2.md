# WeeklyMeter 0.6.2

[한국어 안내](README.md) · [English guide](README.en.md) · [릴리스 / Release](https://github.com/Husky-Bytes/WeeklyMeter/releases/tag/v0.6.2)

## 한국어

- **선택한 크기 그대로 미리보기**: 선택한 dp 크기를 폰 배율에 맞춰 표시합니다. 미리보기 영역에 맞춰 위젯 전체를 축소하지 않습니다.
- **미리보기는 고정, 큰 내용은 스크롤**: 위쪽 미리보기 영역은 유지하고 화면·키보드 상황에 맞춰 영역 높이를 제한합니다. 넘치는 내용은 그 안에서 가로·세로로 확인합니다.
- **홈 크기와 예상 크기 구분**: 런처가 제공한 홈 위젯 크기를 선택하며, 없으면 예상 1×1 / 2×1로 표시합니다. 런처의 칸·바깥 여백까지 재현하는 것은 아닙니다.
- **플로팅은 선택한 크기 표시**: 실제 창은 작은 화면에서 표시 가능한 영역에 맞춰 제한될 수 있으므로 미리보기의 선택 크기와 구분합니다.
- **홈·플로팅 꾸미기를 같은 단계로**: 메인의 **홈 위젯 꾸미기 / 플로팅 위젯 꾸미기**에서 각각 한 번에 엽니다. 별도의 **플로팅 위젯 열기·닫기**는 표시·종료만 담당합니다.

기존 스타일·크기·불투명도·간격 설정을 유지합니다. 미리보기에는 예시 사용량만 표시하며 꾸미기를 열거나 미리보기를 조절해도 조회하지 않습니다. 인증·토큰·권한·자동 조회 로직은 변경하지 않았습니다.

### 설치·검증

같은 서명의 `WeeklyMeter-0.6.2.apk`를 기존 앱 위에 설치합니다. 패키지 `dev.yerin.weeklymeter`, versionCode **12**. 앱을 먼저 삭제하지 마세요.

이번 APK의 Windows Android 빌드·정렬·서명과 **로컬 실행 검사 15,020개**, 별도 아이콘 정적 검사 49개 및 XML 18개·리소스 검사를 통과했습니다. 기존 검사 전체 재실행과 미리보기 검사 1,914개를 포함합니다. [검사 기록](TEST-RESULTS.txt)

별도 **Android 15(API 35) 에뮬레이터 실행 검사 91개**도 통과했습니다. 메인 버튼 진입·선택 크기의 실제 View 측정·고정 위치·작은 앱 창에서 축소 없는 이동과 기존 불투명도·간격·플로팅 회귀를 확인했습니다. 가상 데이터·네트워크 차단 환경이며 앱 View를 직접 그린 PNG 3장을 시각 검토했습니다. 실제 OS 화면·키보드·손가락 입력·설치된 홈 위젯 실측 검사는 아닙니다.

실기기 S25 Ultra / One UI, 실계정 로그인·조회, 실제 절전 중 자동 조회는 미검증입니다. 검사 통과는 계정 연결의 안전이나 정확한 갱신 시각을 보장하지 않습니다. [보안 범위](SECURITY.md)

## English

- **Preview at the selected size**: selected dp dimensions map to the phone's density. The whole widget is no longer shrunk to fit a small preview box.
- **Pinned viewport, scrollable content**: the preview stays above settings. Its viewport height adapts to available screen/keyboard space; oversized content scrolls horizontally or vertically inside it.
- **Reported home sizes versus estimates**: choose dimensions reported by the launcher; if unavailable, the choices are labeled estimated 1×1 / 2×1. Launcher cells and outer margins are not reproduced.
- **Selected floating dimensions**: the actual floating window can be constrained by available screen space, so its preview is labeled as the selected size rather than a guarantee of the final window bounds.
- **One-tap home and floating customization**: **Customize home widget / Customize floating widget** are peers on the main screen. **Show / hide floating widget** remains a separate display control.

Existing styles, dimensions, opacity, and interval settings are retained. Previews use synthetic usage; opening customization or adjusting a preview does not query usage. Authentication, tokens, permissions, and automatic-refresh logic are unchanged.

### Installation and verification

Install the same-signature `WeeklyMeter-0.6.2.apk` over the existing app. Package `dev.yerin.weeklymeter`, versionCode **12**. Do not uninstall first.

This APK passed its Windows Android build, alignment/signature verification, **15,020 host executable checks**, 49 separate static icon checks, and checks across 18 XML files/resources. All previous suites were rerun, plus 1,914 new preview checks. [Test record](TEST-RESULTS.txt)

A separate **91 Android 15 (API 35) emulator runtime checks** passed: main-button navigation, measured selected-size Views, pinned position, panning without shrinking in a small app window, and existing opacity/interval/floating regressions. Synthetic data and disabled networking were used. Three PNGs drawn directly from app Views were visually reviewed. This is not OS-screen, actual keyboard/finger-input, or installed home-widget measurement testing.

Physical S25 Ultra / One UI, live-account login/usage, and real power-saving automatic refresh remain untested. Passing checks does not establish account safety or exact refresh timing. [Security scope](SECURITY.md)

## APK SHA-256

```text
fcfa6cab1ac7384b24158873044571f4e423b6e1f390e5cb0eed8850cdfa6bb6
```

해시 일치는 파일 동일성 확인이며 안전 보증이 아닙니다. / A matching hash verifies file identity, not app safety.
