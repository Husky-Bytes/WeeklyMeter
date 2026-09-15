package dev.yerin.weeklymeter;

import java.util.*;

/** Parses weekly windows only when the SERVER states an exact seven-day duration. */
public final class Usage {
    public static final long WEEK_SECONDS=604800L;
    public final String id, label;
    public final double used;
    public final long resetsAt, fetchedAt;
    public Usage(String id,String label,double used,long resetsAt,long fetchedAt) {
        if(id==null||id.isEmpty()||!Double.isFinite(used)||used<0||used>100||resetsAt<0||fetchedAt<0)throw new IllegalArgumentException("사용량 값 오류");
        this.id=id;this.label=label;this.used=used;this.resetsAt=resetsAt;this.fetchedAt=fetchedAt;
    }
    public double remaining(){return Math.max(0,100-used);}
    public boolean expired(long nowMillis){
        // A missing reset must not make an old weekly snapshot valid forever.
        // A backwards clock change also makes its apparent age untrustworthy.
        return nowMillis<fetchedAt || nowMillis-fetchedAt>=WEEK_SECONDS*1000L
            || (resetsAt>0 && nowMillis/1000>=resetsAt);
    }
    public String percent(){
        double n=remaining();
        return Math.abs(n-Math.rint(n))<0.000001 ? String.format(Locale.KOREA,"%.0f%%",n) : String.format(Locale.KOREA,"%.1f%%",n);
    }
    public Map<String,Object> toMap(){return Json.map("id",id,"label",label,"used",used,"reset",resetsAt,"fetched",fetchedAt);}
    public static Usage fromMap(Map<String,Object> m){
        // Cache fields are required: malformed timestamps must not become "unknown".
        long reset=Json.integer(m.get("reset"),-1),fetched=Json.integer(m.get("fetched"),-1);
        if(reset>100_000_000_000L)throw new IllegalArgumentException("저장된 초기화 시각 형식이 달라졌어.");
        return new Usage(Json.string(m.get("id")),Json.string(m.get("label")),Json.number(m.get("used"),Double.NaN),reset,fetched);
    }
    public static List<Usage> parse(String body,long nowMillis) {
        Object decoded=Json.parse(body);
        if(!(decoded instanceof Map))throw new IllegalArgumentException("사용량 응답이 객체가 아니야.");
        Map<String,Object> root=Json.object(decoded);
        List<Usage> out=new ArrayList<>();
        // Also accept documented App Server results, useful for fixtures or future transports.
        Map<String,Object> result=root.containsKey("result")?objectOrNull(root.get("result")):root;
        boolean raw=root.get("rate_limit")!=null||root.get("additional_rate_limits")!=null;
        boolean rpc=result.get("rateLimitsByLimitId")!=null||result.get("rateLimits")!=null;
        if(raw&&rpc)throw new IllegalArgumentException("사용량 응답에 서로 다른 한도 형식이 함께 있어.");
        if(raw) {
            collect(out,"codex","기본 한도",objectOrNull(root.get("rate_limit")),nowMillis,false);
            Object extras=root.get("additional_rate_limits");
            if(extras!=null&&!(extras instanceof List))throw schema();
            for(Object entry:Json.array(extras)) {
                if(!(entry instanceof Map))throw schema();
                Map<String,Object> extra=Json.object(entry);
                String id=first(extra,"metered_feature","limit_id","limit_name");
                String label=first(extra,"limit_name","metered_feature","limit_id");
                // A weekly entry without an identity cannot be silently omitted.
                collect(out,id,clean(label),objectOrNull(extra.get("rate_limit")),nowMillis,false);
            }
        } else if(result.get("rateLimitsByLimitId")!=null) {
            // A present bucket map is authoritative, even if it has no weekly window.
            // Falling back in that case could display an unrelated legacy bucket.
            Map<String,Object> buckets=objectOrNull(result.get("rateLimitsByLimitId"));
            for(Map.Entry<String,Object> e:buckets.entrySet()) {
                Map<String,Object> b=objectOrNull(e.getValue());
                String declaredId=Json.string(b.get("limitId"));
                if(!declaredId.isEmpty()&&!declaredId.equals(e.getKey()))throw new IllegalArgumentException("주간 한도 항목의 식별자가 일치하지 않아.");
                String label=first(b,"limitName","limitId");
                collect(out,e.getKey(),label.isEmpty()?clean(e.getKey()):clean(label),b,nowMillis,true);
            }
        } else {
            Map<String,Object> legacy=objectOrNull(result.get("rateLimits"));
            String id=Json.string(legacy.get("limitId"));if(id.isEmpty())id="codex";
            String label=Json.string(legacy.get("limitName"));
            if(label.isEmpty())label=id.equals("codex")?"기본 한도":id;
            collect(out,id,clean(label),legacy,nowMillis,true);
        }
        Set<String> ids=new HashSet<>();
        for(Usage u:out)if(!ids.add(u.id))throw new IllegalArgumentException("주간 한도 항목이 중복돼. 임의로 선택하지 않았어.");
        if(out.isEmpty())throw new IllegalArgumentException("응답에서 7일짜리 주간 한도를 찾지 못했어. 공식 사용량 화면을 확인해 줘.");
        return out;
    }
    private static void collect(List<Usage> out,String id,String label,Map<String,Object> m,long now,boolean camel) {
        String[] keys=camel?new String[]{"primary","secondary"}:new String[]{"primary_window","secondary_window"};
        int count=0;
        for(String key:keys) {
            Map<String,Object>w=objectOrNull(m.get(key));
            Object rawDuration=w.get(camel?"windowDurationMins":"limit_window_seconds");
            long duration=Json.integer(rawDuration,-1);
            if(rawDuration!=null&&duration<0)throw schema();
            boolean week=camel?duration==10080:duration==WEEK_SECONDS;
            if(!week)continue;
            double used=Json.number(w.get(camel?"usedPercent":"used_percent"),Double.NaN);
            long reset=Json.integer(w.get(camel?"resetsAt":"reset_at"),0);
            if(w.containsKey(camel?"resetsAt":"reset_at") && w.get(camel?"resetsAt":"reset_at")!=null && reset==0)throw new IllegalArgumentException("초기화 시각 형식이 달라졌어.");
            if(reset>100_000_000_000L)throw new IllegalArgumentException("초기화 시각의 단위를 확인할 수 없어.");
            if(++count>1)throw new IllegalArgumentException("한 항목에 주간 한도가 두 개 있어. 임의로 선택하지 않았어.");
            out.add(new Usage(id,label,used,reset,now));
        }
    }
    private static Map<String,Object> objectOrNull(Object value){if(value!=null&&!(value instanceof Map))throw schema();return Json.object(value);}
    private static IllegalArgumentException schema(){return new IllegalArgumentException("사용량 응답 형식이 달라졌어. 공식 사용량 화면을 확인해 줘.");}
    private static String first(Map<String,Object> m,String...keys){for(String k:keys){String s=Json.string(m.get(k));if(!s.isEmpty())return s;}return "";}
    private static String clean(String s){return s.replaceAll("[\\p{Cntrl}]","").substring(0,Math.min(s.replaceAll("[\\p{Cntrl}]","").length(),60));}
}
