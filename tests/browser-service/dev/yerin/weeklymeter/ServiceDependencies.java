package dev.yerin.weeklymeter;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class MainActivity { }
final class Texts { static java.util.Locale locale(android.content.Context c){return java.util.Locale.KOREAN;} }
final class R { static final class drawable { static final int ic_meter=1; } }
final class Scheduler { static void ensure(android.content.Context context) { } }
final class WeeklyWidget { static void renderAll(android.content.Context context) { } }
final class Repo {
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    static final AtomicInteger exchanges=new AtomicInteger(),syncs=new AtomicInteger();
    static volatile boolean saved,syncSawSaved;
    static volatile CountDownLatch exchanged=new CountDownLatch(1), releaseExchange=new CountDownLatch(0);
    Repo(android.content.Context context) { }
    void finishBrowserLogin(String code,String verifier)throws Exception { exchanges.incrementAndGet();exchanged.countDown();releaseExchange.await(3,TimeUnit.SECONDS);saved=true; }
    void sync() { syncSawSaved=saved;syncs.incrementAndGet(); }
    static String friendly(Exception error) { return "Synthetic login failure"; }
    static void reset() throws Exception { IO.submit(()->{}).get(3,TimeUnit.SECONDS);exchanges.set(0);syncs.set(0);saved=false;syncSawSaved=false;exchanged=new CountDownLatch(1);releaseExchange=new CountDownLatch(0); }
}
final class BrowserAuth {
    static volatile Session next;
    static volatile CountDownLatch bindEntered,releaseBind;
    static void reset() { next=new Session();bindEntered=new CountDownLatch(1);releaseBind=new CountDownLatch(0); }
    static Session bind() throws IOException {
        Session session=next;bindEntered.countDown();
        boolean interrupted=false;
        while (true) try { releaseBind.await(3,TimeUnit.SECONDS);break; } catch (InterruptedException ignore) { interrupted=true; }
        if(interrupted)Thread.currentThread().interrupt();return session;
    }
    static Session bind(java.util.Locale locale) throws IOException { return bind(); }
    static final class Result {
        final String code;final boolean denied;
        Result(String code,boolean denied){this.code=code;this.denied=denied;}
    }
    static final class Session {
        final CountDownLatch waiting=new CountDownLatch(1),answer=new CountDownLatch(1);
        volatile boolean closed;
        volatile Result result;
        String authorizeUrl() { if(closed)throw new IllegalStateException("Closed");return "https://auth.openai.com/oauth/authorize?state=synthetic"; }
        String verifier() { return "synthetic-verifier"; }
        Result awaitCallback()throws IOException {
            waiting.countDown();try { if(!answer.await(3,TimeUnit.SECONDS))throw new IOException("Synthetic timeout"); }
            catch(InterruptedException e){throw new IOException("Synthetic interrupted");}
            if(closed)throw new IOException("Synthetic closed");return result;
        }
        void complete(boolean denied){result=new Result(denied?null:"synthetic-code",denied);answer.countDown();}
        void close(){closed=true;answer.countDown();}
    }
}
