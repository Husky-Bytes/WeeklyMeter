package dev.yerin.weeklymeter;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.*;

final class Display {
    static String reset(Usage u){
        if(u==null)return "초기화 시각 미확인";
        if(u.resetsAt<=0)return "초기화 시각을 받지 못했어";
        return "초기화 "+new SimpleDateFormat("M.d (E) HH:mm",Locale.KOREA).format(new Date(u.resetsAt*1000));
    }
    static String last(Usage u){
        if(u==null)return "아직 조회한 기록이 없어";
        return "확인 "+new SimpleDateFormat("M.d HH:mm",Locale.KOREA).format(new Date(u.fetchedAt));
    }
    static String state(Context c,Usage u){
        if(!Store.connected(c))return "앱에서 계정 연결";
        if(u!=null&&u.expired(System.currentTimeMillis()))return u.resetsAt>0&&System.currentTimeMillis()/1000>=u.resetsAt?"초기화 시각 지남 · 갱신 필요":"조회값 유효기간 지남 · 갱신 필요";
        if(!Store.error(c).isEmpty())return u==null?"조회 실패 · 앱에서 확인":"갱신 실패 · 최근 조회값";
        if(u==null)return "주간 한도 선택·조회 필요";
        long request=Store.prefs(c).getLong("requested",0);
        long now=System.currentTimeMillis();
        if(request>u.fetchedAt&&now>=request&&now-request<90_000)return "갱신 요청됨 · 최근 조회값";
        if(System.currentTimeMillis()-u.fetchedAt>30*60_000L)return "오래된 조회값 · 새로고침";
        return "최근 조회값 · "+u.label;
    }
    private Display(){}
}
