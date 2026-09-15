package android.os;
public final class SystemClock {
    private static final java.util.concurrent.atomic.AtomicLong SEQUENCE = new java.util.concurrent.atomic.AtomicLong(100);
    public static long elapsedRealtimeNanos() { return SEQUENCE.incrementAndGet(); }
}
