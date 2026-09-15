package dev.yerin.weeklymeter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** The fixed preview is a sibling of the settings ScrollView. No account access. */
public final class WidgetStyleSettingsActivity extends Activity {
    private static final int BG=0xff101318,CARD=0xff1c222b,TEXT=0xfff3f5f8,MUTED=0xffaeb8c7,ACCENT=0xffb8efcf;
    private static final String[] ROW_NAMES={"퍼센트","초기화 날짜·시간","마지막 성공 날짜·시간","ChatGPT 브랜드"};
    private static final String[] ELEMENT_NAMES={"%","초기화","조회","브랜드"};
    private static final String[] CATEGORY_NAMES={"글자·날짜","배치·여백","배경","조회 효과"};
    private static final String[] FONT_NAMES={"기본","나눔고딕","주아","나눔명조","나눔고딕코딩"};
    private static final String[] ALIGN_NAMES={"왼쪽","가운데","오른쪽"};
    private static final String[] BRAND_NAMES={"없음","텍스트","로고","텍스트 + 로고"};
    private static final String[] SEPARATOR_NAMES={"점  2026.09.15","슬래시  2026/09/15","대시  2026-09-15","한글  2026년 9월 15일"};
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<Runnable> controlUpdates=new ArrayList<>();
    private WidgetStyle current;
    private LinearLayout settings;
    private LinearLayout rootLayout,elementNavigation,navigation;
    private LinearLayout previewTitle;
    private FrameLayout previewStage;
    private final LinearLayout[] pages=new LinearLayout[4],textPanels=new LinearLayout[4],layoutPanels=new LinearLayout[4];
    private final Button[] categoryButtons=new Button[4],elementButtons=new Button[4];
    private final int[][] scrollPositions=new int[4][4];
    private int selectedCategory,selectedElement;
    private ScrollView scroll;
    private ImageView previewImage;
    private TextView previewWarning,previewCaption;
    private Button squareButton,wideButton;
    private boolean widePreview,updatingControls,compactPreview,tinyPreview;
    private String feedbackState="none";
    private String fullPreviewWarning="";
    private long exampleNow;
    private final Runnable publish=()->{if(current!=null)WeeklyWidget.saveStyle(this,current.copy());};
    private final Runnable endEffect=()->{feedbackState="none";updatePreview();};
    private interface BoolGet {boolean get();}
    private interface BoolSet {void set(boolean value);}
    private interface NumberGet {float get();}
    private interface NumberSet {void set(float value);}
    private interface IntGet {int get();}
    private interface IntSet {void set(int value);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);current=WeeklyWidget.style(this).copy();current.normalize();exampleNow=System.currentTimeMillis();
        if(state!=null){selectedCategory=Math.max(0,Math.min(3,state.getInt("category",0)));selectedElement=Math.max(0,Math.min(3,state.getInt("element",0)));widePreview=state.getBoolean("wide",false);}
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        // Own the insets on API 30+: do not combine a decor-resized content area with IME padding.
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root=new LinearLayout(this);rootLayout=root;root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int bottom=Math.max(bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom);view.setPadding(bars.left,bars.top,bars.right,bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            view.post(this::adjustPinnedLayout);
            return insets;
        });
        root.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->adjustPinnedLayout());
        buildPreviewHeader(root);
        buildNavigation(root);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        settings=new LinearLayout(this);settings.setOrientation(LinearLayout.VERTICAL);settings.setPadding(dp(12),dp(4),dp(12),dp(24));
        scroll.addView(settings,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);root.requestFocus();buildSettings();updatePreview();
    }
    @Override protected void onStart(){super.onStart();updatePreview();}
    @Override protected void onSaveInstanceState(Bundle state){flush();state.putInt("category",selectedCategory);state.putInt("element",selectedElement);state.putBoolean("wide",widePreview);super.onSaveInstanceState(state);}
    @Override protected void onStop(){ui.removeCallbacks(endEffect);feedbackState="none";flush();super.onStop();}
    @Override protected void onDestroy(){ui.removeCallbacks(publish);ui.removeCallbacks(endEffect);super.onDestroy();}

    private void buildPreviewHeader(LinearLayout root){
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(12),dp(4),dp(12),dp(4));
        LinearLayout titleRow=new LinearLayout(this);previewTitle=titleRow;titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);title.setText("위젯 꾸미기");title.setTextSize(19);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setTextColor(TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        titleRow.addView(compactButton("기본값",this::confirmDefaults),new LinearLayout.LayoutParams(dp(64),dp(44)));
        LinearLayout.LayoutParams doneLp=new LinearLayout.LayoutParams(dp(60),dp(44));doneLp.leftMargin=dp(6);titleRow.addView(compactButton("완료",()->{finishEditing();flush();finish();}),doneLp);header.addView(titleRow);
        LinearLayout previewRow=new LinearLayout(this);previewRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout stage=new FrameLayout(this);previewStage=stage;stage.setPadding(dp(3),dp(3),dp(3),dp(3));
        previewImage=new ImageView(this);previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);previewImage.setBackground(new Checkerboard());previewImage.setOnClickListener(v->showEffect("running"));
        stage.addView(previewImage,new FrameLayout.LayoutParams(dp(67),dp(92),Gravity.CENTER));previewRow.addView(stage,new LinearLayout.LayoutParams(dp(152),dp(100)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(8),0,0,0);previewRow.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout modeRow=new LinearLayout(this);modeRow.setGravity(Gravity.CENTER_VERTICAL);
        squareButton=compactButton("1 × 1",()->{widePreview=false;updatePreview();});wideButton=compactButton("2 × 1",()->{widePreview=true;updatePreview();});
        squareButton.setMaxLines(1);squareButton.setEllipsize(TextUtils.TruncateAt.END);wideButton.setMaxLines(1);wideButton.setEllipsize(TextUtils.TruncateAt.END);
        modeRow.addView(squareButton,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams wideLp=new LinearLayout.LayoutParams(0,dp(44),1);wideLp.leftMargin=dp(4);modeRow.addView(wideButton,wideLp);info.addView(modeRow);
        previewCaption=text(info,"",11,MUTED,false);previewCaption.setGravity(Gravity.CENTER);previewCaption.setMaxLines(3);header.addView(previewRow);
        previewWarning=text(header,"",11,0xffffd398,false);previewWarning.setMinHeight(dp(32));previewWarning.setMaxLines(2);previewWarning.setEllipsize(TextUtils.TruncateAt.END);previewWarning.setGravity(Gravity.CENTER);previewWarning.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        previewWarning.setOnClickListener(v->{if(!fullPreviewWarning.isEmpty())new AlertDialog.Builder(this).setTitle("미리보기 표시 안내").setMessage(fullPreviewWarning).setPositiveButton("확인",null).show();});
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));View line=new View(this);line.setBackgroundColor(0xff313a46);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void buildNavigation(LinearLayout root){
        navigation=new LinearLayout(this);navigation.setOrientation(LinearLayout.VERTICAL);navigation.setPadding(dp(10),dp(4),dp(10),dp(4));
        LinearLayout categories=new LinearLayout(this);
        for(int i=0;i<4;i++){final int category=i;categoryButtons[i]=navigationButton(categories,CATEGORY_NAMES[i],()->navigate(category,selectedElement));}
        navigation.addView(categories);elementNavigation=new LinearLayout(this);
        for(int i=0;i<4;i++){final int element=i;elementButtons[i]=navigationButton(elementNavigation,ELEMENT_NAMES[i],()->navigate(selectedCategory,element));}
        navigation.addView(elementNavigation);root.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
    }
    private Button navigationButton(LinearLayout row,String title,Runnable action){Button button=compactButton(title,action);button.setTextSize(12);button.setMaxLines(1);button.setEllipsize(TextUtils.TruncateAt.END);button.setPadding(dp(2),0,dp(2),0);button.setContentDescription(title);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(button,lp);return button;}
    private void adjustPinnedLayout(){
        if(rootLayout==null||previewStage==null||rootLayout.getHeight()==0)return;
        int available=rootLayout.getHeight()-rootLayout.getPaddingTop()-rootLayout.getPaddingBottom();boolean compact=available<dp(560),tiny=available<dp(360);
        boolean modeChanged=compact!=compactPreview||tiny!=tinyPreview;compactPreview=compact;tinyPreview=tiny;
        attachNavigation();if(!modeChanged)return;
        previewTitle.setVisibility(tiny?View.GONE:View.VISIBLE);previewCaption.setVisibility(compact?View.GONE:View.VISIBLE);
        previewWarning.setMaxLines(compact?1:2);previewWarning.setMinHeight(dp(compact?18:32));
        LinearLayout.LayoutParams stage=(LinearLayout.LayoutParams)previewStage.getLayoutParams();stage.width=dp(tiny?82:compact?108:152);stage.height=dp(tiny?52:compact?70:100);previewStage.setLayoutParams(stage);updatePreview();
    }
    private void attachNavigation(){
        if(settings==null||navigation==null||rootLayout==null)return;
        // Reattach idempotently after a settings rebuild even if the height mode is unchanged.
        // Only navigation moves: editor views and their focused input remain in place.
        ViewGroup owner=(ViewGroup)navigation.getParent();LinearLayout target=tinyPreview?settings:rootLayout;
        if(owner==target)return;if(owner!=null)owner.removeView(navigation);
        target.addView(navigation,tinyPreview?0:2,new LinearLayout.LayoutParams(-1,-2));
    }
    private void navigate(int category,int element){
        if(category==selectedCategory&&element==selectedElement)return;
        finishEditing();scrollPositions[selectedCategory][selectedCategory<2?selectedElement:0]=scroll.getScrollY();
        selectedCategory=category;selectedElement=element;showSelectedPage();
        final int position=scrollPositions[category][category<2?element:0];scroll.post(()->scroll.scrollTo(0,position));
    }
    private void finishEditing(){View focused=getCurrentFocus();if(focused instanceof EditText){focused.clearFocus();rootLayout.requestFocus();android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(rootLayout.getWindowToken(),0);}}
    private void showSelectedPage(){
        for(int i=0;i<4;i++){if(pages[i]!=null)pages[i].setVisibility(i==selectedCategory?View.VISIBLE:View.GONE);if(textPanels[i]!=null)textPanels[i].setVisibility(i==selectedElement?View.VISIBLE:View.GONE);if(layoutPanels[i]!=null)layoutPanels[i].setVisibility(i==selectedElement?View.VISIBLE:View.GONE);paintSelected(categoryButtons[i],i==selectedCategory);paintSelected(elementButtons[i],i==selectedElement);}
        elementNavigation.setVisibility(selectedCategory<2?View.VISIBLE:View.GONE);
    }
    private void paintSelected(Button button,boolean selected){button.setSelected(selected);button.setTextColor(selected?BG:TEXT);button.setBackground(round(selected?ACCENT:0xff28313d,10));}
    private void buildSettings(){
        controlUpdates.clear();settings.removeAllViews();
        attachNavigation();
        for(int i=0;i<4;i++){pages[i]=column(settings);}
        for(int id=0;id<4;id++){buildTextElement(id);buildLayoutElement(id);}
        LinearLayout layout=section(pages[1],"위젯 전체");toggle(layout,"공간이 부족하면 글자 자동 맞춤",()->current.autoFit,value->current.autoFit=value);
        LinearLayout spacing=accordion(layout,"안쪽 여백·요소 간격",false);toggle(spacing,"위젯 크기에 맞게 자동 여백",()->current.automaticPadding,value->current.automaticPadding=value);
        LinearLayout manual=column(spacing);numeric(manual,"좌우 여백",0,32,.5f,()->current.paddingHorizontalDp,value->current.paddingHorizontalDp=value,"dp",true);numeric(manual,"상하 여백",0,32,.5f,()->current.paddingVerticalDp,value->current.paddingVerticalDp=value,"dp",true);numeric(manual,"요소 사이 간격",0,16,.5f,()->current.rowGapDp,value->current.rowGapDp=value,"dp",true);
        controlUpdates.add(()->manual.setVisibility(current.automaticPadding?View.GONE:View.VISIBLE));text(spacing,"자동을 끄면 여백과 간격을 직접 조절할 수 있어요.",11,MUTED,false);
        LinearLayout background=section(pages[2],"배경");colorControl(background,"배경색",()->current.background,value->current.background=value);numeric(background,"불투명도",0,100,1,()->current.opacity,value->current.opacity=Math.round(value),"%",false);numeric(background,"모서리 둥글기",0,32,1,()->current.radius,value->current.radius=Math.round(value),"dp",false);text(background,"격자는 투명한 부분이에요. 실제 크기는 홈 화면 격자에 따라 달라져요.",11,MUTED,false);
        LinearLayout feedback=section(pages[3],"조회 효과");
        toggle(feedback,"새로고침 상태 효과 표시",()->current.feedbackEnabled,value->{current.feedbackEnabled=value;if(!value){feedbackState="none";ui.removeCallbacks(endEffect);}});
        numeric(feedback,"효과 지속시간",.1f,10f,.1f,()->current.feedbackDurationMs/1000f,value->current.feedbackDurationMs=Math.round(value*1000),"초",true);
        text(feedback,"0.1–10.0초 · 0.1초 단위 · 기본 1.0초",11,MUTED,false);
        text(feedback,"효과를 눌러 미리 보기",13,TEXT,true);LinearLayout states=column(feedback);String[] labels={"대기","조회 중","성공","실패","건너뜀"},values={"waiting","running","success","error","skipped"};for(int start=0;start<5;start+=3){LinearLayout line=new LinearLayout(this);states.addView(line);for(int i=start;i<Math.min(5,start+3);i++){final String effect=values[i];weighted(line,labels[i],()->showEffect(effect));}}
        text(feedback,"위의 위젯을 눌러도 효과를 볼 수 있어요. 미리보기는 실제 조회나 갱신 시각을 바꾸지 않아요.",11,MUTED,false);
        showSelectedPage();refreshControls();
    }
    private void confirmDefaults(){new AlertDialog.Builder(this).setTitle("기본 스타일로 되돌릴까요?").setMessage("꾸미기 설정만 초기화해요. 계정 연결은 유지돼요.").setNegativeButton("취소",null).setPositiveButton("되돌리기",(d,w)->{finishEditing();current=WidgetStyle.defaults();feedbackState="none";ui.removeCallbacks(endEffect);for(int[] positions:scrollPositions)Arrays.fill(positions,0);buildSettings();changed();scroll.scrollTo(0,0);}).show();}
    private void buildTextElement(int id){
        WidgetStyle.Row row=current.rows[id];textPanels[id]=column(pages[0]);LinearLayout card=section(textPanels[id],ROW_NAMES[id]);toggle(card,"위젯에 표시",()->row.enabled,value->row.enabled=value);
        if(id==WidgetStyle.RESET)dateControls(card,current.resetDate,"초기화");if(id==WidgetStyle.LAST)dateControls(card,current.lastDate,"조회");
        if(id==WidgetStyle.BRAND){choice(card,"표시 방식",BRAND_NAMES,()->current.brandMode,value->current.brandMode=value);LinearLayout logo=accordion(card,"로고 크기",false);numeric(logo,"로고 크기",6,64,.5f,()->current.brandLogoSizeSp,value->current.brandLogoSizeSp=value,"sp",true);}
        LinearLayout typography=accordion(card,"글꼴·크기·굵기",true);choice(typography,"글꼴",FONT_NAMES,()->row.font,value->row.font=value);numeric(typography,"글자 크기",6,96,.5f,()->row.sizeSp,value->row.sizeSp=value,"sp",true);toggle(typography,"굵게",()->row.bold,value->row.bold=value);colorControl(card,"글자색",()->row.color,value->row.color=value);
    }
    private void buildLayoutElement(int id){
        WidgetStyle.Row row=current.rows[id];layoutPanels[id]=column(pages[1]);LinearLayout card=section(layoutPanels[id],ROW_NAMES[id]);toggle(card,"위젯에 표시",()->row.enabled,value->row.enabled=value);
        choice(card,"가로 정렬",ALIGN_NAMES,()->row.alignment,value->row.alignment=value);numeric(card,"위아래 위치",-50,50,1,()->row.offsetY,value->row.offsetY=value,"%",false);text(card,"− 위로  /  + 아래로 · 위젯 높이 기준",11,MUTED,false);
        LinearLayout orderRow=new LinearLayout(this);orderRow.setGravity(Gravity.CENTER_VERTICAL);TextView orderText=new TextView(this);orderText.setTextSize(12);orderText.setTextColor(MUTED);orderRow.addView(orderText,new LinearLayout.LayoutParams(0,-2,1));
        Button up=compactButton("↑ 위로",()->move(id,-1)),down=compactButton("↓ 아래로",()->move(id,1));orderRow.addView(up,new LinearLayout.LayoutParams(dp(76),dp(44)));orderRow.addView(down,new LinearLayout.LayoutParams(dp(86),dp(44)));card.addView(orderRow);
        controlUpdates.add(()->{int position=indexOf(id);orderText.setText("표시 순서 "+(position+1)+" / 4");up.setEnabled(position>0);down.setEnabled(position<3);});
        TextView orderSummary=text(card,"",11,MUTED,false);controlUpdates.add(()->{StringBuilder sequence=new StringBuilder();for(int element:current.order){if(sequence.length()>0)sequence.append(" → ");sequence.append(ELEMENT_NAMES[element]);if(!current.rows[element].enabled)sequence.append("(숨김)");}orderSummary.setText(sequence.toString());});
    }
    private void dateControls(LinearLayout parent,WidgetStyle.DateSpec date,String label){
        TextView example=text(parent,"",12,ACCENT,false);example.setMaxLines(2);example.setEllipsize(TextUtils.TruncateAt.END);controlUpdates.add(()->{String value=date.format((exampleNow+(label.equals("초기화")?2*86400_000L:0))/1000,TimeZone.getDefault(),label);example.setText("예시: "+(value.isEmpty()?"표시 없음":value));});
        LinearLayout details=accordion(parent,"날짜·시간 구성",false);
        GridLayout parts=new GridLayout(this);parts.setColumnCount(3);details.addView(parts,new LinearLayout.LayoutParams(-1,-2));
        part(parts,"연도",()->date.year,value->date.year=value);part(parts,"월",()->date.month,value->date.month=value);part(parts,"일",()->date.day,value->date.day=value);part(parts,"요일",()->date.weekday,value->date.weekday=value);part(parts,"시",()->date.hour,value->date.hour=value);part(parts,"분",()->date.minute,value->date.minute=value);part(parts,"초",()->date.second,value->date.second=value);part(parts,"오전/오후",()->date.ampm,value->date.ampm=value);
        LinearLayout presets=new LinearLayout(this);weighted(presets,"날짜만",()->{date.year=true;date.month=true;date.day=true;date.weekday=false;date.hour=false;date.minute=false;date.second=false;date.ampm=false;changed();});weighted(presets,"시간만",()->{date.year=false;date.month=false;date.day=false;date.weekday=false;date.hour=true;date.minute=true;date.second=false;date.ampm=false;changed();});weighted(presets,"모두 숨김",()->{date.year=false;date.month=false;date.day=false;date.weekday=false;date.hour=false;date.minute=false;date.second=false;date.ampm=false;changed();});details.addView(presets);
        GridLayout format=new GridLayout(this);format.setColumnCount(2);details.addView(format,new LinearLayout.LayoutParams(-1,-2));
        part(format,"24시간제",()->date.use24h,value->date.use24h=value);part(format,"앞자리 0",()->date.leadingZero,value->date.leadingZero=value);part(format,"두 줄 표시",()->date.twoLines,value->date.twoLines=value);part(format,"‘"+label+"’ 표시",()->date.label,value->date.label=value);choice(details,"날짜 구분자",SEPARATOR_NAMES,()->date.separator,value->date.separator=value);text(details,"24시간제를 끄면 12시간제로 표시해요.\n오전/오후는 위에서 따로 켤 수 있어요.",11,MUTED,false);
    }
    private void part(GridLayout grid,String label,BoolGet get,BoolSet set){CheckBox box=new CheckBox(this);box.setText(label);box.setTextSize(12);box.setTextColor(TEXT);box.setMinHeight(dp(48));box.setButtonTintList(ColorStateList.valueOf(ACCENT));GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),GridLayout.spec(GridLayout.UNDEFINED,1f));lp.width=0;lp.height=-2;grid.addView(box,lp);controlUpdates.add(()->box.setChecked(get.get()));box.setOnCheckedChangeListener((button,checked)->{if(!updatingControls){set.set(checked);changed();}});}
    private void numeric(LinearLayout parent,String title,float min,float max,float step,NumberGet get,NumberSet set,String unit,boolean decimal){
        gap(parent,10);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView label=new TextView(this);label.setText(title);label.setTextColor(TEXT);label.setTextSize(14);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(14);input.setTextColor(TEXT);input.setSelectAllOnFocus(true);input.setGravity(Gravity.CENTER);input.setPadding(dp(4),0,dp(4),0);input.setBackground(round(0xff28313d,8));input.setInputType(InputType.TYPE_CLASS_NUMBER|(decimal?InputType.TYPE_NUMBER_FLAG_DECIMAL:0)|(min<0?InputType.TYPE_NUMBER_FLAG_SIGNED:0));input.setImeOptions(EditorInfo.IME_ACTION_DONE);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});input.setContentDescription(title+" 직접 입력, "+min+"에서 "+max+unit);row.addView(input,new LinearLayout.LayoutParams(dp(70),dp(44)));TextView unitView=new TextView(this);unitView.setText(" "+unit);unitView.setTextColor(MUTED);unitView.setTextSize(12);row.addView(unitView);parent.addView(row);
        SeekBar slider=new SeekBar(this);slider.setMax(Math.round((max-min)/step));slider.setContentDescription(title);slider.setProgressTintList(ColorStateList.valueOf(ACCENT));slider.setThumbTintList(ColorStateList.valueOf(ACCENT));parent.addView(slider,new LinearLayout.LayoutParams(-1,dp(44)));boolean[] updating={false};
        Runnable reflect=()->{updating[0]=true;float value=get.get();slider.setProgress(Math.round((value-min)/step));String display=number(value,decimal);if(!input.hasFocus()&&!display.contentEquals(input.getText()))input.setText(display);updating[0]=false;};controlUpdates.add(reflect);
        Runnable commit=()->{if(updating[0])return;try{float value=Float.parseFloat(input.getText().toString());if(!Float.isFinite(value)||value<min||value>max)throw new NumberFormatException();value=min+Math.round((value-min)/step)*step;set.set(value);input.setError(null);updating[0]=true;input.setText(number(value,decimal));slider.setProgress(Math.round((value-min)/step));updating[0]=false;changed();flush();}catch(NumberFormatException e){input.setError(number(min,decimal)+"–"+number(max,decimal)+unit+" 범위로 입력해 주세요.");}};
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){if(updating[0]||updatingControls||!input.hasFocus())return;try{float parsed=Float.parseFloat(value.toString());if(Float.isFinite(parsed)&&parsed>=min&&parsed<=max){set.set(min+Math.round((parsed-min)/step)*step);input.setError(null);updating[0]=true;slider.setProgress(Math.round((get.get()-min)/step));updating[0]=false;changed();}}catch(NumberFormatException ignored){}}});
        input.setOnFocusChangeListener((view,focused)->{if(!focused)commit.run();});input.setOnEditorActionListener((view,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){commit.run();input.clearFocus();return true;}return false;});
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user&&!updating[0]){set.set(min+value*step);updating[0]=true;input.setText(number(get.get(),decimal));updating[0]=false;changed();}}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){flush();}});
    }
    private void colorControl(LinearLayout parent,String title,IntGet get,IntSet set){
        LinearLayout editor=new LinearLayout(this);editor.setOrientation(LinearLayout.VERTICAL);editor.setVisibility(View.GONE);Button reveal=button(parent,"",()->{});
        controlUpdates.add(()->{reveal.setText(title+"  "+WidgetStyle.rgb(get.get())+"  ▾");GradientDrawable swatch=round(get.get(),6);swatch.setBounds(0,0,dp(22),dp(22));reveal.setCompoundDrawables(swatch,null,null,null);reveal.setCompoundDrawablePadding(dp(10));});text(editor,"휠: 색상·채도  /  아래 막대: 밝기",11,MUTED,false);
        ColorWheelView wheel=new ColorWheelView(this);wheel.setColor(get.get());editor.addView(wheel,new LinearLayout.LayoutParams(-1,dp(230)));TextView hsv=text(editor,"",11,MUTED,false);
        SeekBar brightness=new SeekBar(this);brightness.setMax(100);brightness.setContentDescription(title+" 밝기");brightness.setProgressTintList(ColorStateList.valueOf(ACCENT));editor.addView(brightness,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout hexRow=new LinearLayout(this);hexRow.setGravity(Gravity.CENTER_VERTICAL);TextView hexLabel=new TextView(this);hexLabel.setText("HEX  ");hexLabel.setTextColor(TEXT);hexRow.addView(hexLabel);EditText hex=new EditText(this);hex.setSingleLine(true);hex.setTextColor(TEXT);hex.setTextSize(15);hex.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});hex.setImeOptions(EditorInfo.IME_ACTION_DONE);hex.setSelectAllOnFocus(true);hex.setContentDescription(title+" HEX #RRGGBB");hexRow.addView(hex,new LinearLayout.LayoutParams(0,dp(48),1));editor.addView(hexRow);
        text(editor,"최근 사용한 색상",11,MUTED,false);LinearLayout recent=new LinearLayout(this);editor.addView(recent,new LinearLayout.LayoutParams(-1,dp(48)));boolean[] updating={false};
        Runnable reflect=()->{updating[0]=true;int color=get.get();wheel.setColor(color);float[] value=wheel.getHsv();brightness.setProgress(Math.round(value[2]*100));String display=WidgetStyle.rgb(color);if(!hex.hasFocus()&&!display.contentEquals(hex.getText()))hex.setText(display);hsv.setText(String.format(Locale.KOREA,"색상 %.0f° · 채도 %.0f%% · 밝기 %.0f%%",value[0],value[1]*100,value[2]*100));updating[0]=false;};controlUpdates.add(reflect);
        IntSet picked=color->{set.set(color);updating[0]=true;hex.setText(WidgetStyle.rgb(color));hex.setError(null);updating[0]=false;changed();};
        Runnable recentRefresh=()->{recent.removeAllViews();for(int value:recentColors()){Button swatch=new Button(this);swatch.setBackground(round(value,8));swatch.setContentDescription(title+" "+WidgetStyle.rgb(value));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.setMargins(dp(2),dp(4),dp(2),dp(4));recent.addView(swatch,lp);swatch.setOnClickListener(v->{picked.set(value);rememberColor(value);});}};
        reveal.setOnClickListener(v->{boolean show=editor.getVisibility()!=View.VISIBLE;editor.setVisibility(show?View.VISIBLE:View.GONE);if(show){reflect.run();recentRefresh.run();}});
        wheel.setOnColorChangedListener((color,finished)->{if(!updating[0]){picked.set(color);if(finished){rememberColor(color);recentRefresh.run();flush();}}});
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user&&!updating[0]){wheel.setBrightness(value/100f);picked.set(wheel.getColor());}}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){rememberColor(get.get());recentRefresh.run();flush();}});
        Runnable commit=()->{try{int value=WidgetStyle.parseRgb(hex.getText().toString());set.set(value);hex.setError(null);rememberColor(value);recentRefresh.run();changed();flush();}catch(IllegalArgumentException e){hex.setError("#RRGGBB 형식으로 입력해 주세요.");}};
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable value){if(updating[0]||updatingControls||!hex.hasFocus())return;try{set.set(WidgetStyle.parseRgb(value.toString()));hex.setError(null);changed();}catch(IllegalArgumentException ignored){}}});
        hex.setOnFocusChangeListener((v,focused)->{if(!focused)commit.run();});hex.setOnEditorActionListener((view,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){commit.run();hex.clearFocus();return true;}return false;});parent.addView(editor);
    }
    private int[] recentColors(){LinkedHashSet<Integer> values=new LinkedHashSet<>();for(String value:getSharedPreferences("widget_style_ui",MODE_PRIVATE).getString("recent_colors","").split(","))try{values.add(WidgetStyle.parseRgb(value));}catch(IllegalArgumentException ignored){}for(int value:new int[]{0xffffffff,0xff14181f,0xffb8efcf,0xffb9a7ec,0xffffce91,0xffffaab9})values.add(value);int[] result=new int[Math.min(6,values.size())];int i=0;for(int value:values){if(i==result.length)break;result[i++]=value;}return result;}
    private void rememberColor(int color){LinkedHashSet<Integer> values=new LinkedHashSet<>();values.add(color);for(int value:recentColors())values.add(value);StringBuilder saved=new StringBuilder();int i=0;for(int value:values){if(i++==6)break;if(saved.length()>0)saved.append(',');saved.append(WidgetStyle.rgb(value));}getSharedPreferences("widget_style_ui",MODE_PRIVATE).edit().putString("recent_colors",saved.toString()).apply();}
    private void move(int id,int delta){int index=indexOf(id),next=index+delta;if(index<0||next<0||next>=current.order.length)return;int other=current.order[next];current.order[next]=id;current.order[index]=other;changed();}
    private int indexOf(int id){for(int i=0;i<current.order.length;i++)if(current.order[i]==id)return i;return 0;}
    private void updatePreview(){
        if(previewImage==null||current==null)return;int width=widePreview?138:64;int height=tinyPreview?44:compactPreview?62:92;FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)previewImage.getLayoutParams();params.width=dp(Math.round(width*height/88f));params.height=dp(height);previewImage.setLayoutParams(params);
        Usage example=new Usage("preview","",27,exampleNow/1000+2*86400,exampleNow);WidgetRenderer.Result rendered=WidgetRenderer.render(this,example,current,width,88,current.feedbackEnabled?feedbackState:"none");
        previewImage.setImageBitmap(rendered.bitmap);previewImage.setContentDescription("미리보기. "+rendered.accessibility+". 누르면 효과 미리보기");fullPreviewWarning=rendered.warning==null?"":rendered.warning;previewWarning.setText(fullPreviewWarning.isEmpty()?"변경 즉시 미리보기 · 모든 위젯에 자동 저장":fullPreviewWarning);previewWarning.setContentDescription(fullPreviewWarning.isEmpty()?"표시 경고 없음. 모든 위젯에 자동 저장":fullPreviewWarning+". 누르면 전체 안내");previewCaption.setText("예시 73% · 실시간 적용\n"+(feedbackState.equals("none")?"위젯을 눌러 효과 보기":"효과 미리보기 중"));
        squareButton.setTextColor(!widePreview?BG:TEXT);squareButton.setBackground(round(!widePreview?ACCENT:0xff28313d,10));wideButton.setTextColor(widePreview?BG:TEXT);wideButton.setBackground(round(widePreview?ACCENT:0xff28313d,10));
    }
    private void showEffect(String state){if(!current.feedbackEnabled){Toast.makeText(this,"효과 표시를 켜면 미리 볼 수 있어요.",Toast.LENGTH_SHORT).show();return;}feedbackState=state;ui.removeCallbacks(endEffect);updatePreview();ui.postDelayed(endEffect,current.feedbackDurationMs);}
    private void changed(){current.normalize();refreshControls();updatePreview();ui.removeCallbacks(publish);ui.postDelayed(publish,220);}
    private void refreshControls(){updatingControls=true;try{for(Runnable update:controlUpdates)update.run();}finally{updatingControls=false;}}
    private void flush(){ui.removeCallbacks(publish);if(current!=null){current.normalize();WeeklyWidget.saveStyle(this,current.copy());}}
    private void choice(LinearLayout parent,String title,String[] names,IntGet get,IntSet set){
        Button pick=button(parent,"",()->{});LinearLayout options=column(parent);options.setVisibility(View.GONE);
        for(int i=0;i<names.length;i++){final int index=i;Button option=button(options,names[i],()->{set.set(index);options.setVisibility(View.GONE);changed();});controlUpdates.add(()->paintSelected(option,get.get()==index));}
        pick.setOnClickListener(v->{finishEditing();options.setVisibility(options.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);refreshControls();});
        controlUpdates.add(()->pick.setText(title+" · "+names[Math.max(0,Math.min(names.length-1,get.get()))]+(options.getVisibility()==View.VISIBLE?" ▴":" ▾")));
    }
    private void toggle(LinearLayout parent,String title,BoolGet get,BoolSet set){Switch control=new Switch(this);control.setText(title);control.setTextColor(TEXT);control.setTextSize(13);control.setPadding(0,dp(12),0,dp(12));control.setMinHeight(dp(48));parent.addView(control,new LinearLayout.LayoutParams(-1,-2));controlUpdates.add(()->control.setChecked(get.get()));control.setOnCheckedChangeListener((view,checked)->{if(!updatingControls){set.set(checked);changed();}});}
    private LinearLayout column(LinearLayout parent){LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);parent.addView(result,new LinearLayout.LayoutParams(-1,-2));return result;}
    private LinearLayout section(LinearLayout parent,String title){gap(parent,8);LinearLayout box=column(parent);box.setPadding(dp(12),dp(10),dp(12),dp(12));box.setBackground(round(CARD,16));text(box,title,15,TEXT,true);return box;}
    private LinearLayout accordion(LinearLayout parent,String title,boolean expanded){Button reveal=button(parent,title+(expanded?" ▴":" ▾"),()->{});LinearLayout content=column(parent);content.setVisibility(expanded?View.VISIBLE:View.GONE);reveal.setOnClickListener(v->{finishEditing();boolean show=content.getVisibility()!=View.VISIBLE;content.setVisibility(show?View.VISIBLE:View.GONE);reveal.setText(title+(show?" ▴":" ▾"));});return content;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);label.setPadding(0,dp(3),0,dp(3));if(bold)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(label,new LinearLayout.LayoutParams(-1,-2));return label;}
    private Button button(LinearLayout parent,String title,Runnable action){Button button=compactButton(title,action);button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);button.setPadding(dp(12),dp(9),dp(12),dp(9));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);parent.addView(button,lp);return button;}
    private Button compactButton(String title,Runnable action){Button button=new Button(this);button.setText(title);button.setAllCaps(false);button.setTextColor(TEXT);button.setTextSize(13);button.setMinHeight(dp(44));button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(6),0,dp(6),0);button.setBackground(round(0xff28313d,10));button.setOnClickListener(v->action.run());return button;}
    private void weighted(LinearLayout row,String title,Runnable action){Button button=compactButton(title,action);button.setTextSize(11);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(dp(2),dp(5),dp(2),dp(5));row.addView(button,lp);}
    private static String number(float value,boolean decimal){return decimal?String.format(Locale.ROOT,"%.1f",value):Integer.toString(Math.round(value));}
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private GradientDrawable round(int color,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private final class Checkerboard extends Drawable {
        private final Paint paint=new Paint();
        @Override public void draw(Canvas canvas){Rect bounds=getBounds();int tile=dp(10);for(int y=bounds.top;y<bounds.bottom;y+=tile)for(int x=bounds.left;x<bounds.right;x+=tile){paint.setColor(((x-bounds.left)/tile+(y-bounds.top)/tile)%2==0?0xff46505d:0xff353e4a);canvas.drawRect(x,y,Math.min(bounds.right,x+tile),Math.min(bounds.bottom,y+tile),paint);}}
        @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
