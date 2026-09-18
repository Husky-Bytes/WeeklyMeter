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
        super.onCreate(state);createdLanguage=Texts.locale(this).getLanguage();setTitle(R.string.app_name);if(state!=null){openedAttempt=state.getLong("openedAttempt",0);autoOpenLogin=state.getBoolean("autoOpenLogin",false);}
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
    @Override protected void onResume(){
        super.onResume();AppLanguage.synchronize(this);
        if(!Texts.locale(this).getLanguage().equals(createdLanguage)){recreate();return;}
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
            button(t("자동 조회 간격 · ","Refresh interval · ")+Store.prefs(this).getInt("minutes",15)+t("분"," min"),false,()->new AlertDialog.Builder(this).setTitle(t("자동 조회 간격","Refresh interval")).setItems(new String[]{t("15분","15 minutes"),t("30분","30 minutes"),t("60분","60 minutes")},(d,which)->{Store.prefs(this).edit().putInt("minutes",new int[]{15,30,60}[which]).apply();Scheduler.ensure(this);render();}).show());
            button(t("배터리 설정","Battery settings"),false,()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));button(t("연결 해제","Disconnect"),false,this::disconnect);
        }else{
            if(!login.message.isEmpty())text(content,login.message,13,MUTED,false);
            button(t("ChatGPT로 로그인","Sign in with ChatGPT"),true,this::consent);button(t("저장된 연결 정보 초기화","Clear saved connection"),false,this::disconnect);
        }
        gap(content,20);button(t("공식 사용량 화면","Official usage page"),false,()->browser("https://chatgpt.com/codex/settings/usage"));
        button(t("앱 정보","About"),false,()->new AlertDialog.Builder(this).setTitle(getString(R.string.app_name)+" 0.5.1")
            .setMessage(t("Codex의 주간 잔여량을 표시하는 비공식 위젯입니다. 일반 ChatGPT 모델의 통합 한도는 아닙니다.","An unofficial widget for the Codex weekly quota, not a combined limit for ChatGPT models."))
            .setPositiveButton("GitHub",(d,w)->browser("https://github.com/Husky-Bytes/WeeklyMeter")).setNegativeButton(t("닫기","Close"),null).show());
        button(t("글꼴 라이선스 · 상표 안내","Font licenses · Trademarks"),false,this::notices);
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
    private void open(Intent i){try{startActivity(i);}catch(ActivityNotFoundException e){localStatus=t("열 수 있는 앱이 없습니다.","No app is available to open this.");render();}}
    private LinearLayout card(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(16),dp(20),dp(16));box.setBackground(round(CARD,24));content.addView(box,new LinearLayout.LayoutParams(-1,-2));return box;}
    private TextView text(LinearLayout parent,String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(Messages.localize(s,Texts.locale(this)));t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(3),0,dp(3));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private void button(String s,boolean primary,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?BG:TEXT);b.setBackground(round(primary?ACCENT:CARD,14));b.setMinHeight(dp(50));b.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);lp.bottomMargin=dp(6);content.addView(b,lp);b.setOnClickListener(v->action.run());}
    private void gap(LinearLayout parent,int n){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String t(String ko,String en){return Texts.t(this,ko,en);}
}
