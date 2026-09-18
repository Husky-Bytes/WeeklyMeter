# WeeklyMeter 0.5.1

[한국어 안내](README.md) · [English guide](README.en.md) · [릴리스 / Release](https://github.com/Husky-Bytes/WeeklyMeter/releases/tag/v0.5.1)

## 한국어

자동 조회 후 위젯 표시가 갱신되지 않아 직접 눌러야 바뀔 수 있는 경로를 수정했습니다.

- **백그라운드 연결 복원**: 저장된 로그인 정보가 유효해도 연결 상태 캐시가 꺼져 있으면 자동 작업이 조회를 건너뛸 수 있었습니다. 이제 작업 스레드에서 암호화 저장소의 연결 상태를 먼저 확인합니다.
- **저장 직후 위젯 갱신**: 조회값을 저장한 뒤 화면 스레드의 완료 처리를 기다리는 사이 작업이 중지되면 위젯 갱신이 빠질 수 있었습니다. 이제 작업 스레드에서 전체 위젯 갱신을 게시한 뒤 작업 완료를 처리합니다. 이미 저장된 값의 표시 갱신은 작업 취소 여부와 별개로 시도합니다.
- **여러 위젯·동시 갱신 보호**: 한 위젯의 갱신 실패가 다른 위젯까지 막지 않도록 분리하고, 겹친 렌더가 오래된 화면으로 새 표시를 덮지 않도록 순서를 보호합니다.
- **기존 동작 유지**: 조회 간격은 15 / 30 / 60분입니다. 정확한 알람이나 새 권한, 추가 HTTP 조회를 넣지 않았습니다. 앱을 열거나 언어·꾸미기만 바꿀 때 조회하지 않는 동작, 탭 새로고침과 기존 설정도 유지합니다.

위 두 가지 누락 경로는 Android 동작을 대체한 결정적 재현 테스트에서 확인했습니다. **사용자의 Galaxy S25 Ultra에서 원인을 직접 재현하거나 수정 효과를 확인한 것은 아닙니다.** Android가 자동 작업 자체를 절전·네트워크 제한으로 미루는 문제와 앱 내부에서 갱신을 빠뜨리는 문제는 별개입니다. 정확한 간격, 강제 종료 후 동작, 런처의 즉시 화면 반영은 보장하지 않습니다.

### 설치·확인

기존 앱을 지우지 말고 같은 서명의 `WeeklyMeter-0.5.1.apk`를 업데이트 설치합니다. 패키지는 `dev.yerin.weeklymeter`, versionCode는 **8**입니다. 이전 릴리스는 삭제하거나 덮어쓰지 않습니다.

위젯의 **마지막 성공 조회 시각**을 표시해 두고, 자동 새로고침을 켠 상태에서 앱이나 위젯을 누르지 않아도 다음 성공한 자동 조회가 반영되는지 확인할 수 있습니다. Android가 예약을 지연할 수 있으므로 설정된 분 간격만으로 성공·실패를 판단하지 마세요. 실패 시 비밀번호·토큰·인증 코드·원본 로그는 보내지 마세요.

Windows Android 빌드·APK 정렬·서명 검증과 **4,499개 실행 검사**, XML·리소스 검사를 확인한 배포입니다. 로컬 로직·파일 및 대체 Android 환경 검사이며 실기기·실계정 성공을 뜻하지 않습니다. [상세 검사 기록](TEST-RESULTS.txt)

**실계정 로그인·토큰 갱신, 실제 Android Keystore, APK 설치, S25 Ultra / One UI의 장시간 자동 조회·절전·재부팅·홈 화면 반영은 미검증입니다.** 비공식 앱이며 인증 권한은 사용량 읽기 전용이 아닙니다. 자동 토큰 갱신을 시도하지만 영구 로그인을 보장하지 않습니다. 기존 [보안 범위](SECURITY.md)는 그대로 적용됩니다.

## English

This update fixes paths where an automatic refresh could leave the widget unchanged until it was tapped.

- **Restore background connection state**: a scheduled job could skip usage even with valid saved credentials if the cached connection flag was false. The worker now reconciles the encrypted credential store before deciding whether to refresh.
- **Publish widgets after saving**: stopping a job after usage was saved but before its main-thread completion callback could drop the widget update. The worker now publishes the full widget update before completing the job. Publishing already-saved data is attempted independently of job cancellation.
- **Protect multiple widgets and overlapping updates**: one widget's publication failure is isolated so it does not prevent other widgets from updating. Rendering is ordered to prevent an older overlapping render from replacing newer content.
- **Keep existing behavior**: intervals remain 15 / 30 / 60 minutes. No exact alarms, extra permissions, or additional HTTP usage requests were added. Opening the app or changing language/style still does not request usage; tap-to-refresh and saved settings remain.

The two skipped-update paths were reproduced deterministically with Android test doubles. **The cause was not reproduced on the user's Galaxy S25 Ultra, and the fix has not been verified there.** Android delaying a scheduled job under battery/network restrictions is separate from the app dropping an update after a job runs. Exact intervals, behavior after force-stop, and immediate launcher repainting are not guaranteed.

### Update and check

Install the same-signature `WeeklyMeter-0.5.1.apk` over the existing app without uninstalling. Package: `dev.yerin.weeklymeter`; versionCode: **8**. Earlier releases are retained rather than overwritten or deleted.

Enable **last successful refresh time** on the widget and automatic refresh in the app. A successful scheduled refresh should then update that timestamp without opening the app or tapping the widget. Android may defer the schedule, so elapsed interval alone is not proof of success or failure. Never share passwords, tokens, authorization codes, or raw logs when reporting a problem.

The distribution is checked with the Windows Android build, APK alignment/signature verification, **4,499 executable checks**, and XML/resource checks. These are local logic/file checks and Android test doubles, not successful device or live-account tests. [Detailed results](TEST-RESULTS.txt)

**Real-account login/token refresh, Android Keystore on a real device, APK installation, and S25 Ultra / One UI long-running scheduled refresh, battery, reboot, and home-screen behavior remain unverified.** This is unofficial; authentication access is not restricted to read-only usage data. Token refresh is attempted, but permanent login is not guaranteed. The existing [security scope (Korean)](SECURITY.md) still applies.

## APK SHA-256

```text
f4b22b9caa3f1b0d31b1a8a778f32ede1073e0ed321503bbc34d8f45a9699f1c
```

해시는 파일 동일성을 확인하는 수단이며 안전 보증이 아닙니다. / A matching hash verifies file identity, not app safety.
