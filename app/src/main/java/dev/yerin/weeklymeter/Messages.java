package dev.yerin.weeklymeter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Localize canonical legacy status strings at display time, including cached errors.
 * No account data is transmitted for translation. Unknown server labels stay unchanged. */
final class Messages {
    static final Map<String,String> ENGLISH, KOREAN;
    static {
        Map<String,String> en=new LinkedHashMap<>(), ko=new LinkedHashMap<>();
        en.put("브라우저 로그인 연결을 열지 못했어. 다른 로그인 창을 닫고 다시 시도해 줘.","Could not open browser sign-in. Close other sign-in windows and try again."); ko.put("브라우저 로그인 연결을 열지 못했어. 다른 로그인 창을 닫고 다시 시도해 줘.","브라우저 로그인을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        en.put("브라우저 로그인을 다시 시작해 줘.","Please start browser sign-in again."); ko.put("브라우저 로그인을 다시 시작해 줘.","브라우저 로그인을 다시 시작해 주세요.");
        en.put("브라우저 로그인이 취소됐어.","Browser sign-in was cancelled."); ko.put("브라우저 로그인이 취소됐어.","로그인 취소");
        en.put("로그인 시간이 지났어. 다시 로그인해 줘.","Sign-in timed out. Please sign in again."); ko.put("로그인 시간이 지났어. 다시 로그인해 줘.","로그인 시간이 만료되었습니다. 다시 시도해 주세요.");
        en.put("브라우저 로그인 연결이 종료됐어. 다시 시도해 줘.","The browser sign-in connection closed. Please try again."); ko.put("브라우저 로그인 연결이 종료됐어. 다시 시도해 줘.","로그인 연결이 종료되었습니다. 다시 시도해 주세요.");
        en.put("로그인을 취소했어.","Sign-in cancelled."); ko.put("로그인을 취소했어.","로그인 취소");
        en.put("브라우저 로그인 준비 중…","Preparing browser sign-in…"); ko.put("브라우저 로그인 준비 중…","로그인 준비 중…");
        en.put("로그인 진행","Sign-in progress"); ko.put("로그인 진행","로그인");
        en.put("폰 브라우저에서 로그인을 완료해 줘.","Complete sign-in in your phone browser."); ko.put("폰 브라우저에서 로그인을 완료해 줘.","브라우저에서 로그인을 완료해 주세요.");
        en.put("로그인 작업을 시작할 수 없어. 앱을 연 상태에서 다시 눌러 줘.","Could not start sign-in. Keep the app open and try again."); ko.put("로그인 작업을 시작할 수 없어. 앱을 연 상태에서 다시 눌러 줘.","로그인을 시작할 수 없습니다. 앱에서 다시 시도해 주세요.");
        en.put("브라우저에서 로그인한 뒤 이 앱으로 돌아와 줘.","Sign in in your browser, then return to this app."); ko.put("브라우저에서 로그인한 뒤 이 앱으로 돌아와 줘.","로그인 후 앱으로 돌아와 주세요.");
        en.put("브라우저 로그인이 승인되지 않았어. 다시 시도할 수 있어.","Browser sign-in was not approved. You can try again."); ko.put("브라우저 로그인이 승인되지 않았어. 다시 시도할 수 있어.","로그인이 승인되지 않았습니다. 다시 시도해 주세요.");
        en.put("로그인 정보를 안전하게 저장하는 중…","Saving sign-in…"); ko.put("로그인 정보를 안전하게 저장하는 중…","로그인 정보 저장 중…");
        en.put("로그인 완료","Signed in"); ko.put("로그인 완료","로그인 완료");
        en.put("다른 앱이 로그인 연결을 사용 중이야. 잠시 후 다시 눌러 줘.","Another app is using the sign-in connection. Try again shortly."); ko.put("다른 앱이 로그인 연결을 사용 중이야. 잠시 후 다시 눌러 줘.","다른 로그인 작업이 진행 중입니다. 잠시 후 다시 시도해 주세요.");
        en.put("로그인 대기 시간이 끝났어. 다시 로그인 버튼을 눌러 줘.","Sign-in timed out. Tap the sign-in button again."); ko.put("로그인 대기 시간이 끝났어. 다시 로그인 버튼을 눌러 줘.","로그인 시간이 만료되었습니다. 다시 시도해 주세요.");
        en.put("로그인 연결을 열지 못했어. 앱에서 다시 시작해 줘.","Could not open the sign-in connection. Restart sign-in in the app."); ko.put("로그인 연결을 열지 못했어. 앱에서 다시 시작해 줘.","로그인 연결 실패. 다시 시도해 주세요.");
        en.put("Weekly Meter 로그인","WeeklyMeter sign-in"); ko.put("Weekly Meter 로그인","주간 잔여량 로그인");
        en.put("취소","Cancel"); ko.put("취소","취소");
        en.put("로그인 대기 시간이 끝났어. 다시 시작해 줘.","Sign-in timed out. Please start again."); ko.put("로그인 대기 시간이 끝났어. 다시 시작해 줘.","로그인 시간이 만료되었습니다. 다시 시도해 주세요.");
        en.put("로그인 작업이 종료됐어. 다시 시작해 줘.","Sign-in ended. Please start again."); ko.put("로그인 작업이 종료됐어. 다시 시작해 줘.","로그인이 종료되었습니다. 다시 시도해 주세요.");
        en.put("인증정보 형식 오류","Invalid authentication data format"); ko.put("인증정보 형식 오류","인증정보 오류");
        en.put("계정 정보 형식 오류","Invalid account data format"); ko.put("계정 정보 형식 오류","계정 정보 오류");
        en.put("JSON 대신 로그인·보안 확인 화면이 반환됐어. 공식 화면에서 상태를 확인해 줘.","Additional sign-in is required. Check the official website."); ko.put("JSON 대신 로그인·보안 확인 화면이 반환됐어. 공식 화면에서 상태를 확인해 줘.","추가 로그인이 필요합니다. 공식 사이트를 확인해 주세요.");
        en.put("서버 응답이 너무 커.","The server response is too large."); ko.put("서버 응답이 너무 커.","서버 응답 오류");
        en.put("허용되지 않은 통신 주소","Network address is not allowed"); ko.put("허용되지 않은 통신 주소","연결할 수 없는 주소입니다.");
        en.put("허용되지 않은 통신 경로","Network path is not allowed"); ko.put("허용되지 않은 통신 경로","연결할 수 없는 경로입니다.");
        en.put("JSON 응답 형식을 확인할 수 없어.","Unsupported response format."); ko.put("JSON 응답 형식을 확인할 수 없어.","응답 형식 오류");
        en.put("로그인 응답을 확인할 수 없어. 다시 시작해 줘.","Could not validate the sign-in response. Please start again."); ko.put("로그인 응답을 확인할 수 없어. 다시 시작해 줘.","로그인 응답 오류. 다시 시도해 주세요.");
        en.put("이미 연결된 계정이 있어.","An account is already connected."); ko.put("이미 연결된 계정이 있어.","이미 연결된 계정이 있습니다.");
        en.put("로그인 유지용 인증정보가 발급되지 않았어. 다시 로그인해 줘.","Could not keep you signed in. Please sign in again."); ko.put("로그인 유지용 인증정보가 발급되지 않았어. 다시 로그인해 줘.","로그인을 유지할 수 없습니다. 다시 로그인해 주세요.");
        en.put("서버가 로그인 재시도 대기를 요청했어. 잠시 후 다시 확인해 줘.","The server asked you to wait before signing in again. Try again shortly."); ko.put("서버가 로그인 재시도 대기를 요청했어. 잠시 후 다시 확인해 줘.","잠시 후 다시 로그인해 주세요.");
        en.put("서버에서 유효한 인증정보가 오지 않았어.","The server did not return valid authentication data."); ko.put("서버에서 유효한 인증정보가 오지 않았어.","인증정보를 확인할 수 없습니다.");
        en.put("서버에서 유효한 계정 정보가 오지 않았어.","The server did not return valid account data."); ko.put("서버에서 유효한 계정 정보가 오지 않았어.","계정 정보를 확인할 수 없습니다.");
        en.put("다시 로그인이 필요해. 연결을 지우고 로그인해 줘.","Sign-in is required again. Disconnect, then sign in."); ko.put("다시 로그인이 필요해. 연결을 지우고 로그인해 줘.","다시 로그인해 주세요.");
        en.put("서버가 재시도 대기를 요청했어. 잠시 후 다시 확인해 줘.","The server asked you to wait before retrying. Try again shortly."); ko.put("서버가 재시도 대기를 요청했어. 잠시 후 다시 확인해 줘.","잠시 후 다시 시도해 주세요.");
        en.put("먼저 로그인해 줘.","Please sign in first."); ko.put("먼저 로그인해 줘.","먼저 로그인해 주세요.");
        en.put("앱에서 먼저 로그인해 줘.","Please sign in in the app first."); ko.put("앱에서 먼저 로그인해 줘.","앱에서 먼저 로그인해 주세요.");
        en.put("서버의 주간 한도 값이 이미 만료됐어. 최근 정상 조회값을 유지했어.","The server's weekly limit has already expired. The last valid reading was kept."); ko.put("서버의 주간 한도 값이 이미 만료됐어. 최근 정상 조회값을 유지했어.","주간 한도 정보가 만료되어 이전 값을 유지합니다.");
        en.put("선택한 주간 한도의 최신 값을 찾지 못했어. 앱에서 표시할 한도를 확인해 줘.","No current value was found for the selected weekly limit. Check the selected limit in the app."); ko.put("선택한 주간 한도의 최신 값을 찾지 못했어. 앱에서 표시할 한도를 확인해 줘.","선택한 한도를 찾을 수 없습니다. 표시할 한도를 확인해 주세요.");
        en.put("인증이 만료됐어. 연결을 지우고 다시 로그인해 줘. (401)","Authentication expired. Disconnect and sign in again. (401)"); ko.put("인증이 만료됐어. 연결을 지우고 다시 로그인해 줘. (401)","로그인이 만료되었습니다. 다시 로그인해 주세요. (401)");
        en.put("접근이 거부됐어. 계정 권한 또는 서버 제한을 확인해 줘. (403)","Access denied. Check account permissions or server restrictions. (403)"); ko.put("접근이 거부됐어. 계정 권한 또는 서버 제한을 확인해 줘. (403)","접근 권한을 확인해 주세요. (403)");
        en.put("인증·사용량 경로를 찾지 못했어. 서버 변경 가능성이 있어. (404)","The authentication or usage endpoint was not found. The service may have changed. (404)"); ko.put("인증·사용량 경로를 찾지 못했어. 서버 변경 가능성이 있어. (404)","서비스 연결 경로를 찾을 수 없습니다. (404)");
        en.put("서버가 요청을 제한했어. 잠시 후 다시 확인해 줘. (429)","The server limited requests. Please try again later. (429)"); ko.put("서버가 요청을 제한했어. 잠시 후 다시 확인해 줘. (429)","요청이 많습니다. 잠시 후 다시 시도해 주세요. (429)");
        en.put("인터넷 연결이 불안정해. 최근 조회값을 유지했어.","The network connection is unavailable or unstable. The previous reading was kept."); ko.put("인터넷 연결이 불안정해. 최근 조회값을 유지했어.","연결 실패. 이전 조회값을 유지합니다.");
        en.put("보안 연결을 확인할 수 없어. 인증서 검증을 건너뛰지 않았어.","Could not verify the secure connection."); ko.put("보안 연결을 확인할 수 없어. 인증서 검증을 건너뛰지 않았어.","보안 연결을 확인할 수 없습니다.");
        en.put("저장된 인증정보를 복호화할 수 없어. 연결을 지운 뒤 다시 로그인해 줘.","Stored sign-in is unavailable. Please sign in again."); ko.put("저장된 인증정보를 복호화할 수 없어. 연결을 지운 뒤 다시 로그인해 줘.","저장된 로그인을 사용할 수 없습니다. 다시 로그인해 주세요.");
        en.put("응답을 확인할 수 없어.","Could not validate the response."); ko.put("응답을 확인할 수 없어.","응답을 확인할 수 없습니다.");
        en.put("조회하지 못했어. 연결 상태와 공식 사용량 화면을 확인해 줘.","Could not fetch usage. Check your connection and the official usage page."); ko.put("조회하지 못했어. 연결 상태와 공식 사용량 화면을 확인해 줘.","조회 실패. 연결 상태를 확인해 주세요.");
        en.put("자동 갱신 작업을 등록하지 못했어. 앱에서 새로고침해 줘.","Could not schedule automatic refresh. Refresh from the app."); ko.put("자동 갱신 작업을 등록하지 못했어. 앱에서 새로고침해 줘.","자동 새로고침을 설정할 수 없습니다.");
        en.put("Android가 위젯 조회 시작을 막았어. 배터리 제한을 확인한 뒤 위젯을 다시 눌러 줘.","Android blocked widget refresh. Check battery restrictions, then tap the widget again."); ko.put("Android가 위젯 조회 시작을 막았어. 배터리 제한을 확인한 뒤 위젯을 다시 눌러 줘.","새로고침을 시작할 수 없습니다. 배터리 설정을 확인해 주세요.");
        en.put("Android가 위젯 조회 시작을 막았어. 배터리 제한을 확인하고 다시 눌러 줘.","Android blocked widget refresh. Check battery restrictions and try again."); ko.put("Android가 위젯 조회 시작을 막았어. 배터리 제한을 확인하고 다시 눌러 줘.","새로고침을 시작할 수 없습니다. 배터리 설정을 확인해 주세요.");
        en.put("사용량 값 오류","Invalid usage value"); ko.put("사용량 값 오류","사용량 값 오류");
        en.put("저장된 초기화 시각 형식이 달라졌어.","The stored reset-time format has changed."); ko.put("저장된 초기화 시각 형식이 달라졌어.","저장된 초기화 시각 오류");
        en.put("사용량 응답이 객체가 아니야.","The usage response is not an object."); ko.put("사용량 응답이 객체가 아니야.","사용량 응답 오류");
        en.put("사용량 응답에 서로 다른 한도 형식이 함께 있어.","The usage response mixes incompatible limit formats."); ko.put("사용량 응답에 서로 다른 한도 형식이 함께 있어.","한도 응답 형식 오류");
        en.put("기본 한도","Default limit"); ko.put("기본 한도","기본 한도");
        en.put("주간 한도 항목의 식별자가 일치하지 않아.","The weekly limit identifiers do not match."); ko.put("주간 한도 항목의 식별자가 일치하지 않아.","주간 한도 정보 오류");
        en.put("주간 한도 항목이 중복돼. 임의로 선택하지 않았어.","Duplicate weekly limits were found. None was selected arbitrarily."); ko.put("주간 한도 항목이 중복돼. 임의로 선택하지 않았어.","주간 한도 정보 중복");
        en.put("응답에서 7일짜리 주간 한도를 찾지 못했어. 공식 사용량 화면을 확인해 줘.","No seven-day weekly limit was found. Check the official usage page."); ko.put("응답에서 7일짜리 주간 한도를 찾지 못했어. 공식 사용량 화면을 확인해 줘.","주간 한도를 찾을 수 없습니다. 공식 사용량 화면을 확인해 주세요.");
        en.put("초기화 시각 형식이 달라졌어.","The reset-time format has changed."); ko.put("초기화 시각 형식이 달라졌어.","초기화 시각 오류");
        en.put("초기화 시각의 단위를 확인할 수 없어.","Could not determine the reset-time units."); ko.put("초기화 시각의 단위를 확인할 수 없어.","초기화 시각 단위 오류");
        en.put("한 항목에 주간 한도가 두 개 있어. 임의로 선택하지 않았어.","Two weekly limits were found in one entry. None was selected arbitrarily."); ko.put("한 항목에 주간 한도가 두 개 있어. 임의로 선택하지 않았어.","주간 한도 정보 중복");
        en.put("사용량 응답 형식이 달라졌어. 공식 사용량 화면을 확인해 줘.","The usage response format has changed. Check the official usage page."); ko.put("사용량 응답 형식이 달라졌어. 공식 사용량 화면을 확인해 줘.","사용량 응답 형식이 변경되었습니다. 공식 화면을 확인해 주세요.");
        en.put("위젯 사용량을 조회하고 있어.","Refreshing widget usage…"); ko.put("위젯 사용량을 조회하고 있어.","새로고침 중…");
        en.put("조회 작업을 시작하지 못했어. 위젯을 다시 눌러 줘.","Could not start refresh. Tap the widget again."); ko.put("조회 작업을 시작하지 못했어. 위젯을 다시 눌러 줘.","새로고침을 시작할 수 없습니다. 다시 시도해 주세요.");
        en.put("조회 작업을 시작하지 못했어.","Could not start refresh."); ko.put("조회 작업을 시작하지 못했어.","새로고침 시작 실패");
        en.put("위젯 새로고침","Widget refresh"); ko.put("위젯 새로고침","위젯 새로고침");
        en.put("Weekly Meter 새로고침","WeeklyMeter refresh"); ko.put("Weekly Meter 새로고침","주간 잔여량 새로고침");
        en.put("조회를 완료하지 못했어. 위젯을 다시 눌러 줘.","Could not complete refresh. Tap the widget again."); ko.put("조회를 완료하지 못했어. 위젯을 다시 눌러 줘.","새로고침 실패. 다시 시도해 주세요.");
        en.put("위젯을 업데이트했어.","Widget updated."); ko.put("위젯을 업데이트했어.","새로고침 완료");
        en.put("최근 조회값을 유지했어.","Previous reading kept."); ko.put("최근 조회값을 유지했어.","이전 조회값 유지");
        en.put("조회 시간이 길어져 이번 요청을 중단했어. 연결 상태를 확인하고 위젯을 다시 눌러 줘.","This request took too long and was stopped. Check your connection, then tap the widget again."); ko.put("조회 시간이 길어져 이번 요청을 중단했어. 연결 상태를 확인하고 위젯을 다시 눌러 줘.","조회 시간이 초과되었습니다. 연결 상태를 확인해 주세요.");
        en.put("조회를 마무리하고 있어. 잠시 후 다시 눌러 줘.","Finishing the request. Try again shortly."); ko.put("조회를 마무리하고 있어. 잠시 후 다시 눌러 줘.","조회 종료 중…");
        en.put("조회 제한 시간이 지났어. 위젯을 다시 눌러 줘.","Refresh timed out. Tap the widget again."); ko.put("조회 제한 시간이 지났어. 위젯을 다시 눌러 줘.","조회 시간 초과. 다시 시도해 주세요.");
        en.put("Android가 조회 작업을 종료했어. 위젯을 다시 눌러 줘.","Android ended the refresh task. Tap the widget again."); ko.put("Android가 조회 작업을 종료했어. 위젯을 다시 눌러 줘.","조회가 종료되었습니다. 다시 시도해 주세요.");
        en.put("위젯 조회 작업이 종료됐어. 위젯을 다시 눌러 줘.","The widget refresh task ended. Tap the widget again."); ko.put("위젯 조회 작업이 종료됐어. 위젯을 다시 눌러 줘.","조회가 종료되었습니다. 다시 시도해 주세요.");
        en.put("저장된 인증정보를 읽을 수 없어. 연결을 지운 뒤 다시 로그인해 줘.","Stored credentials could not be read. Disconnect and sign in again."); ko.put("저장된 인증정보를 읽을 수 없어. 연결을 지운 뒤 다시 로그인해 줘.","저장된 로그인을 사용할 수 없습니다. 다시 로그인해 주세요.");
        ENGLISH=Collections.unmodifiableMap(en);KOREAN=Collections.unmodifiableMap(ko);
    }
    static String localize(String raw, Locale locale){
        if(raw==null)return "";
        boolean korean=locale!=null&&"ko".equals(locale.getLanguage());
        String value=(korean?KOREAN:ENGLISH).get(raw);if(value!=null)return value;
        if(raw.matches("서버 연결 오류 [(]HTTP [0-9]{3}[)]"))return korean?raw:raw.replace("서버 연결 오류", "Server connection error");
        if(raw.matches("서버 응답 HTTP [0-9]{3}"))return korean?raw:raw.replace("서버 응답", "Server response");
        return raw;
    }
    private Messages(){}
}
