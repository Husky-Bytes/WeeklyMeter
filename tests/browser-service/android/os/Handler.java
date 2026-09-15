package android.os;
public class Handler {
    public static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> MAIN = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable action) { MAIN.add(action); return true; }
    public static void drain() { Runnable action; while ((action=MAIN.poll()) != null) action.run(); }
}
