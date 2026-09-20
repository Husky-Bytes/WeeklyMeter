# WeeklyMeter 0.6.6

## Smoother widgets, reliable refresh

- **Lighter customization:** create controls when opened, batch preview edits per frame, and skip unchanged saves.
- **Less redundant rendering:** share same-size home-widget images during publication; moving the floating widget no longer redraws its content.
- **Fresh floating data:** keep updates that arrive during drawing and recheck cached readings after expiry or window recovery.
- **Cleaner refresh feedback:** consolidate expiry callbacks and clear stale indicators after partial publication failures.
- **Live main-screen labels:** reflect saved usage and timestamps without a half-second polling loop or rebuilding every control.
- **One login-store read per refresh:** retain token-save, cancellation and reauthentication safeguards without keeping a plaintext session between tasks.

Includes the 0.6.4-0.6.5 wake recovery, transparent-window and authentication fixes. No new permissions or extra usage polling; existing fonts and styles remain available.

Install over the existing app **without uninstalling**. Same signing certificate, version code 16. Build and host checks passed; real-account, S25 Ultra / One UI runtime and measured battery improvements remain unverified. [Full test record](TEST-RESULTS.txt).

## 한국어

전체 검토에서 제안한 표시·편집기 최적화를 적용했습니다. 0.6.4·0.6.5의 복귀 처리와 보안 수정도 포함합니다.

- 꾸미기 항목과 컬러휠은 열 때 준비합니다. 연속 입력은 프레임마다 모아서 미리보기에 반영하고, 숨겨진 항목의 불필요한 갱신을 줄였습니다.
- 바꾸지 않은 설정은 다시 저장하거나 위젯에 게시하지 않습니다. 마지막 입력의 저장, 고정된 실제 크기 미리보기, 독립적인 홈·플로팅 설정은 유지합니다.
- 같은 크기의 홈 위젯은 한 번의 게시에서 이미지를 공유합니다. 위젯별 클릭 동작은 유지합니다.
- 홈·플로팅 조회 표시 만료를 통합했습니다. 일부 게시 실패나 그리는 도중 만료된 경우에도 표시가 남지 않도록 재검사합니다.
- 플로팅을 이동하거나 자동 조회 설정을 저장할 때 불필요하게 이미지를 다시 만들지 않습니다. 크기·내용·초기화 시각·유효기간이 바뀌면 갱신합니다.
- 메인 화면의 0.5초 반복 확인을 제거했습니다. 사용량·조회 시각·연결 상태가 바뀌면 화면에 반영하며, 값만 바뀔 때는 버튼을 다시 만들지 않습니다.
- 한 번의 조회 작업에서 암호화된 로그인 정보를 중복해서 읽지 않습니다. 토큰 저장·로그아웃·재로그인 보호와 직렬 처리는 유지합니다.
- Android 실행 검사 도구가 고정된 옛 버전 대신 빌드한 APK의 버전을 검사하도록 수정했습니다.

새 권한·외부 서버·추가 네트워크 조회는 없습니다. 기존 글꼴과 기능을 유지하며 실제 폰의 배터리·속도 개선율은 측정하지 않았습니다.

같은 서명의 APK를 기존 앱 위에 설치하면 설정과 연결 정보를 유지하는 업데이트 경로입니다. 앱을 삭제할 필요는 없습니다. 실계정 로그인·S25 Ultra/One UI·절전 상태의 실제 동작은 미검증입니다. 호스트 검사와 APK 빌드 결과는 [검사 기록](TEST-RESULTS.txt)에 구분합니다.
