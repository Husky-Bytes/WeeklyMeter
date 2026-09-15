package android.content;
public abstract class BroadcastReceiver {
    public static int asyncCalls,finishes;
    public abstract void onReceive(Context context,Intent intent);
    public final PendingResult goAsync(){asyncCalls++;return new PendingResult();}
    public static class PendingResult {public void finish(){finishes++;}}
}
