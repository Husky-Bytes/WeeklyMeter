package dev.yerin.weeklymeter;

import java.util.concurrent.CopyOnWriteArraySet;

/** In-process state notification only. Observers own their UI dispatch and lifecycle. */
final class AppSignals {
    private static final CopyOnWriteArraySet<Runnable> observers=new CopyOnWriteArraySet<>();
    static void register(Runnable observer){if(observer!=null)observers.add(observer);}
    static void unregister(Runnable observer){observers.remove(observer);}
    static void changed(){
        for(Runnable observer:observers)try{observer.run();}catch(RuntimeException unavailable){
            // A screen observer must not break service or credential completion.
        }
    }
    private AppSignals(){}
}
