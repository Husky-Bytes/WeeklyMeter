package dev.yerin.weeklymeter;

/** Process-local home-widget gesture; never persists a pending open across restart. */
final class WidgetTapSequence {
    static final long WINDOW_MS=900, QUIET_MS=900;
    enum Action { REFRESH, SUPPRESS, SHOW_FLOATING }
    private long firstAt=-1,lastAt=-1;
    private int count,widgetId;
    private boolean cooling;

    synchronized Action tap(int id,long now){
        // elapsedRealtime should be monotonic. A reset or invalid clock must
        // never complete a gesture started in another time base.
        if(now<0){reset();return Action.REFRESH;}
        if(lastAt>=0&&now<lastAt)reset();
        if(cooling){
            if(now-lastAt<=QUIET_MS){lastAt=now;return Action.SUPPRESS;}
            reset();
        }
        if(count==0||widgetId!=id||now-firstAt>WINDOW_MS){
            widgetId=id;firstAt=lastAt=now;count=1;return Action.REFRESH;
        }
        lastAt=now;
        if(++count==3){count=0;cooling=true;return Action.SHOW_FLOATING;}
        return Action.SUPPRESS;
    }

    synchronized void reset(){firstAt=lastAt=-1;count=0;cooling=false;}
}
