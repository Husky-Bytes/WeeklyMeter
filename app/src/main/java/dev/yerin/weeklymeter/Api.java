package dev.yerin.weeklymeter;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** No telemetry, redirects, custom trust managers, or model-generation requests. */
final class Api {
    static final String CLIENT_ID="app_EMoamEEZ73f0CkXaXp7hrann"; // Public Codex client ID, NOT an API key.
    static final long REQUEST_TIMEOUT_MILLIS=30_000;
    private static final AtomicBoolean TRANSPORT_BUSY=new AtomicBoolean();
    private static final ThreadPoolExecutor TRANSPORT=worker("weeklymeter-http"),ABORT=worker("weeklymeter-http-close");
    static final class HttpError extends IOException {
        final int status; final long retrySeconds; final String errorCode;
        HttpError(int status,long retry){this(status,retry,"");}
        HttpError(int status,long retry,String code){super("서버 응답 HTTP "+status);this.status=status;this.retrySeconds=retry;this.errorCode=code;}
        boolean invalidCredentials(){return !errorCode.isEmpty();}
    }
    static final class RequestTimeout extends SocketTimeoutException {
        final boolean authMayHaveChanged;
        RequestTimeout(boolean sent){super("HTTP request deadline exceeded");authMayHaveChanged=sent;}
    }
    static final class AuthExchangeUncertain extends IOException {AuthExchangeUncertain(){super("Authentication response incomplete");}}
    static final class TransportBusy extends IOException {TransportBusy(){super("Previous HTTP transport is still stopping");}}
    private static ThreadPoolExecutor worker(String name){
        ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);return executor;
    }
    static boolean transportIdle(){return !TRANSPORT_BUSY.get();}
    static Map<String,Object> postJson(String path,Map<String,Object> body)throws Exception{
        return Json.object(Json.parse(request("POST",NetworkPolicy.AUTH+path,Json.encode(body),"application/json",null,null)));
    }
    static Map<String,Object> token(Map<String,Object> form)throws Exception{
        return token(form,REQUEST_TIMEOUT_MILLIS);
    }
    // Package-visible bounded-duration seam; production always uses thirty seconds.
    static Map<String,Object> token(Map<String,Object> form,long timeoutMillis)throws Exception{
        StringBuilder body=new StringBuilder();
        for(Map.Entry<String,Object> e:form.entrySet()){
            if(body.length()>0)body.append('&');
            body.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(String.valueOf(e.getValue()),"UTF-8"));
        }
        return Json.object(Json.parse(request("POST",NetworkPolicy.AUTH+"/oauth/token",body.toString(),"application/x-www-form-urlencoded",null,null,timeoutMillis)));
    }
    static String usage(String access,String account)throws Exception{
        return request("GET",NetworkPolicy.USAGE,null,null,access,account);
    }
    static String usage(String access,String account,long timeoutMillis)throws Exception{return request("GET",NetworkPolicy.USAGE,null,null,access,account,timeoutMillis);}
    private static String request(String method,String address,String body,String type,String token,String account)throws Exception{
        return request(method,address,body,type,token,account,REQUEST_TIMEOUT_MILLIS);
    }
    private static String request(String method,String address,String body,String type,String token,String account,long timeoutMillis)throws Exception{
        interrupted();NetworkPolicy.check(method,address,token!=null);
        if(timeoutMillis<=0||timeoutMillis>REQUEST_TIMEOUT_MILLIS)throw new IllegalArgumentException("Invalid HTTP deadline");
        if(token!=null&&!NetworkPolicy.headerSafe(token))throw new SecurityException("인증정보 형식 오류");
        if(account!=null&&!account.isEmpty()&&!NetworkPolicy.headerSafe(account))throw new SecurityException("계정 정보 형식 오류");
        if(!TRANSPORT_BUSY.compareAndSet(false,true))throw new TransportBusy();
        Exchange exchange=new Exchange(method,address,body,type,token,account,timeoutMillis);
        Future<String> future;
        try{future=TRANSPORT.submit(()->{try{return exchange.perform();}finally{TRANSPORT_BUSY.set(false);}});}
        catch(RuntimeException rejected){TRANSPORT_BUSY.set(false);throw new TransportBusy();}
        boolean restoreInterrupt=false;
        try{
            for(;;){
                try{return future.get(Math.max(1,exchange.remainingNanos()),TimeUnit.NANOSECONDS);}
                catch(InterruptedException cancelled){
                    // Preserve a complete rotating-token response even if the caller was cancelled.
                    if(exchange.auth){restoreInterrupt=true;continue;}
                    exchange.abandon();exchange.abort();throw cancelled;
                }catch(TimeoutException timeout){
                    // A response completed at the deadline must not be discarded while close() drains.
                    String complete=exchange.abandon();if(complete!=null)return complete;
                    exchange.abort();throw new RequestTimeout(exchange.authSent);
                }catch(ExecutionException failed){
                    String complete=exchange.completed();if(complete!=null)return complete;
                    Throwable cause=failed.getCause();
                    if(cause instanceof RequestTimeout)throw (RequestTimeout)cause;
                    if(cause instanceof IOException&&!(cause instanceof HttpError)&&exchange.authSent)throw new AuthExchangeUncertain();
                    if(cause instanceof Exception)throw (Exception)cause;
                    throw new IOException("HTTP transport failed");
                }
            }
        }finally{if(restoreInterrupt)Thread.currentThread().interrupt();}
    }
    private static final class Exchange {
        final String method,address,body,type,token,account;final boolean auth;final long deadline;
        volatile HttpsURLConnection connection;volatile boolean authSent,abandoned;
        private String complete;
        Exchange(String method,String address,String body,String type,String token,String account,long millis){
            this.method=method;this.address=address;this.body=body;this.type=type;this.token=token;this.account=account;auth="POST".equals(method);deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);
        }
        long remainingNanos(){return deadline-System.nanoTime();}
        synchronized String completed(){return complete;}
        synchronized String abandon(){if(complete!=null)return complete;abandoned=true;return null;}
        synchronized String finish(String value)throws RequestTimeout{if(abandoned)throw new RequestTimeout(authSent);complete=value;return value;}
        synchronized void beginBody()throws RequestTimeout{timeout(12000);authSent=auth;}
        int timeout(int maximum)throws RequestTimeout{
            long left=remainingNanos();if(abandoned||left<=0)throw new RequestTimeout(authSent);
            return (int)Math.max(1,Math.min(maximum,TimeUnit.NANOSECONDS.toMillis(left)));
        }
        void abort(){
            // disconnect() itself is platform I/O. Never put it back on the serial credential executor.
            try{ABORT.execute(()->{HttpsURLConnection c=connection;if(c!=null)try{c.disconnect();}catch(RuntimeException ignored){}});}catch(RejectedExecutionException ignored){}
        }
        String perform()throws Exception{
            timeout(8000);HttpsURLConnection c=(HttpsURLConnection)new URL(address).openConnection();connection=c;
            try{
                c.setInstanceFollowRedirects(false);c.setConnectTimeout(timeout(8000));c.setReadTimeout(timeout(12000));c.setUseCaches(false);
                c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Cache-Control","no-cache");
                c.setRequestProperty("User-Agent","WeeklyMeter (Android; personal usage widget)");
                if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
                if(account!=null&&!account.isEmpty())c.setRequestProperty("ChatGPT-Account-ID",account);
                if(body!=null){
                    c.setDoOutput(true);c.setRequestProperty("Content-Type",type);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
                    try(OutputStream out=c.getOutputStream()){beginBody();out.write(bytes);}
                }
                c.setReadTimeout(timeout(12000));int status=c.getResponseCode();timeout(12000);
                if(status<200||status>=300){
                    long retry=60;try{retry=Math.max(30,Math.min(86400,Long.parseLong(c.getHeaderField("Retry-After"))));}catch(Exception ignored){}
                    String code="";
                    if(auth&&(status==400||status==401)){
                        // Persist only an exact allowlisted code, never descriptions or arbitrary bodies.
                        try(InputStream error=c.getErrorStream()){if(error!=null)code=credentialError(read(c,error,16_384));}
                        catch(IOException|IllegalArgumentException invalid){if(abandoned||remainingNanos()<=0)throw new RequestTimeout(authSent);}
                    }
                    throw new HttpError(status,retry,code);
                }
                String ct=c.getContentType();
                if(ct==null||!ct.toLowerCase(Locale.ROOT).contains("json"))throw new IOException("JSON 대신 로그인·보안 확인 화면이 반환됐어. 공식 화면에서 상태를 확인해 줘.");
                try(InputStream in=c.getInputStream()){return finish(read(c,in,1_048_576));}
            }finally{try{c.disconnect();}catch(RuntimeException ignored){}}
        }
        String read(HttpsURLConnection c,InputStream in,int limit)throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            for(;;){c.setReadTimeout(timeout(12000));n=in.read(buffer);timeout(12000);if(n==-1)break;if(out.size()+n>limit)throw new IOException("서버 응답이 너무 커.");out.write(buffer,0,n);}
            return out.toString("UTF-8");
        }
    }
    private static String credentialError(String body){
        Map<String,Object> value=Json.object(Json.parse(body));Object error=value.get("error");
        String code=error instanceof String?(String)error:Json.string(Json.object(error).get("code"));
        switch(code){
            case "invalid_grant":case "refresh_token_expired":case "refresh_token_reused":case "refresh_token_invalidated":return code;
            default:return "";
            }
    }
    static void interrupted()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private Api(){}
}
