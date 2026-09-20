package dev.yerin.weeklymeter;

/** One-shot local display deadlines, never a network refresh or exact alarm. */
final class DisplayExpiry {
    private static final long WEEK_MILLIS=604800_000L,STALE_MILLIS=1800_000L;
    static long nextDelay(long nowMillis,long fetchedAt,long resetsAtSeconds){
        if(nowMillis<0||fetchedAt<0||nowMillis<fetchedAt)return -1;
        long age=nowMillis-fetchedAt;
        if(age>=WEEK_MILLIS||(resetsAtSeconds>0&&nowMillis/1000>=resetsAtSeconds))return -1;
        long delay=WEEK_MILLIS-age;
        if(resetsAtSeconds>0&&resetsAtSeconds<=Long.MAX_VALUE/1000)
            delay=Math.min(delay,resetsAtSeconds*1000-nowMillis);
        // Display.state changes at strictly more than thirty minutes.
        if(age<=STALE_MILLIS)delay=Math.min(delay,STALE_MILLIS-age+1);
        return Math.max(1,delay);
    }
    private DisplayExpiry(){}
}
