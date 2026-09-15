# WeeklyMeter 0.5.0

[한국어 안내](README.md) · [English guide](README.en.md) · [릴리스 / Release](https://github.com/Husky-Bytes/WeeklyMeter/releases/tag/v0.5.0)

## 한국어

- **한국어·영어 지원**: 첫 실행의 기본값은 시스템 설정입니다. 폰의 첫 번째 시스템 언어가 한국어면 한국어, 그 외에는 영어를 사용합니다. 국가나 위치를 확인하지 않으며 추가 권한도 요청하지 않습니다.
- **지구본 언어 설정**: 앱 상단의 지구본에서 시스템 설정 / 한국어 / English를 선택합니다. 앱 화면, 위젯 날짜·요일·오전/오후, 조회 알림과 로그인 결과 안내에 적용합니다. 언어 변경은 저장된 값을 다시 표시할 뿐 사용량을 조회하지 않습니다.
- **언어별 앱 이름**: 한국어는 `주간 잔여량`, 영어는 `WeeklyMeter`입니다. Android 13 이상에서는 앱별 언어 설정과 연동합니다. 시스템 설정 모드도 한국어 보조 언어로 잘못 전환되지 않도록 실제 사용할 한 언어를 Android에 지정하므로, Android 설정에는 한국어 또는 영어로 보일 수 있습니다. 앱의 지구본 메뉴에서는 시스템 설정 선택이 유지됩니다. 홈 화면 이름은 Android·런처에 따라 시스템 언어를 따르거나 캐시될 수 있습니다. 수동 앱 언어에 따른 런처 이름 변경은 보장하지 않으며, 실제 One UI 동작은 미검증입니다.
- **적응형 앱 아이콘**: 전경·배경을 분리하고 Android 13 이상 테마 아이콘용 단색 리소스를 추가했습니다. 런처가 다른 앱과 같은 원형·둥근 사각형 등의 외곽을 적용합니다. 모든 폰에 같은 모서리 모양을 강제하는 방식은 아닙니다.
- **알아보기 쉬운 꾸미기**: 글자·날짜 / 배치·여백 / 배경 / 조회 효과의 4개 카드에 조절할 내용을 표시합니다. 꾸밀 요소는 별도 선택하며 미리보기는 고정됩니다. 불필요한 설명은 줄이고 한국어 문구를 간결한 명사형·존댓말로 정리했습니다.
- **기존 기능 유지**: 1×1·크기 조절, 위젯 탭 새로고침, 기존 날짜·글꼴·색·여백 설정과 계정 저장 형식은 유지합니다. 앱 진입·언어·꾸미기 변경은 네트워크 조회를 시작하지 않습니다.

### 설치와 확인

`WeeklyMeter-0.5.0.apk`를 기존 앱 위에 업데이트 설치합니다. 패키지는 `dev.yerin.weeklymeter`, versionCode는 **7**입니다. 같은 서명이 필요하며, 기존 앱을 삭제하면 연결 정보와 설정이 사라질 수 있습니다.

Windows Android 빌드·APK 정렬·서명 검증과 **4,136개 실행 검사**, 적응형 아이콘 구조·도형 검사 49개와 XML 18개·리소스 검사를 확인한 배포입니다. 로직·파일 및 대체 Android 환경 검사이며 실제 기기의 성공을 뜻하지 않습니다. 자세한 항목은 [TEST-RESULTS.txt](TEST-RESULTS.txt)를 확인해 주세요.

**실계정 로그인·토큰 갱신, 실제 Android Keystore, APK 설치, Galaxy S25 Ultra / One UI의 언어·아이콘·레이아웃·절전 동작은 미검증입니다.** 비공식 앱이고 토큰 권한은 사용량 읽기 전용으로 제한되지 않습니다. 로그인은 서버 만료·철회·앱 데이터 삭제 등으로 풀릴 수 있으며 영구 로그인을 보장하지 않습니다. [보안 범위](SECURITY.md)

## English

- **Korean and English**: the initial choice is System default. Korean is used only when the phone's primary system language is Korean; all other primary languages use English. This does not inspect your country or location and needs no additional permission.
- **Globe language selector**: choose System default / 한국어 / English from the globe at the top of the app. App screens, widget dates/weekdays/AM/PM, refresh notifications, and browser sign-in result messages are localized. Changing language only redraws cached data; it does not request usage.
- **Localized app name**: `주간 잔여량` in Korean and `WeeklyMeter` in English. Android 13+ integrates with per-app languages. Automatic mode pins the resolved single language in Android to prevent a secondary Korean language from being selected, so Android Settings may show Korean or English while the in-app selector still shows System default. Home-screen labels may follow system language or remain cached, depending on Android and the launcher. Manual app-language selection does not guarantee a launcher-name change; One UI behavior remains unverified.
- **Adaptive launcher icon**: separate foreground/background layers and an Android 13+ monochrome themed-icon resource let the launcher apply its own circle, rounded-square, or other mask. This does not force an identical corner shape on every phone.
- **Clearer customization**: four descriptive cards—Text & dates, Layout & spacing, Background, and Refresh feedback—show what can be changed before opening them. A separate element picker replaces the second tab row; the preview stays pinned. Redundant explanations were removed and Korean wording is concise and polite.
- **Existing behavior preserved**: 1×1/resizing, tap-to-refresh, saved date/font/color/padding preferences, and credential storage remain. Opening the app or changing language/style does not request usage.

### Update and verification

Install `WeeklyMeter-0.5.0.apk` over the existing app using the same signature. Package: `dev.yerin.weeklymeter`; versionCode: **7**. Uninstalling first can erase your connection and settings.

The distribution is checked with the Windows Android build, APK alignment/signature verification, **4,136 executable checks**, 49 static adaptive-icon structure/geometry checks, and checks across 18 XML files/resources. These are logic/file checks and Android test doubles, not successful device tests. See [TEST-RESULTS.txt](TEST-RESULTS.txt).

**Real-account login/token refresh, Android Keystore on a real device, APK installation, and Galaxy S25 Ultra / One UI language, icon, layout, and battery behavior remain unverified.** This is unofficial; tokens are not restricted to read-only usage access. Server expiry/revocation or cleared app data can require another sign-in. Permanent login is not guaranteed. [Security scope (Korean)](SECURITY.md)

## APK SHA-256

```text
4c5bf928989dfbc38ab45e7faddec7ba208a420fdc12086e35f93ace385ee193
```

해시는 파일 동일성을 확인하는 수단이며 안전 보증이 아닙니다. / A matching hash verifies file identity, not app safety.
