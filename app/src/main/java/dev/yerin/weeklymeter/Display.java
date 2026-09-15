package dev.yerin.weeklymeter;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.*;

final class Display {
    static String reset(Context c,Usage u){
        if(u==null)return Texts.t(c,"초기화 시각 미확인","Reset time unknown");
        if(u.resetsAt<=0)return Texts.t(c,"초기화 시각 없음","No reset time received");
        return Texts.t(c,"초기화 ","Resets ")+new SimpleDateFormat("M.d (E) HH:mm",Texts.locale(c)).format(new Date(u.resetsAt*1000));
    }
    static String last(Context c,Usage u){
        if(u==null)return Texts.t(c,"조회 기록 없음","No successful refresh yet");
        return Texts.t(c,"확인 ","Updated ")+new SimpleDateFormat("M.d HH:mm",Texts.locale(c)).format(new Date(u.fetchedAt));
    }
    static String state(Context c,Usage u){
        if(!Store.connected(c))return Texts.t(c,"앱에서 계정 연결","Connect an account in the app");
        if(u!=null&&u.expired(System.currentTimeMillis()))return u.resetsAt>0&&System.currentTimeMillis()/1000>=u.resetsAt?Texts.t(c,"초기화 시각 지남 · 갱신 필요","Reset time passed · refresh needed"):Texts.t(c,"조회값 유효기간 지남 · 갱신 필요","Reading expired · refresh needed");
        if(!Store.error(c).isEmpty())return u==null?Texts.t(c,"조회 실패 · 앱에서 확인","Refresh failed · check the app"):Texts.t(c,"갱신 실패 · 최근 조회값","Refresh failed · showing last reading");
        if(u==null)return Texts.t(c,"주간 한도 선택·조회 필요","Select a weekly limit and refresh");
        long request=Store.prefs(c).getLong("requested",0);
        long now=System.currentTimeMillis();
        if(request>u.fetchedAt&&now>=request&&now-request<90_000)return Texts.t(c,"갱신 요청됨 · 최근 조회값","Refresh requested · showing last reading");
        if(System.currentTimeMillis()-u.fetchedAt>30*60_000L)return Texts.t(c,"오래된 조회값 · 새로고침","Older reading · tap to refresh");
        return Texts.t(c,"최근 조회값 · ","Latest reading · ")+Messages.localize(u.label,Texts.locale(c));
    }
    private Display(){}
}
