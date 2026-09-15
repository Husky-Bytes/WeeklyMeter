package dev.yerin.weeklymeter;

/** Pure state model; elapsed time only, and no usage values or credentials. */
final class RefreshFeedbackModel {
    static final long WAITING_MS=20_000, RUNNING_MS=60_000;
    final String state;
    final long requestId,startedAt,expiresAt;
    private RefreshFeedbackModel(String state,long requestId,long startedAt,long expiresAt){
        this.state=state;this.requestId=requestId;this.startedAt=startedAt;this.expiresAt=expiresAt;
    }
    static RefreshFeedbackModel none(){return new RefreshFeedbackModel("none",0,0,0);}
    static int duration(int requested){return Math.max(100,Math.min(10_000,requested));}
    static RefreshFeedbackModel begin(String state,long requestId,long now,int terminalDuration){
        long duration;
        if("waiting".equals(state))duration=WAITING_MS;
        else if("running".equals(state))duration=RUNNING_MS;
        else if("success".equals(state)||"error".equals(state)||"skipped".equals(state))duration=duration(terminalDuration);
        else return none();
        if(requestId<=0||now<0||now>Long.MAX_VALUE-duration)return none();
        return new RefreshFeedbackModel(state,requestId,now,now+duration);
    }
    boolean visible(long now,boolean enabled){
        return enabled&&requestId>0&&!"none".equals(state)&&now>=startedAt&&now<expiresAt
            &&expiresAt>startedAt&&expiresAt-startedAt<=RUNNING_MS;
    }
    boolean matches(long id){return id>0&&requestId==id;}
}
