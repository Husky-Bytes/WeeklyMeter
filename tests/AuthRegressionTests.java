package dev.yerin.weeklymeter;

import android.content.Context;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.*;

/** All HTTPS URLs are intercepted in-process. No sockets, real accounts, or credentials. */
public final class AuthRegressionTests {
    private static final ArrayDeque<Response> responses=new ArrayDeque<>();
    private static final List<FakeConnection> requests=new ArrayList<>();
    private static Runnable onResponse;
    private static int checks;
    private static final String VERIFIER="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";
    private interface Checked {void run()throws Exception;}
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private static void rejects(Class<? extends Exception> kind,Checked action,String name)throws Exception{
        checks++;try{action.run();}catch(Exception e){if(kind.isInstance(e))return;throw e;}throw new AssertionError(name);
    }
    private static void reset(){Thread.interrupted();responses.clear();requests.clear();onResponse=null;Vault.state.clear();Vault.writeFailuresRemaining=0;Vault.writeAttempts=0;Store.values.data.clear();Store.onSave=null;}
    private static void respond(String path,int status,Object body,boolean interrupt){responses.add(new Response(path,status,Json.encode(body),interrupt));}
    private static Map<String,Object> tokens(String access,String refresh){return Json.map("access_token",access,"refresh_token",refresh,"expires_in",3600);}
    private static String repeat(char value,int length){char[] result=new char[length];Arrays.fill(result,value);return new String(result);}
    private static String jwt(Map<String,Object> claims){return "fixture."+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encode(claims).getBytes(StandardCharsets.UTF_8))+".fixture";}
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->{
            if(!"https".equals(protocol))return null;
            return new URLStreamHandler(){protected URLConnection openConnection(URL url){return new FakeConnection(url);}};
        });
        Context context=new Context();Repo repo=new Repo(context);

        reset();Thread.currentThread().interrupt();
        rejects(InterruptedException.class,()->Api.token(Json.map("grant_type","fixture")),"already-cancelled exchange never starts");
        check(requests.isEmpty(),"no request before interrupted exchange");Thread.interrupted();

        reset();respond("/oauth/token",200,tokens("fixture-access","fixture-refresh"),true);
        Map<String,Object> response=Api.token(Json.map("grant_type","fixture"));
        check(Json.string(response.get("refresh_token")).equals("fixture-refresh"),"read complete token body after cancellation");
        check(Thread.currentThread().isInterrupted(),"auth exchange preserves cancellation flag");Thread.interrupted();
        check(!requests.get(0).getInstanceFollowRedirects(),"redirect following disabled");
        check(requests.get(0).getRequestProperty("Authorization")==null,"auth request carries no bearer header");

        reset();Vault.state.put("auth",Json.map("access","fixture-old-access","refresh","fixture-old-refresh","account","fixture-account","expires",1));
        Store.values.data.put("connected",true);
        respond("/oauth/token",200,tokens("fixture-new-access","fixture-new-refresh"),true);
        rejects(InterruptedException.class,repo::sync,"cancelled sync stops before usage");
        Thread.interrupted();
        check(Json.string(Json.object(Vault.state.get("auth")).get("refresh")).equals("fixture-new-refresh"),"rotated refresh token committed despite cancellation");
        check(requests.size()==1,"cancelled refresh does not start usage request");
        check(Json.string(Json.object(Vault.state.get("auth")).get("account")).equals("fixture-account"),"refresh retains account when new claims absent");

        reset();Vault.state.put("auth",Json.map("access","fixture-old-access","refresh","fixture-old-refresh","account","fixture-account","expires",1));
        java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();
        respond("/oauth/token",200,tokens("fixture-new-access","fixture-new-refresh"),false);
        onResponse=()->stopped.set(true);
        check(repo.sync(stopped::get)==Repo.SyncOutcome.CANCELLED,"cooperative stop has explicit cancelled outcome");
        check(Json.string(Json.object(Vault.state.get("auth")).get("refresh")).equals("fixture-new-refresh"),"cooperative job stop still commits rotated token");
        check(requests.size()==1&&!Store.values.data.containsKey("saved_usage"),"stopped job performs no usage request or cache update");
        reset();check(repo.sync(()->true)==Repo.SyncOutcome.CANCELLED,"already-stopped outcome is cancelled");check(requests.isEmpty()&&Store.values.data.isEmpty(),"already-stopped job performs no work");

        reset();
        respond("/oauth/token",200,tokens("fixture-issued-access","fixture-issued-refresh"),true);
        repo.finishBrowserLogin("fixture-code",VERIFIER);Thread.interrupted();
        check(Store.connected(context),"browser-issued session committed after interrupted response");
        check(Json.string(Json.object(Vault.state.get("auth")).get("refresh")).equals("fixture-issued-refresh"),"initial refresh credential retained");
        String form=requests.get(0).body.toString("UTF-8");
        check(form.contains("code_verifier="+VERIFIER)&&form.contains("redirect_uri="+URLEncoder.encode(BrowserAuth.REDIRECT_URI,"UTF-8")),"PKCE verifier and fixed browser callback sent");
        check(form.contains("grant_type=authorization_code")&&form.contains("code=fixture-code")&&form.contains("client_id="+Api.CLIENT_ID),"browser code exchange has expected grant and public client ID");
        check(requests.size()==1,"browser login uses only token exchange");

        reset();Vault.writeFailuresRemaining=1;
        respond("/oauth/token",200,tokens("fixture-issued-access","fixture-issued-refresh"),false);
        repo.finishBrowserLogin("fixture-code",VERIFIER);
        check(Vault.writeAttempts==2&&Store.connected(context),"transient save failure retries issued credentials");
        check(requests.size()==1&&Json.string(Json.object(Vault.state.get("auth")).get("refresh")).equals("fixture-issued-refresh"),"save retry never repeats authorization code exchange");

        reset();
        for(String code:new String[]{null,"","has space","has\rreturn","has\nnewline","has\t tab","has\u007fdel","nonascii-한",repeat('a',4097)})
            rejects(IllegalArgumentException.class,()->repo.finishBrowserLogin(code,VERIFIER),"invalid browser authorization code rejected");
        for(String verifier:new String[]{null,"",repeat('a',42),repeat('a',129),repeat('a',42)+" ",repeat('a',42)+"/",repeat('a',42)+"+",repeat('a',42)+"한"})
            rejects(IllegalArgumentException.class,()->repo.finishBrowserLogin("fixture-code",verifier),"invalid browser PKCE verifier rejected");
        check(requests.isEmpty()&&!Vault.state.containsKey("auth"),"invalid inputs never reach HTTP or session storage");

        reset();Vault.state.put("auth",Json.map("access","fixture-existing-access","refresh","fixture-existing-refresh","account","fixture-account","expires",1));
        String existing=Json.encode(Vault.state);
        rejects(IllegalStateException.class,()->repo.finishBrowserLogin("fixture-code",VERIFIER),"existing encrypted session rejects replacement even when preference is false");
        check(requests.isEmpty()&&Json.encode(Vault.state).equals(existing),"existing session retained without network request");

        reset();respond("/oauth/token",429,Collections.emptyMap(),false);
        rejects(Api.HttpError.class,()->repo.finishBrowserLogin("fixture-code",VERIFIER),"browser token exchange handles server rate limit");
        check(Json.integer(Vault.state.get("login_retry_at"),0)>System.currentTimeMillis()+100_000,"browser login retains server retry delay");
        rejects(IllegalStateException.class,()->repo.finishBrowserLogin("fixture-code",VERIFIER),"immediate browser exchange retry blocked");
        check(requests.size()==1&&!Store.connected(context),"login backoff prevents second request and does not connect");

        reset();respond("/oauth/token",200,Json.map("access_token","fixture-access","expires_in",3600),false);
        rejects(IllegalArgumentException.class,()->repo.finishBrowserLogin("fixture-code",VERIFIER),"initial login without refresh token rejected");
        check(!Vault.state.containsKey("auth")&&!Store.connected(context),"missing initial refresh token never becomes connected");
        reset();respond("/oauth/token",200,Json.map("refresh_token","fixture-refresh","expires_in",3600),false);
        rejects(IllegalArgumentException.class,()->repo.finishBrowserLogin("fixture-code",VERIFIER),"initial login without access token rejected");
        check(!Vault.state.containsKey("auth")&&!Store.connected(context),"missing initial access token never becomes connected");

        reset();Vault.state.put("auth",Json.map("access","fixture-old-access","refresh","fixture-old-refresh","account","fixture-account","expires",1));
        respond("/oauth/token",200,Json.map("access_token","fixture-new-access","expires_in",3600),false);
        respond("/backend-api/wham/usage",200,Json.map("rate_limit",Json.map("secondary_window",Json.map("limit_window_seconds",604800,"used_percent",27,"reset_at",System.currentTimeMillis()/1000+604800))),false);
        repo.sync();
        check(Json.string(Json.object(Vault.state.get("auth")).get("refresh")).equals("fixture-old-refresh"),"omitted refresh token retains existing credential");
        check(requests.get(1).getRequestProperty("Authorization").equals("Bearer fixture-new-access"),"usage uses refreshed bearer");
        check(requests.get(1).getRequestProperty("ChatGPT-Account-ID").equals("fixture-account"),"usage binds account header");

        reset();Vault.state.put("auth",Json.map("access","fixture-old-access","refresh","fixture-old-refresh","account","fixture-account","expires",1));
        respond("/oauth/token",200,Json.map("refresh_token","fixture-rotated-refresh-only"),true);
        rejects(InterruptedException.class,repo::sync,"partial refresh response still honors later cancellation");Thread.interrupted();
        Map<String,Object> partial=Json.object(Vault.state.get("auth"));
        check(Json.string(partial.get("refresh")).equals("fixture-rotated-refresh-only"),"partial refresh persists rotated credential");
        check(Json.string(partial.get("access")).equals("fixture-old-access")&&Json.integer(partial.get("expires"),0)==1,"omitted access preserves prior token and expiry");

        reset();respond("/oauth/token",302,Collections.emptyMap(),false);
        rejects(Api.HttpError.class,()->Api.token(Json.map("grant_type","fixture")),"redirect response rejected");
        check(requests.size()==1,"redirect has no follow-up request");

        reset();
        Map<String,Object> bad=tokens("fixture-access","fixture-refresh");
        bad.put("id_token",jwt(Json.map("https://api.openai.com/auth",Json.map("chatgpt_account_id","account\r\nInjected: fake"))));
        respond("/oauth/token",200,bad,false);
        rejects(IllegalArgumentException.class,()->repo.finishBrowserLogin("fixture-code",VERIFIER),"invalid account header rejected before persistence");
        check(!Vault.state.containsKey("auth")&&!Store.connected(context),"invalid account never becomes connected");

        reset();Vault.state.put("auth",Json.map("access","fixture-old-access","refresh","fixture-old-refresh","account","fixture-account","expires",1));
        Store.values.data.put("connected",true);String beforeFailure=Json.encode(Vault.state);
        respond("/oauth/token",503,Collections.emptyMap(),false);
        rejects(Api.HttpError.class,repo::sync,"temporary refresh server failure reported");
        check(Json.encode(Vault.state).equals(beforeFailure)&&Store.connected(context),"temporary refresh failure retains saved session");

        reset();Vault.state.put("auth",Json.map("access","fixture-valid-access","refresh","fixture-valid-refresh","account","fixture-account","expires",System.currentTimeMillis()/1000+3600));
        Store.values.data.put("connected",true);String beforeUsageFailure=Json.encode(Vault.state);
        respond("/backend-api/wham/usage",503,Collections.emptyMap(),false);
        rejects(Api.HttpError.class,repo::sync,"temporary usage server failure reported");
        check(Json.encode(Vault.state).equals(beforeUsageFailure)&&Store.connected(context),"temporary usage failure retains saved session");

        reset();signedIn();
        Usage oldUsage=new Usage("codex","Codex",88,System.currentTimeMillis()/1000+604800,System.currentTimeMillis()-60000);
        Store.values.data.put("saved_usage",Collections.singletonList(oldUsage));
        long beforeFetch=System.currentTimeMillis();respondUsage(33,System.currentTimeMillis()/1000+604800);
        check(repo.sync()==Repo.SyncOutcome.UPDATED,"valid selected quota reports updated");
        Usage newUsage=savedUsage();
        check(newUsage.used==33&&newUsage.fetchedAt>=beforeFetch&&newUsage.fetchedAt<=System.currentTimeMillis(),"successful fetch saves actual receipt timestamp");
        check(newUsage.fetchedAt!=oldUsage.fetchedAt,"successful automatic or manual fetch advances cached timestamp");
        check(repo.sync()==Repo.SyncOutcome.SKIPPED&&requests.size()==1,"immediate throttle reports skipped without HTTP");
        check(savedUsage().fetchedAt==newUsage.fetchedAt,"throttled request does not invent a new timestamp");
        Usage unchangedPrevious=new Usage("codex","Codex",33,System.currentTimeMillis()/1000+604800,System.currentTimeMillis()-60000);
        Store.values.data.put("saved_usage",Collections.singletonList(unchangedPrevious));Store.values.data.put("attempt",System.currentTimeMillis()-11000);
        respondUsage(33,System.currentTimeMillis()/1000+604800);
        check(repo.sync()==Repo.SyncOutcome.UPDATED,"unchanged percentage still counts as genuinely updated response");
        check(savedUsage().used==unchangedPrevious.used&&savedUsage().fetchedAt>unchangedPrevious.fetchedAt,"unchanged percentage still advances actual fetched timestamp");

        reset();signedIn();Store.values.data.put("saved_usage",Collections.singletonList(oldUsage));
        java.util.concurrent.atomic.AtomicBoolean cancelUsage=new java.util.concurrent.atomic.AtomicBoolean();
        onResponse=()->cancelUsage.set(true);respondUsage(20,System.currentTimeMillis()/1000+604800);
        check(repo.sync(cancelUsage::get)==Repo.SyncOutcome.CANCELLED,"stop after usage response reports cancelled");
        check(savedUsage()==oldUsage,"cancelled response does not replace usage cache or timestamp");

        reset();signedIn();java.util.concurrent.atomic.AtomicBoolean cancelOnSave=new java.util.concurrent.atomic.AtomicBoolean();
        Store.onSave=()->cancelOnSave.set(true);respondUsage(25,System.currentTimeMillis()/1000+604800);
        check(repo.sync(cancelOnSave::get)==Repo.SyncOutcome.CANCELLED,"cancellation during cache commit cannot report success");
        check(savedUsage().used==25,"already committed real response is retained without inventing another timestamp");

        reset();signedIn();Store.values.data.put("selected","missing-bucket");respondUsage(20,System.currentTimeMillis()/1000+604800);
        rejects(IllegalStateException.class,repo::sync,"missing selected bucket is not successful refresh");
        check(Store.values.data.containsKey("error")&&savedUsage().id.equals("codex"),"missing selection retains authoritative other buckets and reports issue");

        reset();signedIn();Store.values.data.put("saved_usage",Collections.singletonList(oldUsage));respondUsage(20,System.currentTimeMillis()/1000-1);
        rejects(IllegalStateException.class,repo::sync,"already-reset selected data is not successful refresh");
        check(Store.values.data.containsKey("error"),"expired server data reports error instead of success");
        check(savedUsage()==oldUsage,"expired selected response cannot overwrite last genuine timestamp");

        reset();signedIn();Store.values.data.put("saved_usage",Collections.singletonList(oldUsage));respond("/backend-api/wham/usage",200,Json.map("rateLimitsByLimitId",Collections.emptyMap()),false);
        rejects(IllegalArgumentException.class,repo::sync,"empty quota response is not successful refresh");
        check(savedUsage()==oldUsage,"invalid empty quota response leaves last genuine timestamp unchanged");

        reset();signedIn();Store.values.data.put("saved_usage",Collections.singletonList(oldUsage));respond("/backend-api/wham/usage",503,Collections.emptyMap(),false);
        rejects(Api.HttpError.class,repo::sync,"failed request has no updated outcome");
        check(savedUsage()==oldUsage,"server failure preserves real previous timestamp");

        reset();Vault.state.put("auth",Json.map("access","fixture-saved-access","refresh","fixture-saved-refresh","account","fixture-account","expires",1));
        check(repo.reconcileConnection(),"encrypted session recovers false connected preference");
        check(Store.connected(context)&&requests.isEmpty(),"connection recovery is local and accepts refreshable expired access");
        Store.values.data.put("connected",false);
        check(new Repo(context).reconcileConnection()&&Store.connected(context),"new repository instance recovers saved session after process-like restart");
        reset();Store.values.data.put("connected",true);
        check(!repo.reconcileConnection()&&!Store.connected(context),"missing encrypted auth repairs stale true preference");
        check(Vault.state.isEmpty()&&requests.isEmpty(),"signed-out reconciliation creates no credentials or network request");

        reset();Vault.state.put("auth",Json.map("access","fixture-access","refresh","fixture-refresh"));Store.values.data.put("connected",true);
        repo.disconnect();check(Vault.state.isEmpty()&&Store.values.data.isEmpty(),"disconnect removes local session and display data");
        check(responses.isEmpty(),"all fake responses consumed");
        System.out.println("PASS: "+checks+" auth regression checks (fake HTTPS and storage; Android Keystore and real login NOT tested).");
    }
    private static void signedIn(){Vault.state.put("auth",Json.map("access","fixture-access","refresh","fixture-refresh","expires",System.currentTimeMillis()/1000+3600));Store.values.data.put("connected",true);}
    private static void respondUsage(int used,long reset){respond("/backend-api/wham/usage",200,Json.map("rate_limit",Json.map("secondary_window",Json.map("limit_window_seconds",604800,"used_percent",used,"reset_at",reset))),false);}
    private static Usage savedUsage(){return (Usage)((List<?>)Store.values.data.get("saved_usage")).get(0);}
    private static final class Response {
        final String path,body;final int status;final boolean interrupt;
        Response(String path,int status,String body,boolean interrupt){this.path=path;this.status=status;this.body=body;this.interrupt=interrupt;}
    }
    private static final class FakeConnection extends HttpsURLConnection {
        final ByteArrayOutputStream body=new ByteArrayOutputStream();Response response;
        FakeConnection(URL url){super(url);requests.add(this);}
        @Override public int getResponseCode(){
            if(responses.isEmpty())throw new AssertionError("Unexpected fake request: "+url.getPath());
            response=responses.remove();
            if(!url.getPath().equals(response.path))throw new AssertionError("Unexpected fake path: "+url.getPath());
            if(onResponse!=null){Runnable callback=onResponse;onResponse=null;callback.run();}
            if(response.interrupt)Thread.currentThread().interrupt();return response.status;
        }
        @Override public OutputStream getOutputStream(){return body;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(response.body.getBytes(StandardCharsets.UTF_8));}
        @Override public String getContentType(){return "application/json";}
        @Override public String getHeaderField(String name){return "Retry-After".equals(name)?"120":null;}
        @Override public void connect(){}
        @Override public void disconnect(){}
        @Override public boolean usingProxy(){return false;}
        @Override public String getCipherSuite(){return "fake-test";}
        @Override public Certificate[] getLocalCertificates(){return null;}
        @Override public Certificate[] getServerCertificates(){return new Certificate[0];}
    }
}
