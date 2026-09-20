# WeeklyMeter 0.6.1

[한국어 안내](README.md) · [English guide](README.en.md) · [릴리스 / Release](https://github.com/Husky-Bytes/WeeklyMeter/releases/tag/v0.6.1)

## 한국어

- **글자까지 전체 투명도 조절**: 홈 또는 플로팅 꾸미기의 **배경·투명도 → 전체 불투명도**에서 배경·글자·로고·조회 효과를 함께 조절합니다. 100%는 원래 모습, 0%는 완전 투명입니다. 기존 **배경 불투명도**는 별도로 유지합니다.
- **홈·플로팅 독립 설정**: 한쪽의 전체 불투명도 변경·초기화는 다른 쪽에 영향을 주지 않습니다. 기존 저장값은 전체 100%로 읽어 업데이트만으로 모습이 바뀌지 않습니다.
- **자동 조회 간격 직접 입력**: 앱의 **자동 조회 간격**에서 **15~10080분(7일)** 사이 정수를 입력하거나 15 / 30 / 60분 버튼을 고릅니다. 잘못된 값은 저장하지 않고 입력창을 유지합니다.
- **하나의 자동 조회 주기**: 홈·플로팅에 같은 간격을 사용합니다. 저장은 예약만 조정하며 즉시 조회하지 않습니다. Android 주기 작업의 최소 간격은 15분이고 절전·네트워크 제한으로 실행이 늦어질 수 있습니다.

전체 불투명도 0%에서도 플로팅 창과 터치 영역은 남습니다. 보이지 않을 때는 앱의 **플로팅 위젯 → 닫기**로 닫거나 **꾸미기·크기**에서 불투명도를 다시 올릴 수 있습니다. 새 권한·인증 변경·알람·별도 폴링은 없습니다.

### 설치·검증

같은 서명의 `WeeklyMeter-0.6.1.apk`를 기존 앱 위에 설치합니다. 패키지 `dev.yerin.weeklymeter`, versionCode **11**. 앱을 먼저 삭제하지 마세요.

전체 Windows Android 빌드·APK 정렬·서명과 **13,106개 로컬 실행 검사**, 아이콘 정적 검사 49개 및 XML 18개·리소스 검사를 통과했습니다. APK 자산 검사 36개는 서명 전후 실행했지만 합계에는 한 번만 포함했습니다. [검사 기록](TEST-RESULTS.txt)

별도로 이번 APK의 **Android 15(API 35) 에뮬레이터 실행 검사 62개**를 통과했습니다. 실제 비트맵의 배경·글자·성공 표시에서 100/50/0% 알파, 열린 플로팅의 즉시 변경과 홈 설정 분리, 실제 간격 입력창의 저장·거부·취소·단축 버튼을 확인했습니다. 기존 플로팅 검사도 다시 통과했습니다. 가상 데이터·네트워크 차단 환경이며 로고 픽셀·불투명도 슬라이더 직접 조작·실제 47분 주기 실행·스크린샷 시각 검수는 아닙니다.

실제 S25 Ultra / One UI, 실계정 로그인·조회, 실제 절전 주기 실행은 미검증입니다. 로컬 검사 통과가 계정 연결의 안전이나 정확한 갱신 시각을 보장하지 않습니다. [보안 범위](SECURITY.md)

## English

- **Fade the entire widget, including text**: choose **Background & opacity → Overall opacity** in home or floating styling. Background, text, logo, and refresh feedback fade together. 100% keeps the original appearance; 0% is fully transparent. Existing **Background opacity** remains separate.
- **Independent home/floating settings**: changing or resetting one does not affect the other. Old styles default to 100% overall opacity, so upgrading alone does not change their appearance.
- **Custom automatic-refresh interval**: enter a whole number from **15 to 10080 minutes (7 days)** under **Refresh interval**, or choose 15 / 30 / 60. Invalid input is not saved and the dialog stays open.
- **One shared schedule**: home and floating widgets use the same interval. Saving adjusts scheduling without fetching usage. Android periodic jobs have a 15-minute minimum; battery/network restrictions can delay execution.

At 0% opacity, the floating window and its touch area remain active. Use **Floating widget → Hide** or restore opacity through **Style & size** in the app. No new permissions, authentication changes, alarms, or separate polling are added.

### Installation and verification

Install the same-signature `WeeklyMeter-0.6.1.apk` over the existing app. Package `dev.yerin.weeklymeter`, versionCode **11**. Do not uninstall first.

The full Windows Android build, APK alignment/signature verification, **13,106 local executable checks**, 49 static icon checks, and checks across 18 XML files/resources passed. The 36 APK asset checks ran before/after signing but are counted once. [Test record](TEST-RESULTS.txt)

Separately, **62 Android 15 (API 35) emulator runtime checks passed on this APK**: actual bitmap background/text/success-feedback alpha at 100/50/0%, live floating changes with home-style isolation, and the real interval dialog's save/reject/cancel/shortcut behavior. Existing floating checks passed again. Synthetic data and disabled networking were used; logo pixels, direct opacity-slider interaction, actual 47-minute execution, and screenshot visual QA were not tested.

Real S25 Ultra / One UI, live-account login/usage, and actual power-saving intervals remain unverified. Passing local checks does not guarantee account safety or exact refresh timing. [Security scope](SECURITY.md)

## APK SHA-256

```text
1be1cebbfad9406dc8c2b8b039c84575dcf6cd1f0abd6682ed2b9789fe223dcb
```

해시 일치는 파일 동일성 확인이며 안전 보증이 아닙니다. / A matching hash verifies file identity, not app safety.
