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
    private boolean busy,autoOpenLogin;
    private long openedAttempt;
    private String localStatus="";
    private BrowserLoginService.Status seenStatus;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private interface Action {void run()throws Exception;}
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
        super.onCreate(state);if(state!=null){openedAttempt=state.getLong("openedAttempt",0);autoOpenLogin=state.getBoolean("autoOpenLogin",false);}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(24),dp(24),dp(32));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(scroll);render();
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putLong("openedAttempt",openedAttempt);out.putBoolean("autoOpenLogin",autoOpenLogin);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();ui.post(loginUpdates);if(!busy)run(()->new Repo(this).reconcileConnection());}
    @Override protected void onPause(){ui.removeCallbacks(loginUpdates);super.onPause();}
    private void run(Action action){
        if(busy)return;busy=true;localStatus="";render();
        Repo.IO.execute(()->{String err="";try{action.run();}catch(Exception e){err=Repo.friendly(e);}final String message=err;
            ui.post(()->{if(isDestroyed())return;busy=false;localStatus=message;Scheduler.ensure(this);WeeklyWidget.renderAll(this);render();});});
    }
    private void render(){
        if(content==null)return;content.removeAllViews();text(content,"Weekly Meter",28,TEXT,true);gap(content,20);
        Usage u=Store.selected(this);boolean valid=u!=null&&!u.expired(System.currentTimeMillis());
        LinearLayout hero=card();text(hero,valid?u.percent():"—%",56,TEXT,true);text(hero,Display.reset(u),14,MUTED,false);text(hero,Display.last(u),12,MUTED,false);gap(content,12);
        button("위젯 꾸미기",true,()->startActivity(new Intent(this,WidgetStyleSettingsActivity.class)));
        button("홈 화면에 위젯 추가",false,this::pin);text(content,"1×1부터 크기 조절 가능 · 위젯을 누르면 새로고침",12,MUTED,false);gap(content,20);
        if(busy)text(content,"확인 중…",13,ACCENT,false);
        if(!localStatus.isEmpty())text(content,localStatus,13,0xffffd99b,false);
        if(!Store.error(this).isEmpty()&&!Store.error(this).equals(localStatus))text(content,Store.error(this),13,0xffffd99b,false);
        BrowserLoginService.Status login=BrowserLoginService.status();
        if(login.active()){
            text(content,login.message,14,MUTED,false);
            if(login.phase.equals("waiting"))button("로그인 페이지 다시 열기",false,()->{openedAttempt=login.attempt;browser(login.url);});
            if(!login.phase.equals("finishing"))button("로그인 취소",false,()->BrowserLoginService.cancel(this));
        }else if(Store.connected(this)){
            text(content,"ChatGPT 연결됨",16,ACCENT,true);button("지금 새로고침",false,()->run(()->new Repo(this).sync()));
            if(!Store.meters(this).isEmpty())button("표시할 주간 한도",false,this::chooseBucket);
            gap(content,12);Switch auto=new Switch(this);auto.setText("자동 새로고침");auto.setTextColor(TEXT);auto.setChecked(Store.prefs(this).getBoolean("auto",true));auto.setPadding(0,dp(12),0,dp(12));
            auto.setOnCheckedChangeListener((b,value)->{Store.prefs(this).edit().putBoolean("auto",value).apply();Scheduler.ensure(this);});content.addView(auto,new LinearLayout.LayoutParams(-1,-2));
            button("자동 조회 간격 · "+Store.prefs(this).getInt("minutes",15)+"분",false,()->new AlertDialog.Builder(this).setTitle("자동 조회 간격").setItems(new String[]{"15분","30분","60분"},(d,which)->{Store.prefs(this).edit().putInt("minutes",new int[]{15,30,60}[which]).apply();Scheduler.ensure(this);render();}).show());
            text(content,"조회할 때 로그인도 자동 갱신해. 절전 중에는 조회가 늦어질 수 있어.",12,MUTED,false);
            button("배터리 설정",false,()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));button("연결 해제",false,this::disconnect);
        }else{
            if(!login.message.isEmpty())text(content,login.message,13,MUTED,false);
            button("ChatGPT로 로그인",true,this::consent);text(content,"폰 브라우저에서 한 번 로그인하면 인증정보를 저장하고 자동 갱신해.",13,MUTED,false);button("저장된 연결 정보 초기화",false,this::disconnect);
        }
        gap(content,20);button("공식 사용량 화면",false,()->browser("https://chatgpt.com/codex/settings/usage"));
        button("앱 정보 · 로그인 안내",false,()->new AlertDialog.Builder(this).setTitle("Weekly Meter 0.4.0")
            .setMessage("개인용 비공식 앱이야. Codex의 7일 한도를 표시하며 일반 ChatGPT의 모든 모델 한도를 합친 값은 아니야.\n\n폰에서 OpenAI로 직접 연결하고, 토큰은 Android Keystore로 암호화해 저장해. 비밀번호는 외부 브라우저에서만 입력해. PC·중계 서버·광고·분석 SDK는 없어.\n\n로그인은 자동 갱신하지만 서버 만료·권한 철회·앱 데이터 삭제 후에는 다시 로그인해야 할 수 있어. 사용량 읽기 전용으로 제한된 토큰은 아니야.\n\n비공식 Android 브라우저 로그인과 S25 Ultra 실기기 동작은 아직 미검증이야. 첫 연결 후 공식 화면과 비교해 줘.").setPositiveButton("확인",null).show());
        button("글꼴 라이선스 · 상표 안내",false,this::notices);
    }
    private void notices(){
        String[] names={"상표·출처 안내","나눔고딕","주아","나눔명조","나눔고딕코딩"};
        String[] files={"NOTICES.txt","fonts/gothic-OFL.txt","fonts/rounded-OFL.txt","fonts/serif-OFL.txt","fonts/mono-OFL.txt"};
        new AlertDialog.Builder(this).setTitle("글꼴 라이선스 · 상표 안내").setItems(names,(d,which)->{
            try(java.io.InputStream in=getAssets().open(files[which]);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                TextView copy=new TextView(this);copy.setText(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));copy.setTextSize(13);copy.setPadding(dp(20),dp(12),dp(20),dp(12));copy.setTextIsSelectable(true);
                ScrollView scroll=new ScrollView(this);scroll.addView(copy);new AlertDialog.Builder(this).setTitle(names[which]).setView(scroll).setPositiveButton("닫기",null).show();
            }catch(java.io.IOException e){localStatus="안내 파일을 열지 못했어.";render();}
        }).show();
    }
    private void consent(){new AlertDialog.Builder(this).setTitle("ChatGPT로 로그인")
        .setMessage("외부 브라우저의 OpenAI 로그인 페이지를 열게. 이 앱은 비공식 개인용 앱이며, 승인 화면에 Codex가 표시될 수 있어. 로그인 권한은 사용량 읽기 전용으로 제한되지 않아.\n\n비밀번호나 로그인 토큰을 이 앱이나 채팅에 입력할 필요는 없어. 로그인 후 이 앱으로 돌아와 줘.")
        .setNegativeButton("취소",null).setPositiveButton("브라우저 열기",(d,w)->{autoOpenLogin=true;localStatus="";
            try{startForegroundService(new Intent(this,BrowserLoginService.class));}catch(RuntimeException e){autoOpenLogin=false;localStatus="로그인을 시작하지 못했어. 앱을 연 상태에서 다시 시도해 줘.";render();}}).show();}
    private void disconnect(){new AlertDialog.Builder(this).setTitle("연결 정보를 삭제할까?").setMessage("이 폰의 로그인·사용량을 삭제해. 위젯 스타일은 유지해. 서버의 로그인 권한 철회와는 별개야.")
        .setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->run(()->new Repo(this).disconnect())).show();}
    private void pin(){AppWidgetManager m=getSystemService(AppWidgetManager.class);if(m.isRequestPinAppWidgetSupported())m.requestPinAppWidget(new ComponentName(this,WeeklyWidget.class),null,null);
        else new AlertDialog.Builder(this).setMessage("홈 화면 빈 곳 길게 누르기 → 위젯 → 주간 잔여량").setPositiveButton("확인",null).show();}
    private void chooseBucket(){List<Usage> list=Store.meters(this);String[] labels=new String[list.size()];for(int i=0;i<labels.length;i++)labels[i]=list.get(i).label+" · "+list.get(i).percent();
        new AlertDialog.Builder(this).setTitle("공식 화면과 같은 주간 한도 선택").setItems(labels,(d,which)->{Store.prefs(this).edit().putString("selected",list.get(which).id).apply();WeeklyWidget.renderAll(this);render();}).show();}
    private void browser(String address){Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(address));i.addCategory(Intent.CATEGORY_BROWSABLE);open(i);}
    private void open(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){localStatus="열 수 있는 앱을 찾지 못했어.";render();}}
    private LinearLayout card(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(16),dp(20),dp(16));box.setBackground(round(CARD,24));content.addView(box,new LinearLayout.LayoutParams(-1,-2));return box;}
    private TextView text(LinearLayout parent,String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(3),0,dp(3));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private void button(String s,boolean primary,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?BG:TEXT);b.setBackground(round(primary?ACCENT:CARD,14));b.setMinHeight(dp(50));b.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);lp.bottomMargin=dp(6);content.addView(b,lp);b.setOnClickListener(v->action.run());}
    private void gap(LinearLayout parent,int n){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
