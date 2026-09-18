package dev.yerin.weeklymeter;
import android.content.Context;
final class WeeklyWidget {
    static volatile int renders,attempts,percent;
    static volatile long fetchedAt;
    static final java.util.concurrent.atomic.AtomicInteger failures=new java.util.concurrent.atomic.AtomicInteger();
    static void renderAll(Context c){
        attempts++;
        if(failures.getAndUpdate(value->Math.max(0,value-1))>0)throw new IllegalStateException("Synthetic launcher failure");
        percent=Store.percent;fetchedAt=Store.fetchedAt;renders++;
    }
}
