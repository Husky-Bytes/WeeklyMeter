package dev.yerin.weeklymeter;

/** Pure monotonic state checks; no Android, networking, or fabricated usage samples. */
public final class RefreshFeedbackTests {
    private static int checks;
    private static void check(boolean okay,String label){checks++;if(!okay)throw new AssertionError(label);}
    public static void main(String[]args){
        RefreshFeedbackModel none=RefreshFeedbackModel.none();
        check(!none.visible(0,true)&&!none.visible(1000,true),"empty feedback never visible");
        for(String state:new String[]{"waiting","running","success","error","skipped"}){
            RefreshFeedbackModel value=RefreshFeedbackModel.begin(state,11,10000,1000);
            check(value.visible(10000,true),state+" initially visible");
            check(!value.visible(10000,false),state+" setting off");
            check(!value.visible(9999,true),state+" clock rollback hidden");
            check(value.visible(value.expiresAt-1,true)&&!value.visible(value.expiresAt,true),state+" exact deadline");
            check(value.matches(11)&&!value.matches(12)&&!value.matches(0),state+" generation isolation");
        }
        check(RefreshFeedbackModel.begin("waiting",1,100,1).expiresAt==20100,"waiting duration bounded independently");
        check(RefreshFeedbackModel.begin("running",1,100,1).expiresAt==60100,"running duration bounded independently");
        check(RefreshFeedbackModel.duration(Integer.MIN_VALUE)==100,"lower duration clamp");
        check(RefreshFeedbackModel.duration(Integer.MAX_VALUE)==10000,"upper duration clamp");
        for(int duration:new int[]{100,200,1000,1500,10000})check(RefreshFeedbackModel.begin("success",1,1000,duration).expiresAt==1000+duration,"terminal duration "+duration);
        check(!RefreshFeedbackModel.begin("unexpected",1,100,1000).visible(100,true),"unknown state hidden");
        check(!RefreshFeedbackModel.begin("success",0,100,1000).visible(100,true),"zero request hidden");
        check(!RefreshFeedbackModel.begin("success",-1,100,1000).visible(100,true),"negative request hidden");
        check(!RefreshFeedbackModel.begin("success",1,-1,1000).visible(0,true),"negative clock rejected");
        check(!RefreshFeedbackModel.begin("success",1,Long.MAX_VALUE-99,1000).visible(Long.MAX_VALUE-99,true),"deadline overflow rejected");
        System.out.println("PASS: "+checks+" pure refresh feedback checks");
    }
}
