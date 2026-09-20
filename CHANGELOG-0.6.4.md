# WeeklyMeter 0.6.4

## 한국어

화면을 껐다 켠 뒤 플로팅 위젯이 돌아오지 않을 수 있는 복귀 처리를 수정했습니다.

- 화면 켜짐·잠금 해제 신호와 실제 잠금 상태가 바뀌는 시점이 어긋나도, 최대 15초 동안 0.5초 간격으로 상태를 다시 확인합니다. 잠겨 있거나 화면이 꺼져 있으면 표시하지 않습니다.
- 창을 다시 붙이는 과정의 일시적인 오류도 같은 제한 시간 안에서 재시도합니다. 계속 실패하면 종료하고 앱에서 다시 열도록 안내합니다.
- 사용자가 켜 둔 플로팅 서비스가 프로세스 정리로 종료된 경우 Android에 복구를 요청합니다. 시스템이 서비스를 다시 만들면 잠금 상태를 확인한 뒤 표시합니다.
- 길게 누르기·앱의 닫기·알림의 닫기는 실행을 끝냅니다. 직접 닫은 위젯을 다시 띄우는 예약이나 부팅 자동 실행은 추가하지 않았습니다.
- 복귀 확인으로 계정 조회를 추가하거나 조회 주기를 다시 시작하지 않습니다. 위치·크기·홈/플로팅 스타일·로그인 정보는 그대로 유지합니다. 새 권한도 없습니다.

이전 코드의 한 번뿐인 복귀 확인과 프로세스 종료 후 복구하지 않는 동작을 보완한 것입니다. 사용자 S25 Ultra에서 어느 경로로 사라졌는지는 실기기 로그 없이 확정하지 않았습니다. 강제 중지·재부팅·제조사 절전 정책까지 자동 복구를 보장하지 않습니다. [Android 서비스 복구 설명](https://developer.android.com/reference/android/app/Service#START_STICKY)

업데이트할 때는 기존 앱을 삭제하지 않고 같은 서명의 `WeeklyMeter-0.6.4.apk`를 덮어 설치합니다. 설치 후 앱에서 플로팅 위젯을 한 번 띄워 새 실행을 시작하세요.

이번 변경은 플로팅 복귀 문제에 한정됩니다. 앞서 전체 검토에서 보고한 일반 메인 화면 캡처 정책·완전 투명 창의 터치·수동 조회 예외 처리·인증 복구 안내는 별도 수정 대상입니다.

검증: 전체 빌드·동일 서명·로컬 검사 15,154개를 통과했습니다. 새 복귀 검사 128개는 수정 전 실패하는 경로가 있는 것을 확인한 뒤 수정 후 모두 통과했습니다. 실제 S25 Ultra / One UI와 이번 버전의 에뮬레이터 실행은 미검증입니다. [상세 검사 기록](TEST-RESULTS.txt).

## English

Improved floating-widget recovery after turning the screen off and on.

- Wake and unlock transitions now recheck local screen/keyguard state every 0.5 seconds for up to 15 seconds. The overlay stays hidden while locked or asleep.
- Temporary window-attachment failures are retried within the same bounded window; persistent failures stop the session with a reopen message.
- An active foreground session asks Android to recreate it after process reclamation and checks screen privacy again before displaying anything.
- Hold-to-close, app close and notification close still end the session. There is no new boot auto-start, persistent enabled preference or alarm that resurrects a dismissed widget.
- Recovery adds no account query, refresh schedule reset or permission. Existing position, dimensions, styling and sign-in data are preserved.

These changes address code-level recovery gaps, not a confirmed diagnosis from physical S25 Ultra logs. System recreation is not guaranteed after force-stop, reboot or manufacturer restrictions. [Android service restart reference](https://developer.android.com/reference/android/app/Service#START_STICKY)

Install the same-signed `WeeklyMeter-0.6.4.apk` over the existing installation. Open the floating widget once after updating to start a new session. See [test results and limitations](TEST-RESULTS.txt).
