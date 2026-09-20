package dev.yerin.weeklymeter;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Passwords are entered only in the external browser; no WebView or cookie extraction. */
public final class MainActivity extends Activity {
    private static final int BG=0xff101216,CARD=0xff1d2026,TEXT=0xfff7f8fa,MUTED=0xffa6abb5,ACCENT=0xffb8efcf;
    private LinearLayout content;
    private boolean busy,autoOpenLogin,awaitingOverlayReturn;
    private long openedAttempt;
    private String localStatus="";
    private String createdLanguage;
    private BrowserLoginService.Status seenStatus;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private interface Action {void run()throws Exception;}
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(base));}
    private final Runnable loginUpdates=new Runnable(){public void run(){
        BrowserLoginService.Status s=BrowserLoginService.status();
        if(s!=seenStatus){
            seenStatus=s;render();
            if(s.phase.equals("waiting")&&autoOpenLogin&&openedAttempt!=s.attempt){openedAttempt=s.attempt;autoOpenLogin=false;browser(s.url);}
            if(s.phase.equals("done")&&!busy)MainActivity.this.run(()->new Repo(MainActivity.this).reconcileConnection());
        }
        ui.postDelayed(this,500);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);createdLanguage=Texts.locale(this).getLanguage();setTitle(R.string.app_name);if(state!=null){openedAttempt=state.getLong("openedAttempt",0);autoOpenLogin=state.getBoolean("autoOpenLogin",false);awaitingOverlayReturn=state.getBoolean("awaitingOverlayReturn",false);}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(24),dp(24),dp(32));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(scroll);render();
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putLong("openedAttempt",openedAttempt);out.putBoolean("autoOpenLogin",autoOpenLogin);out.putBoolean("awaitingOverlayReturn",awaitingOverlayReturn);super.onSaveInstanceState(out);}
    @Override protected void onResume(){
        super.onResume();AppLanguage.synchronize(this);
        if(!Texts.locale(this).getLanguage().equals(createdLanguage)){recreate();return;}
        if(awaitingOverlayReturn){awaitingOverlayReturn=false;if(overlayAllowed())showFloating();else Toast.makeText(this,t("다른 앱 위에 표시 권한이 필요합니다.","Allow display over other apps to show the floating widget."),Toast.LENGTH_LONG).show();}
        ui.post(loginUpdates);if(!busy)run(()->new Repo(this).reconcileConnection());
    }
    @Override protected void onPause(){ui.removeCallbacks(loginUpdates);super.onPause();}
    private void run(Action action){
        if(busy)return;busy=true;localStatus="";render();
        Repo.IO.execute(()->{String err="";try{action.run();}catch(Exception e){err=Repo.friendly(e);}final String message=err;
            ui.post(()->{if(isDestroyed())return;busy=false;localStatus=message;Scheduler.ensure(this);WeeklyWidget.renderAll(this);render();});});
    }
    private void render(){
        if(content==null)return;content.removeAllViews();languageHeader();gap(content,20);
        Usage u=Store.selected(this);boolean valid=u!=null&&!u.expired(System.currentTimeMillis());
        LinearLayout hero=card();text(hero,valid?u.percent():"—%",56,TEXT,true);text(hero,Display.reset(this,u),14,MUTED,false);text(hero,Display.last(this,u),12,MUTED,false);gap(content,12);
        button(t("위젯 꾸미기","Customize widget"),true,()->startActivity(new Intent(this,WidgetStyleSettingsActivity.class)));
        button(t("플로팅 위젯","Floating widget"),false,this::floatingOptions);
        button(t("홈 화면에 위젯 추가","Add widget to home screen"),false,this::pin);gap(content,20);
        if(busy)text(content,t("확인 중…","Checking…"),13,ACCENT,false);
        if(!localStatus.isEmpty())text(content,localStatus,13,0xffffd99b,false);
        if(!Store.error(this).isEmpty()&&!Store.error(this).equals(localStatus))text(content,Store.error(this),13,0xffffd99b,false);
        BrowserLoginService.Status login=BrowserLoginService.status();
        if(login.active()){
            text(content,login.message,14,MUTED,false);
            if(login.phase.equals("waiting"))button(t("로그인 페이지 다시 열기","Reopen sign-in page"),false,()->{openedAttempt=login.attempt;browser(login.url);});
            if(!login.phase.equals("finishing"))button(t("로그인 취소","Cancel sign-in"),false,()->BrowserLoginService.cancel(this));
        }else if(Store.connected(this)){
            text(content,t("ChatGPT 연결됨","Connected to ChatGPT"),16,ACCENT,true);button(t("지금 새로고침","Refresh now"),false,()->run(()->new Repo(this).sync()));
            if(!Store.meters(this).isEmpty())button(t("표시할 주간 한도","Choose weekly limit"),false,this::chooseBucket);
            gap(content,12);Switch auto=new Switch(this);auto.setText(t("자동 새로고침","Automatic refresh"));auto.setTextColor(TEXT);auto.setChecked(Store.prefs(this).getBoolean("auto",true));auto.setPadding(0,dp(12),0,dp(12));
            auto.setOnCheckedChangeListener((b,value)->{Store.prefs(this).edit().putBoolean("auto",value).apply();Scheduler.ensure(this);});content.addView(auto,new LinearLayout.LayoutParams(-1,-2));
            button(t("자동 조회 간격 · ","Refresh interval · ")+Scheduler.minutes(this)+t("분"," min"),false,this::refreshInterval);
            button(t("연결 해제","Disconnect"),false,this::disconnect);
        }else{
            if(!login.message.isEmpty())text(content,login.message,13,MUTED,false);
            button(t("ChatGPT로 로그인","Sign in with ChatGPT"),true,this::consent);button(t("저장된 연결 정보 초기화","Clear saved connection"),false,this::disconnect);
        }
        button(t("자동 조회 상태 · 절전 설정","Auto refresh · Battery settings"),false,this::automaticStatus);
        gap(content,20);button(t("공식 사용량 화면","Official usage page"),false,()->browser("https://chatgpt.com/codex/settings/usage"));
        button(t("앱 정보","About"),false,()->new AlertDialog.Builder(this).setTitle(getString(R.string.app_name)+" 0.6.1")
            .setMessage(t("Codex의 주간 잔여량을 표시하는 비공식 위젯입니다. 일반 ChatGPT 모델의 통합 한도는 아닙니다.","An unofficial widget for the Codex weekly quota, not a combined limit for ChatGPT models."))
            .setPositiveButton("GitHub",(d,w)->browser("https://github.com/Husky-Bytes/WeeklyMeter")).setNegativeButton(t("닫기","Close"),null).show());
        button(t("글꼴 라이선스 · 상표 안내","Font licenses · Trademarks"),false,this::notices);
    }
    private void refreshInterval(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(8),dp(24),dp(8));
        text(box,t("15~10080분 · 절전 중에는 지연될 수 있습니다.","15–10080 minutes · May be delayed during power saving."),12,MUTED,false);
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(TEXT);input.setTextSize(20);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        input.setContentDescription(t("조회 간격(분)","Refresh interval in minutes"));input.setSelectAllOnFocus(true);
        input.setText(String.valueOf(Scheduler.minutes(this)));box.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout presets=new LinearLayout(this);box.addView(presets,new LinearLayout.LayoutParams(-1,-2));
        for(int minutes:new int[]{15,30,60}){
            Button preset=new Button(this);preset.setAllCaps(false);preset.setText(minutes+t("분"," min"));
            preset.setOnClickListener(v->{input.setText(String.valueOf(minutes));input.setSelection(input.length());input.setError(null);});
            presets.addView(preset,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(t("자동 조회 간격","Refresh interval")).setView(box)
            .setPositiveButton(t("저장","Save"),null).setNegativeButton(t("취소","Cancel"),null).create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
                int minutes=RefreshInterval.parse(input.getText().toString());
                if(minutes<0){input.setError(t("15~10080 사이의 정수를 입력해 주세요.","Enter a whole number from 15 to 10080."));return;}
                Store.prefs(this).edit().putInt("minutes",minutes).apply();Scheduler.ensure(this);render();dialog.dismiss();
            });
            input.setOnEditorActionListener((view,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();return true;}return false;});
        });
        dialog.show();
    }
    private void floatingOptions(){
        boolean shown=FloatingWidgetService.isActive();
        new AlertDialog.Builder(this).setTitle(t("플로팅 위젯","Floating widget"))
            .setMessage(t("홈 위젯을 빠르게 3번 누르면 표시됩니다.\n\n누르기 · 새로고침\n끌기 · 이동\n길게 누르기 · 닫기\n\n홈 위젯과 꾸미기·크기를 따로 설정합니다. 표시 중에는 알림이 유지됩니다.",
                "Tap the home widget 3 times quickly to show it.\n\nTap · Refresh\nDrag · Move\nLong press · Close\n\nStyle and size are separate from the home widget. A notification remains while enabled."))
            .setPositiveButton(shown?t("닫기","Hide"):t("띄우기","Show"),(d,w)->{if(shown){FloatingWidgetService.hide(this);render();}else requestFloating();})
            .setNeutralButton(t("꾸미기·크기","Style & size"),(d,w)->startActivity(new Intent(this,WidgetStyleSettingsActivity.class).putExtra(WidgetStyleSettingsActivity.EXTRA_FLOATING,true)))
            .setNegativeButton(t("취소","Cancel"),null).show();
    }
    private void requestFloating(){
        if(overlayAllowed()){showFloating();return;}
        new AlertDialog.Builder(this).setTitle(t("다른 앱 위에 표시","Display over other apps"))
            .setMessage(t("플로팅 위젯을 사용하려면 다음 화면에서 주간 잔여량을 허용해 주세요. 다른 앱의 내용은 읽지 않습니다.",
                "Allow WeeklyMeter on the next screen to use the floating widget. It does not read other apps' content."))
            .setPositiveButton(t("설정 열기","Open settings"),(d,w)->{
                awaitingOverlayReturn=true;
                try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
                catch(ActivityNotFoundException|SecurityException error){awaitingOverlayReturn=false;localStatus=t("휴대전화 설정에서 다른 앱 위에 표시를 허용해 주세요.","Allow display over other apps in your phone settings.");render();}
            }).setNegativeButton(t("취소","Cancel"),null).show();
    }
    private void showFloating(){
        try{FloatingWidgetService.show(this);}
        catch(RuntimeException error){localStatus=t("플로팅 위젯을 시작하지 못했습니다. 권한을 확인해 주세요.","Could not show the floating widget. Check its permission.");}
        render();
    }
    private boolean overlayAllowed(){try{return Settings.canDrawOverlays(this);}catch(RuntimeException unavailable){return false;}}
    private void automaticStatus(){
        // Read-only: checking system restrictions must not fetch usage or reset a job's interval.
        BackgroundAccess.Snapshot access=BackgroundAccess.read(this);
        AutoRefreshDiagnostics.Snapshot last=AutoRefreshDiagnostics.read(this);
        StringBuilder status=new StringBuilder();
        status.append(t("자동 조회: ","Automatic refresh: ")).append(Store.prefs(this).getBoolean("auto",true)?t("켜짐","On"):t("꺼짐","Off"));
        status.append("\n").append(t("절전모드: ","Power saving: ")).append(yesNo(access.powerSave));
        status.append("\n").append(t("배터리 최적화 예외: ","Battery optimization exception: ")).append(access.batteryExempt==1?t("허용됨","Allowed"):access.batteryExempt==0?t("미허용","Not allowed"):t("확인 불가","Unknown"));
        status.append("\n").append(t("백그라운드 실행 제한: ","Background restriction: ")).append(yesNo(access.backgroundRestricted));
        status.append("\n").append(t("조회 예약: ","Scheduled job: ")).append(access.scheduled==1?t("등록됨","Registered"):access.scheduled==0?t("없음","None"):t("확인 불가","Unknown"));
        status.append("\n").append(t("현재 예약 상태: ","Current job state: ")).append(pendingText(access.pendingHint));
        status.append("\n\n").append(t("마지막 자동 조회 시작: ","Last automatic start: ")).append(statusTime(last.startedAt));
        status.append("\n").append(t("결과: ","Result: ")).append(outcomeText(last.outcome));
        if(last.finishedAt>0)status.append("\n").append(t("종료: ","Finished: ")).append(statusTime(last.finishedAt));
        if(last.stopReason>=0)status.append("\n").append(t("중단 사유: ","Stop reason: ")).append(stopText(last.stopReason));
        status.append("\n").append(t("마지막 자동 조회 성공: ","Last automatic success: ")).append(statusTime(last.succeededAt));
        status.append("\n").append(t("이번 위젯 전달: ","Widget delivery for this attempt: ")).append(!last.publishAttempted?t("기록 없음","Not recorded"):last.publishSucceeded?t("전달됨","Sent"):t("실패","Failed"));
        status.append("\n\n").append(t("기록은 이 버전부터 남습니다. 위젯 전달은 홈 화면의 실제 표시 확인과 다릅니다.","Records start with this version. Delivery does not confirm that the home screen displayed the update."));
        if(access.backgroundRestricted==1||access.batteryExempt==0)
            status.append("\n\n").append(t("절전 중 조회가 멈추면 아래 절전 설정에서 이 앱의 제한을 해제하세요.","If refresh pauses during power saving, allow this app in Battery settings below."));
        TextView copy=new TextView(this);copy.setText(status);copy.setTextSize(14);copy.setPadding(dp(20),dp(12),dp(20),dp(12));copy.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);scroll.addView(copy);
        new AlertDialog.Builder(this).setTitle(t("자동 조회 상태","Automatic refresh status")).setView(scroll)
            .setPositiveButton(t("절전 설정","Battery settings"),(d,w)->batteryGuide())
            .setNeutralButton(t("다시 확인","Check again"),(d,w)->automaticStatus())
            .setNegativeButton(t("닫기","Close"),null).show();
    }
    private void batteryGuide(){
        new AlertDialog.Builder(this).setTitle(t("절전 중 조회 설정","Refresh during power saving"))
            .setMessage(t("이 앱만 배터리 제한 예외로 허용하세요.\n\n• 앱 정보 → 배터리 → 제한 없음\n• 최적화 예외 설정 → 전체 앱 → 주간 잔여량 → 최적화 안 함\n• 갤럭시의 초절전 상태 앱에서 제외\n\n기종에 따라 메뉴가 다를 수 있습니다. 배터리 소모가 늘 수 있으며, 강한 절전·데이터 제한에서는 조회가 늦어질 수 있습니다.",
                "Allow a battery exception for this app.\n\n• App info → Battery → Unrestricted\n• Optimization exceptions → All apps → WeeklyMeter → Don't optimize\n• On Galaxy, remove it from Deep sleeping apps\n\nMenu names may vary. Battery use may increase. Strong power-saving or data restrictions can still delay refresh."))
            .setPositiveButton(t("최적화 예외 설정","Optimization exceptions"),(d,w)->openBatteryExceptions())
            .setNeutralButton(t("앱 배터리 설정","App battery settings"),(d,w)->openAppSettings())
            .setNegativeButton(t("닫기","Close"),null).show();
    }
    private void openBatteryExceptions(){
        try{startActivity(BackgroundAccess.batterySettings(this));}
        catch(ActivityNotFoundException|SecurityException unavailable){openAppSettings();}
    }
    private void openAppSettings(){open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    private String yesNo(int value){return value<0?t("확인 불가","Unknown"):value==1?t("켜짐","On"):t("꺼짐","Off");}
    private String statusTime(long value){return value<=0?t("기록 없음","Not recorded"):new java.text.SimpleDateFormat("M.d HH:mm:ss",Texts.locale(this)).format(new Date(value));}
    private String pendingText(String reason){
        switch(reason){
            case "battery":return t("배터리 조건 대기","Waiting for battery conditions");
            case "network":return t("네트워크 대기","Waiting for a network");
            case "quota":return t("백그라운드 실행 한도 대기","Waiting for background allowance");
            case "device":return t("기기 상태로 대기","Waiting due to device state");
            case "idle":return t("기기 유휴 조건 대기","Waiting for the idle condition");
            case "optimization":return t("시스템 최적화로 대기","Waiting for system optimization");
            case "app_restricted":return t("앱 실행 제한","App is restricted");
            case "running":return t("실행 중","Running");
            case "latency":return t("예약 시간 대기","Waiting for the scheduled window");
            case "not_scheduled":return t("예약 없음","Not scheduled");
            case "unavailable":return t("확인 불가","Unavailable");
            default:return t("기타 · 사유 미제공","Other / no reason provided");
        }
    }
    private String outcomeText(String outcome){
        switch(outcome){
            case "unavailable":return t("기록 확인 불가","Records unavailable");
            case "running":return t("시작됨 · 완료 기록 없음","Started · no completion recorded");
            case "updated":return t("조회 성공","Refresh succeeded");
            case "skipped":return t("최근 요청으로 건너뜀","Skipped after a recent request");
            case "cancelled":return t("완료 전 취소","Cancelled before completion");
            case "error":return t("조회 실패","Refresh failed");
            case "signed_out":return t("저장된 로그인 없음","No saved sign-in");
            case "stopped":return t("시스템에서 중단","Stopped by the system");
            case "destroyed":return t("작업 종료","Service ended");
            case "executor_rejected":return t("조회 시작 실패","Could not start refresh");
            case "disabled":return t("자동 조회 꺼짐 또는 표시 중인 위젯 없음","Automatic refresh is off or no active widget exists");
            default:return t("기록 없음","Not recorded");
        }
    }
    private String stopText(int reason){
        switch(reason){
            case 1:return t("앱에서 예약 취소","Cancelled by the app");
            case 2:return t("다른 작업에 우선권 부여","Another job took priority");
            case 3:case 16:return t("실행 시간 제한","Time limit");
            case 4:return t("기기 상태 · 절전 등","Device state, including power saving");
            case 5:return t("배터리 부족","Battery low");
            case 6:return t("충전 조건 변경","Charging condition changed");
            case 7:return t("네트워크 조건 변경","Network condition changed");
            case 8:return t("유휴 조건 변경","Idle condition changed");
            case 9:return t("저장공간 부족","Storage low");
            case 10:return t("백그라운드 실행 한도","Background execution limit");
            case 11:return t("백그라운드 제한","Background restriction");
            case 12:return t("앱 대기 정책","App standby policy");
            case 13:return t("사용자가 종료","Stopped by the user");
            default:return t("기타 · ","Other · ")+reason;
        }
    }
    private void languageHeader(){
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=new TextView(this);name.setText(R.string.app_name);name.setTextSize(28);name.setTextColor(TEXT);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton language=new ImageButton(this);language.setImageResource(R.drawable.ic_language);language.setBackground(round(CARD,14));language.setPadding(dp(12),dp(12),dp(12),dp(12));
        language.setContentDescription("언어 / Language");language.setTooltipText("언어 / Language");language.setOnClickListener(v->chooseLanguage());
        header.addView(language,new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(header,new LinearLayout.LayoutParams(-1,-2));
    }
    private void chooseLanguage(){
        String[] choices={AppLanguage.SYSTEM,AppLanguage.KO,AppLanguage.EN};
        String selected=AppLanguage.choice(this);int checked=Arrays.asList(choices).indexOf(selected);
        String[] names={t("시스템 설정","System default"),"한국어","English"};
        new AlertDialog.Builder(this).setTitle("언어 / Language").setSingleChoiceItems(names,checked,(dialog,which)->{
            dialog.dismiss();if(choices[which].equals(selected))return;
            AppLanguage.apply(this,choices[which]);recreate();
        }).setNegativeButton(t("취소","Cancel"),null).show();
    }
    private void notices(){
        String[] names={t("상표·출처 안내","Trademarks and credits"),t("나눔고딕","Nanum Gothic"),t("주아","Jua"),t("나눔명조","Nanum Myeongjo"),t("나눔고딕코딩","Nanum Gothic Coding")};
        String[] files={t("NOTICES.txt","NOTICES-en.txt"),"fonts/gothic-OFL.txt","fonts/rounded-OFL.txt","fonts/serif-OFL.txt","fonts/mono-OFL.txt"};
        new AlertDialog.Builder(this).setTitle(t("글꼴 라이선스 · 상표 안내","Font licenses · Trademarks")).setItems(names,(d,which)->{
            try(java.io.InputStream in=getAssets().open(files[which]);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                TextView copy=new TextView(this);copy.setText(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));copy.setTextSize(13);copy.setPadding(dp(20),dp(12),dp(20),dp(12));copy.setTextIsSelectable(true);
                ScrollView scroll=new ScrollView(this);scroll.addView(copy);new AlertDialog.Builder(this).setTitle(names[which]).setView(scroll).setPositiveButton(t("닫기","Close"),null).show();
            }catch(java.io.IOException e){localStatus=t("안내 파일을 열 수 없습니다.","Could not open the notice file.");render();}
        }).show();
    }
    private void consent(){new AlertDialog.Builder(this).setTitle(t("ChatGPT로 로그인","Sign in with ChatGPT"))
        .setMessage(t("비공식 앱입니다. 로그인 권한은 사용량 조회 전용이 아닙니다.\n\nOpenAI 로그인 페이지가 외부 브라우저에서 열립니다. 비밀번호는 브라우저에서만 입력하고, 로그인 후 앱으로 돌아오세요.","This is an unofficial app. Access is not restricted to read-only usage data.\n\nOpenAI sign-in will open in your browser. Enter your password only there, then return to the app."))
        .setNegativeButton(t("취소","Cancel"),null).setPositiveButton(t("브라우저 열기","Open browser"),(d,w)->{autoOpenLogin=true;localStatus="";
            try{startForegroundService(new Intent(this,BrowserLoginService.class));}catch(RuntimeException e){autoOpenLogin=false;localStatus=t("로그인을 시작할 수 없습니다. 앱을 연 상태에서 다시 시도하세요.","Could not start sign-in. Keep the app open and try again.");render();}}).show();}
    private void disconnect(){new AlertDialog.Builder(this).setTitle(t("연결 정보 삭제","Clear saved connection"))
        .setMessage(t("이 폰의 로그인 정보와 사용량이 삭제됩니다. 위젯 설정은 유지됩니다. 서버의 접근 권한은 별도로 철회해야 합니다.","Delete sign-in credentials and usage on this phone. Widget settings are kept. Server access must be revoked separately."))
        .setNegativeButton(t("취소","Cancel"),null).setPositiveButton(t("삭제","Delete"),(d,w)->run(()->new Repo(this).disconnect())).show();}
    private void pin(){AppWidgetManager m=getSystemService(AppWidgetManager.class);if(m.isRequestPinAppWidgetSupported())m.requestPinAppWidget(new ComponentName(this,WeeklyWidget.class),null,null);
        else new AlertDialog.Builder(this).setMessage(t("홈 화면 빈 곳 길게 누르기 → 위젯 → 주간 잔여량","Long-press an empty area on your home screen → Widgets → WeeklyMeter")).setPositiveButton(t("확인","OK"),null).show();}
    private void chooseBucket(){List<Usage> list=Store.meters(this);String[] labels=new String[list.size()];for(int i=0;i<labels.length;i++)labels[i]=Messages.localize(list.get(i).label,Texts.locale(this))+" · "+list.get(i).percent();
        new AlertDialog.Builder(this).setTitle(t("주간 한도 선택","Choose weekly limit")).setItems(labels,(d,which)->{Store.prefs(this).edit().putString("selected",list.get(which).id).apply();WeeklyWidget.renderAll(this);render();}).show();}
    private void browser(String address){Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(address));i.addCategory(Intent.CATEGORY_BROWSABLE);open(i);}
    private void open(Intent i){try{startActivity(i);}catch(ActivityNotFoundException|SecurityException e){localStatus=t("열 수 있는 앱이 없습니다.","No app is available to open this.");render();}}
    private LinearLayout card(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(16),dp(20),dp(16));box.setBackground(round(CARD,24));content.addView(box,new LinearLayout.LayoutParams(-1,-2));return box;}
    private TextView text(LinearLayout parent,String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(Messages.localize(s,Texts.locale(this)));t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(3),0,dp(3));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private void button(String s,boolean primary,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?BG:TEXT);b.setBackground(round(primary?ACCENT:CARD,14));b.setMinHeight(dp(50));b.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);lp.bottomMargin=dp(6);content.addView(b,lp);b.setOnClickListener(v->action.run());}
    private void gap(LinearLayout parent,int n){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String t(String ko,String en){return Texts.t(this,ko,en);}
}
