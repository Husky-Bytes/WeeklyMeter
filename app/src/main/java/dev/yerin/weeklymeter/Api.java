package dev.yerin.weeklymeter;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** No telemetry, redirects, custom trust managers, or model-generation requests. */
final class Api {
    static final String CLIENT_ID="app_EMoamEEZ73f0CkXaXp7hrann"; // Public Codex client ID, NOT an API key.
    static final class HttpError extends IOException {
        final int status; final long retrySeconds;
        HttpError(int status,long retry){super("서버 응답 HTTP "+status);this.status=status;this.retrySeconds=retry;}
    }
    static Map<String,Object> postJson(String path,Map<String,Object> body)throws Exception{
        return Json.object(Json.parse(request("POST",NetworkPolicy.AUTH+path,Json.encode(body),"application/json",null,null)));
    }
    static Map<String,Object> token(Map<String,Object> form)throws Exception{
        StringBuilder body=new StringBuilder();
        for(Map.Entry<String,Object> e:form.entrySet()){
            if(body.length()>0)body.append('&');
            body.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(String.valueOf(e.getValue()),"UTF-8"));
        }
        return Json.object(Json.parse(request("POST",NetworkPolicy.AUTH+"/oauth/token",body.toString(),"application/x-www-form-urlencoded",null,null)));
    }
    static String usage(String access,String account)throws Exception{
        return request("GET",NetworkPolicy.USAGE,null,null,access,account);
    }
    private static String request(String method,String address,String body,String type,String token,String account)throws Exception{
        interrupted();NetworkPolicy.check(method,address,token!=null);
        // Once an authentication POST is sent, finish reading its bounded response.
        // A cancelled job must not discard a newly rotated refresh token.
        boolean authExchange="POST".equals(method);
        if(token!=null&&!NetworkPolicy.headerSafe(token))throw new SecurityException("인증정보 형식 오류");
        if(account!=null&&!account.isEmpty()&&!NetworkPolicy.headerSafe(account))throw new SecurityException("계정 정보 형식 오류");
        HttpsURLConnection c=(HttpsURLConnection)new URL(address).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setUseCaches(false);
        c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Cache-Control","no-cache");
        c.setRequestProperty("User-Agent","WeeklyMeter/0.3 (Android; personal usage widget)");
        if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
        if(account!=null&&!account.isEmpty())c.setRequestProperty("ChatGPT-Account-ID",account);
        try {
            if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type",type);byte[]bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int status=c.getResponseCode();if(!authExchange)interrupted();
            if(status<200||status>=300){
                long retry=60;
                try{retry=Math.max(30,Math.min(86400,Long.parseLong(c.getHeaderField("Retry-After"))));}catch(Exception ignored){}
                // Do NOT log or persist arbitrary error bodies, which may contain secrets.
                throw new HttpError(status,retry);
            }
            String ct=c.getContentType();
            if(ct==null||!ct.toLowerCase(Locale.ROOT).contains("json"))throw new IOException("JSON 대신 로그인·보안 확인 화면이 반환됐어. 공식 화면에서 상태를 확인해 줘.");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[]buffer=new byte[4096];int n;
                while((n=in.read(buffer))!=-1){if(!authExchange)interrupted();if(out.size()+n>1_048_576)throw new IOException("서버 응답이 너무 커.");out.write(buffer,0,n);}
                return out.toString("UTF-8");
            }
        } finally {c.disconnect();}
    }
    static void interrupted()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private Api(){}
}
