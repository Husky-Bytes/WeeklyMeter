package dev.yerin.weeklymeter;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** One serial executor prevents refresh-token rotation races and logout/write races. */
final class Repo {
    enum SyncOutcome { UPDATED, SKIPPED, CANCELLED, SIGNED_OUT }
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final String REAUTH="reauth_required",REAUTH_REASON="reauth_reason",SESSION_ID="session_id";
    static final String REAUTH_MESSAGE="로그인 권한이 만료되었거나 철회됐어. 앱에서 다시 로그인해 줘.";
    static final String AUTH_UNCERTAIN_MESSAGE="로그인 확인이 필요해. 인증 응답을 끝까지 확인하지 못했어. 저장된 정보는 유지했으니 앱에서 다시 로그인해 줘.";
    private final Context c;
    private final Vault vault;
    Repo(Context context){c=context.getApplicationContext();vault=new Vault(c);}
    static boolean reauthenticationRequired(Context context){return Store.prefs(context).getBoolean(REAUTH,false);}
    // SharedPreferences is only a display cache; the encrypted session is authoritative.
    // Call on IO at app/widget/boot recovery, including after process death between writes.
    boolean reconcileConnection()throws Exception{
        return publishConnection(vault.read());
    }
    private boolean needsReauthentication(Map<String,Object> state){
        String id=Json.string(state.get(SESSION_ID));
        boolean newSession=!id.isEmpty()&&!id.equals(Store.prefs(c).getString(SESSION_ID,""));
        return Boolean.TRUE.equals(state.get(REAUTH))||(!newSession&&reauthenticationRequired(c));
    }
    private boolean publishConnection(Map<String,Object> state){
        String id=Json.string(state.get(SESSION_ID));boolean changed=!id.isEmpty()&&!id.equals(Store.prefs(c).getString(SESSION_ID,""));
        boolean reauth=needsReauthentication(state);
        boolean connected=NetworkPolicy.headerSafe(Json.string(Json.object(state.get("auth")).get("access")))&&!reauth;
        String reason=Json.string(state.get(REAUTH_REASON));if(reauth&&reason.isEmpty())reason=Store.prefs(c).getString(REAUTH_REASON,"");
        if(changed)Store.prefs(c).edit().remove("meters").remove("attempt").remove("backoff").putString("selected","codex").putString("error","").apply();
        Store.prefs(c).edit().putString(SESSION_ID,id).putBoolean("connected",connected).putBoolean(REAUTH,reauth).putString(REAUTH_REASON,reason).apply();
        return connected;
    }
    private void requireUsableSession(Map<String,Object> state){
        String reason=Json.string(state.get(REAUTH_REASON));if(reason.isEmpty())reason=Store.prefs(c).getString(REAUTH_REASON,"");
        if(needsReauthentication(state))throw new IllegalStateException("uncertain".equals(reason)?AUTH_UNCERTAIN_MESSAGE:REAUTH_MESSAGE);
    }
    private void quarantine(String reason){
        // A separate durable guard also protects against a failed credential-file replacement.
        // A false commit still leaves the in-process guard set; unavailable storage cannot be guaranteed.
        Store.prefs(c).edit().putBoolean(REAUTH,true).putBoolean("connected",false).putString(REAUTH_REASON,reason).commit();
    }
    private void markReauthentication(Map<String,Object> state,String reason)throws Exception{
        // Keep credentials, but never automatically reuse a definitively invalid or ambiguous grant.
        state.put(REAUTH,true);state.put(REAUTH_REASON,reason);
        quarantine(reason);
        saveCredentials(state);
    }
    void finishBrowserLogin(String code,String verifier)throws Exception{
        if(code==null||code.isEmpty()||code.length()>4096||!code.chars().allMatch(ch->ch>=33&&ch<=126)
            ||verifier==null||!verifier.matches("[A-Za-z0-9._~-]{43,128}"))throw new IllegalArgumentException("로그인 응답을 확인할 수 없어. 다시 시작해 줘.");
        Map<String,Object> state=vault.read();
        if(!Json.object(state.get("auth")).isEmpty()&&!needsReauthentication(state))throw new IllegalStateException("이미 연결된 계정이 있어.");
        requireLoginReady(state);
        Map<String,Object> tokens;
        try{tokens=Api.token(Json.map("grant_type","authorization_code","client_id",Api.CLIENT_ID,"code",code,"code_verifier",verifier,"redirect_uri",BrowserAuth.REDIRECT_URI));}
        catch(Api.HttpError e){rememberLoginBackoff(state,e);throw e;}
        Map<String,Object> auth=normalize(tokens,Collections.emptyMap());
        if(Json.string(auth.get("refresh")).isEmpty())throw new IllegalArgumentException("로그인 유지용 인증정보가 발급되지 않았어. 다시 로그인해 줘.");
        // Issued credentials must be durably saved before any later cancellation check.
        state.remove("pending");state.remove("login_retry_at");state.remove(REAUTH);state.remove(REAUTH_REASON);
        state.put("auth",auth);state.put(SESSION_ID,UUID.randomUUID().toString());saveCredentials(state);
        // The encrypted generation also repairs a crash between this atomic write and display reset.
        publishConnection(state);
    }
    private void saveCredentials(Map<String,Object> state)throws Exception{
        try{vault.write(state);}catch(Exception first){
            // Retry the SAME new credentials, never repeat a token rotation request.
            try{vault.write(state);}catch(Exception second){throw second;}
        }
    }
    private static void requireLoginReady(Map<String,Object> state){
        if(Json.integer(state.get("login_retry_at"),0)>System.currentTimeMillis())
            throw new IllegalStateException("서버가 로그인 재시도 대기를 요청했어. 잠시 후 다시 확인해 줘.");
    }
    private void rememberLoginBackoff(Map<String,Object> state,Api.HttpError error)throws Exception{
        if(error.status==429){state.put("login_retry_at",System.currentTimeMillis()+error.retrySeconds*1000);vault.write(state);}
    }
    private static Map<String,Object> normalize(Map<String,Object> response,Map<String,Object> previous)throws Exception{
        String access=Json.string(response.get("access_token"));
        // Codex refresh responses may omit individual token fields.
        boolean reusedAccess=!response.containsKey("access_token")||response.get("access_token")==null;
        if(reusedAccess)access=Json.string(previous.get("access"));
        String refresh=Json.string(response.get("refresh_token"));if(refresh.isEmpty())refresh=Json.string(previous.get("refresh"));
        if(!NetworkPolicy.headerSafe(access)||(!refresh.isEmpty()&&!NetworkPolicy.headerSafe(refresh)))throw new IllegalArgumentException("서버에서 유효한 인증정보가 오지 않았어.");
        String account=accountClaim(Json.string(response.get("id_token")));if(account.isEmpty())account=accountClaim(access);
        if(account.isEmpty())account=Json.string(previous.get("account"));
        if(!account.isEmpty()&&!NetworkPolicy.headerSafe(account))throw new IllegalArgumentException("서버에서 유효한 계정 정보가 오지 않았어.");
        long exp=Json.integer(jwt(access).get("exp"),0);
        long ttl=Json.integer(response.get("expires_in"),0);
        if(exp==0&&ttl>0)exp=System.currentTimeMillis()/1000+Math.min(ttl,60*60*24*30);
        else if(exp==0&&reusedAccess)exp=Json.integer(previous.get("expires"),0);
        return Json.map("access",access,"refresh",refresh,"account",account,"expires",exp);
    }
    private static Map<String,Object> jwt(String token){
        // Claims are hints from the HTTPS token response, NOT used as proof of identity.
        // The usage server validates the bearer token. No JWT-based authorization happens locally.
        try{String[]p=token.split("\\.");if(p.length!=3||p[1].length()>65536)return Collections.emptyMap();return Json.object(Json.parse(new String(Base64.decode(p[1],Base64.URL_SAFE|Base64.NO_WRAP),StandardCharsets.UTF_8)));}catch(Exception e){return Collections.emptyMap();}
    }
    private static String accountClaim(String token){return Json.string(Json.object(jwt(token).get("https://api.openai.com/auth")).get("chatgpt_account_id"));}
    private Map<String,Object> refresh(Map<String,Object> state,Map<String,Object> auth)throws Exception{
        String r=Json.string(auth.get("refresh"));
        if(r.isEmpty()){markReauthentication(state,"invalid");throw new IllegalStateException(REAUTH_MESSAGE);}
        Map<String,Object> response;
        try{response=Api.postJson("/oauth/token",Json.map("grant_type","refresh_token","client_id",Api.CLIENT_ID,"refresh_token",r));}
        catch(Api.HttpError error){if(error.invalidCredentials())markReauthentication(state,"invalid");throw error;}
        catch(Api.RequestTimeout error){if(error.authMayHaveChanged)markReauthentication(state,"uncertain");throw error;}
        catch(Api.AuthExchangeUncertain error){markReauthentication(state,"uncertain");throw error;}
        catch(IllegalArgumentException invalid){markReauthentication(state,"uncertain");throw new IllegalStateException(AUTH_UNCERTAIN_MESSAGE);}
        // The server may already have invalidated the old refresh token.
        Map<String,Object> updated;
        try{
            if(!NetworkPolicy.headerSafe(Json.string(response.get("access_token")))&&!NetworkPolicy.headerSafe(Json.string(response.get("refresh_token"))))throw new IllegalArgumentException();
            updated=normalize(response,auth);
        }catch(IllegalArgumentException invalid){markReauthentication(state,"uncertain");throw new IllegalStateException(AUTH_UNCERTAIN_MESSAGE);}
        state.put("auth",updated);
        try{saveCredentials(state);}catch(Exception storageFailure){quarantine("uncertain");throw new IllegalStateException(AUTH_UNCERTAIN_MESSAGE);}
        return updated;
    }
    SyncOutcome sync()throws Exception{
        return sync(()->false);
    }
    SyncOutcome sync(BooleanSupplier stopped)throws Exception{
        return sync(stopped,false,()->false);
    }
    // Call within one IO task. Recovery and refresh share only this invocation's
    // authoritative vault snapshot; no plaintext session survives on Repo.
    SyncOutcome syncConnected(BooleanSupplier stopped)throws Exception{
        return syncConnected(stopped,()->false);
    }
    SyncOutcome syncConnected(BooleanSupplier stopped,BooleanSupplier ineligibleAfterRecovery)throws Exception{
        return sync(stopped,true,ineligibleAfterRecovery);
    }
    private SyncOutcome sync(BooleanSupplier stopped,boolean connectedOnly,BooleanSupplier ineligibleAfterRecovery)throws Exception{
        if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;
        long now=System.currentTimeMillis();
        try{
            Map<String,Object>state=vault.read();Map<String,Object>auth=Json.object(state.get("auth"));
            boolean connected=publishConnection(state);
            if(stopped.getAsBoolean()||ineligibleAfterRecovery.getAsBoolean())return SyncOutcome.CANCELLED;
            if(connectedOnly&&!connected)return SyncOutcome.SIGNED_OUT;
            requireUsableSession(state);
            if(Store.prefs(c).getLong("backoff",0)>now)throw new IllegalStateException("서버가 재시도 대기를 요청했어. 잠시 후 다시 확인해 줘.");
            long last=Store.prefs(c).getLong("attempt",0);if(now>=last&&now-last<10_000)return SyncOutcome.SKIPPED;
            Store.prefs(c).edit().putLong("attempt",now).apply();
            if(auth.isEmpty())throw new IllegalStateException("먼저 로그인해 줘.");
            long exp=Json.integer(auth.get("expires"),0);boolean refreshed=false;
            if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;
            if(exp>0&&exp<=now/1000+120){auth=refresh(state,auth);refreshed=true;}
            if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;
            String body;
            try{body=Api.usage(Json.string(auth.get("access")),Json.string(auth.get("account")));}
            catch(Api.HttpError e){if(e.status!=401||refreshed)throw e;if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;auth=refresh(state,auth);if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;body=Api.usage(Json.string(auth.get("access")),Json.string(auth.get("account")));}
            if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;
            long fetchedAt=System.currentTimeMillis();
            List<Usage> parsed=Usage.parse(body,fetchedAt);Api.interrupted();
            if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;
            String selected=Store.prefs(c).getString("selected","codex");
            boolean selectedValid=false;
            for(Usage usage:parsed)if(usage.id.equals(selected)){
                if(usage.expired(fetchedAt))throw new IllegalStateException("서버의 주간 한도 값이 이미 만료됐어. 최근 정상 조회값을 유지했어.");
                selectedValid=true;
            }
            // A newly received response can legitimately omit the chosen bucket.
            // Save that authoritative list, but never present it as a successful
            // refresh of the selected percentage or invent a fetched timestamp.
            Store.save(c,parsed);
            if(stopped.getAsBoolean())return SyncOutcome.CANCELLED;
            if(!selectedValid)throw new IllegalStateException("선택한 주간 한도의 최신 값을 찾지 못했어. 앱에서 표시할 한도를 확인해 줘.");
            return SyncOutcome.UPDATED;
        }catch(Api.HttpError e){
            if(e.status==429)Store.prefs(c).edit().putLong("backoff",System.currentTimeMillis()+e.retrySeconds*1000).apply();
            Store.error(c,friendly(e));throw e;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
        catch(Exception e){Store.error(c,friendly(e));throw e;}
    }
    void disconnect()throws Exception{
        Scheduler.cancel(c);vault.clear();Store.prefs(c).edit().clear().apply();
    }
    static String friendly(Exception e){
        if(e instanceof Api.AuthExchangeUncertain||(e instanceof Api.RequestTimeout&&((Api.RequestTimeout)e).authMayHaveChanged))return AUTH_UNCERTAIN_MESSAGE;
        if(e instanceof Api.TransportBusy)return "이전 통신을 종료하는 중이야. 잠시 후 다시 확인해 줘.";
        if(e instanceof Api.HttpError){int s=((Api.HttpError)e).status;
            if(((Api.HttpError)e).invalidCredentials())return REAUTH_MESSAGE;
            if(s==401)return "인증이 만료됐어. 연결을 지우고 다시 로그인해 줘. (401)";
            if(s==403)return "접근이 거부됐어. 계정 권한 또는 서버 제한을 확인해 줘. (403)";
            if(s==404)return "인증·사용량 경로를 찾지 못했어. 서버 변경 가능성이 있어. (404)";
            if(s==429)return "서버가 요청을 제한했어. 잠시 후 다시 확인해 줘. (429)";
            return "서버 연결 오류 (HTTP "+s+")";
        }
        if(e instanceof java.net.UnknownHostException||e instanceof java.net.SocketTimeoutException||e instanceof java.net.ConnectException)return "인터넷 연결이 불안정해. 최근 조회값을 유지했어.";
        if(e instanceof javax.net.ssl.SSLException)return "보안 연결을 확인할 수 없어. 인증서 검증을 건너뛰지 않았어.";
        if(e instanceof java.security.GeneralSecurityException)return "저장된 인증정보를 복호화할 수 없어. 연결을 지운 뒤 다시 로그인해 줘.";
        if(e instanceof IllegalArgumentException||e instanceof IllegalStateException)return e.getMessage()==null?"응답을 확인할 수 없어.":e.getMessage();
        return "조회하지 못했어. 연결 상태와 공식 사용량 화면을 확인해 줘.";
    }
}
