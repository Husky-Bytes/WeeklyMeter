package dev.yerin.weeklymeter;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual Api, fake transport: no sockets, accounts, secrets, or Android platform assumptions. */
public final class ApiDeadlineTests {
    private static int checks;
    private static volatile Fake latest;
    private static volatile String mode="normal";
    private static volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    private static final AtomicInteger opens=new AtomicInteger();
    private interface Call {Object run()throws Exception;}
    private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private static Object invoke(String name,Class<?>[] types,Object[] args,long millis)throws Exception{
        // The fallback permits a true red run against the pre-fix Api, without modifying it.
        Method method;Object[] actual;
        try{Class<?>[] timed=Arrays.copyOf(types,types.length+1);timed[types.length]=long.class;method=Api.class.getDeclaredMethod(name,timed);actual=Arrays.copyOf(args,args.length+1);actual[args.length]=millis;}
        catch(NoSuchMethodException old){method=Api.class.getDeclaredMethod(name,types);actual=args;}
        method.setAccessible(true);
        try{return method.invoke(null,actual);}catch(InvocationTargetException wrapped){Throwable cause=wrapped.getCause();if(cause instanceof Exception)throw (Exception)cause;throw (Error)cause;}
    }
    private static String usage(long millis)throws Exception{return (String)invoke("usage",new Class<?>[]{String.class,String.class},new Object[]{"synthetic-access",""},millis);}
    private static Map<String,Object> token(long millis)throws Exception{return Json.object(invoke("token",new Class<?>[]{Map.class},new Object[]{Json.map("grant_type","synthetic")},millis));}
    private static Exception timeout(Call call,boolean ambiguous)throws Exception{
        long before=System.nanoTime();Exception failure=null;
        try{call.run();}catch(Exception e){failure=e;}
        check(failure!=null&&failure.getClass().getSimpleName().equals("RequestTimeout"),"whole request times out instead of finishing a slow stream");
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<1500,"caller deadline releases without waiting for the transport");
        Field sent=failure.getClass().getDeclaredField("authMayHaveChanged");sent.setAccessible(true);
        check(sent.getBoolean(failure)==ambiguous,"only actually attempted auth body is ambiguous");return failure;
    }
    private static boolean idle()throws Exception{
        try{Method method=Api.class.getDeclaredMethod("transportIdle");method.setAccessible(true);return (Boolean)method.invoke(null);}catch(NoSuchMethodException old){return true;}
    }
    private static void awaitIdle()throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!idle()&&System.nanoTime()<until)Thread.yield();check(idle(),"finished transport releases single-worker guard");
    }
    private static void setup(String next)throws Exception{release.countDown();awaitIdle();Thread.interrupted();mode=next;entered=new CountDownLatch(1);release=new CountDownLatch(1);latest=null;}
    private static void atomicBodyBoundary()throws Exception{
        Class<?> exchange=Class.forName("dev.yerin.weeklymeter.Api$Exchange");
        Constructor<?> constructor=exchange.getDeclaredConstructor(String.class,String.class,String.class,String.class,String.class,String.class,long.class);constructor.setAccessible(true);
        Method begin=exchange.getDeclaredMethod("beginBody"),abandon=exchange.getDeclaredMethod("abandon");begin.setAccessible(true);abandon.setAccessible(true);
        Field sent=exchange.getDeclaredField("authSent");sent.setAccessible(true);
        check(Modifier.isSynchronized(begin.getModifiers())&&Modifier.isSynchronized(abandon.getModifiers()),"body-start decision and abandonment share one monitor");
        Object before=constructor.newInstance("POST",NetworkPolicy.AUTH+"/oauth/token","x","application/json",null,null,1000L);abandon.invoke(before);
        boolean refused=false;try{begin.invoke(before);}catch(InvocationTargetException expected){refused=expected.getCause().getClass().getSimpleName().equals("RequestTimeout");}
        check(refused&&!sent.getBoolean(before),"abandon before begin prevents any auth write attempt");
        Object after=constructor.newInstance("POST",NetworkPolicy.AUTH+"/oauth/token","x","application/json",null,null,1000L);begin.invoke(after);abandon.invoke(after);
        check(sent.getBoolean(after),"begin before abandon always records potentially sent auth");
    }
    private static void block(){entered.countDown();boolean interrupted=false;for(;;){try{release.await();break;}catch(InterruptedException ignored){interrupted=true;}}if(interrupted)Thread.currentThread().interrupt();}
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url){Fake c=new Fake(url,mode);latest=c;opens.incrementAndGet();return c;}}:null);
        try{
            // Initialize the desktop TLS classes before testing sub-second synthetic budgets.
            setup("normal");check(usage(30_000).equals("{}"),"warm synthetic transport performs no real network");awaitIdle();
            setup("drip");timeout(()->usage(75),false);awaitIdle();check(latest.reads<64,"slow drip is stopped before body completion");
            check(latest.maximumReadTimeout<=75,"socket read timeout also respects remaining total budget");
            atomicBodyBoundary();

            setup("block-response");timeout(()->usage(75),false);int before=opens.get();long started=System.nanoTime();
            for(int i=0;i<20;i++){
                Exception failure=null;try{usage(75);}catch(Exception e){failure=e;}
                check(failure!=null&&failure.getClass().getSimpleName().equals("TransportBusy"),"stuck transport rejects another request without queueing");
            }
            check(opens.get()==before&&TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<1000,"blocked calls create no new transport or growing request queue");
            release.countDown();awaitIdle();setup("normal");check(usage(500).equals("{}"),"transport recovers after original worker drains");

            setup("block-connect");timeout(()->token(75),false);release.countDown();awaitIdle();check(latest.writes==0,"timeout during connect sends no auth body");
            setup("block-response");timeout(()->token(75),true);check(latest.writes==1,"post-send timeout attempts body exactly once");release.countDown();awaitIdle();

            setup("block-close");Map<String,Object> completed=token(75);
            check(Json.string(completed.get("refresh_token")).equals("synthetic-new-refresh"),"complete rotating response survives deadline while stream close is stuck");
            check(!idle(),"complete response can be saved while old close still drains");release.countDown();awaitIdle();

            setup("block-response");Thread caller=Thread.currentThread();Thread cancel=new Thread(()->{try{entered.await();caller.interrupt();release.countDown();}catch(InterruptedException ignored){}});cancel.setDaemon(true);cancel.start();
            completed=token(1000);check(Thread.currentThread().isInterrupted(),"auth preserves caller cancellation after complete result");Thread.interrupted();
            check(Json.string(completed.get("refresh_token")).equals("synthetic-new-refresh"),"caller cancellation never discards completed rotation");awaitIdle();

            setup("block-response");Thread cancelGet=new Thread(()->{try{entered.await();caller.interrupt();}catch(InterruptedException ignored){}});cancelGet.setDaemon(true);cancelGet.start();
            boolean cancelled=false;try{usage(1000);}catch(InterruptedException expected){cancelled=true;}
            check(cancelled,"usage cancellation releases its caller promptly");Thread.interrupted();release.countDown();awaitIdle();
            setup("normal");check(usage(500).equals("{}"),"request after cancellation works without stale cancellation state");
            System.out.println("PASS: "+checks+" HTTP deadline/worker checks (fake transport; no actual network or Android device).");
        }finally{release.countDown();Thread.interrupted();}
    }
    private static final class Fake extends HttpsURLConnection {
        final String scenario;volatile int writes,reads,maximumReadTimeout;
        Fake(URL url,String scenario){super(url);this.scenario=scenario;}
        @Override public OutputStream getOutputStream(){if(scenario.equals("block-connect"))block();return new ByteArrayOutputStream(){@Override public void write(byte[] b,int o,int n){writes++;super.write(b,o,n);}};}
        @Override public int getResponseCode(){if(scenario.equals("block-response"))block();return 200;}
        @Override public InputStream getInputStream(){
            String body=url.getPath().equals("/oauth/token")?"{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-new-refresh\"}":"{}";
            if(scenario.equals("drip")){char[] spaces=new char[64];Arrays.fill(spaces,' ');body=new String(spaces)+body;}
            final byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            return new ByteArrayInputStream(bytes){
                @Override public synchronized int read(byte[] b,int off,int len){reads++;if(scenario.equals("drip")){try{Thread.sleep(15);}catch(InterruptedException ignored){}return super.read(b,off,Math.min(1,len));}return super.read(b,off,len);}
                @Override public void close(){if(scenario.equals("block-close"))block();}
            };
        }
        @Override public void setReadTimeout(int timeout){super.setReadTimeout(timeout);maximumReadTimeout=Math.max(maximumReadTimeout,timeout);}
        @Override public String getContentType(){return "application/json";}
        @Override public String getHeaderField(String name){return null;}
        @Override public void connect(){}
        @Override public void disconnect(){} // Deliberately does not unblock a simulated broken platform call.
        @Override public boolean usingProxy(){return false;}
        @Override public String getCipherSuite(){return "synthetic";}
        @Override public Certificate[] getLocalCertificates(){return null;}
        @Override public Certificate[] getServerCertificates(){return new Certificate[0];}
    }
}
