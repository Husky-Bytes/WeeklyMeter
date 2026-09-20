# WeeklyMeter 0.6.5

전체 검토에서 확인한 항목을 수정한 로컬 업데이트입니다. 0.6.4의 화면 복귀·실행 세션 복원도 포함합니다.

- 일반 메인 화면의 불필요한 캡처 제한을 제거했습니다. 앱은 비밀번호나 토큰을 화면에 표시하지 않습니다.
- 완전히 투명한 플로팅은 창과 터치 영역을 제거합니다. 실행 세션과 자동 조회는 유지하며 다시 보이게 설정하면 복원합니다.
- 위젯 게시 실패가 수동 조회나 로그인 서비스 정리를 중단시키지 않도록 분리했습니다. 언어·스타일 저장과 예약 복구도 보호합니다.
- 갱신 권한 만료·철회 시 **다시 로그인** 버튼을 제공합니다. 연결 장애만으로 토큰을 지우지 않으며 새 로그인이 저장되어야 기존 연결을 교체합니다.
- 앱 첫 화면에 플로팅의 **표시 중 / 숨김** 상태와 **닫기**를 배치했습니다. 별도의 알림 권한을 추가하지 않았습니다.
- 각 HTTP 요청의 전체 대기를 30초로 제한했습니다. 전송 후 인증 응답을 확인할 수 없으면 같은 토큰을 반복 사용하지 않고 재로그인을 안내합니다.

통신 제한은 요청 하나 기준이며 OS I/O를 강제 종료한다는 보장은 아닙니다. 멈춘 작업이 있으면 추가 통신을 막고 작업자 수를 제한합니다. 이미 완료된 토큰 응답은 보존합니다. 비공식 인증·사용량 경로라는 점과 기존 권한 범위는 달라지지 않습니다.

기존 앱을 지우지 않고 같은 서명의 `WeeklyMeter-0.6.5.apk`를 덮어 설치하세요. 설치 후 앱에서 플로팅을 다시 띄워 새 세션을 시작할 수 있습니다.

전체 Windows 빌드·정렬·동일 서명과 15,657개 호스트 검사를 통과했습니다. 이번 버전의 에뮬레이터·실계정·Galaxy S25 Ultra / One UI 검증은 하지 않았습니다. GitHub에는 아직 게시하지 않은 로컬 APK입니다. [실행한 검사와 한계](TEST-RESULTS.txt) · [보안 범위](SECURITY.md)

## English

This local update retains 0.6.4 wake/session recovery and addresses all six review findings: normal-screen capture, fully transparent overlay hit areas, publication/cleanup failure isolation, explicit reauthentication, direct main-screen floating status/close controls, and a 30-second whole-request deadline.

Credentials are not deleted for transient network errors. A successful browser sign-in replaces the old credentials only after durable storage. Uncertain token exchanges do not blindly repeat the old refresh grant. Platform I/O cancellation remains best-effort with bounded workers; the deadline applies to each HTTP request, not the entire multi-request sync.

Install the same-signed APK over the existing app. No notification permission or new network endpoint is added. No emulator, physical-device, or real-account validation was performed for this version. See [test results](TEST-RESULTS.txt) and [security scope](SECURITY.md).
