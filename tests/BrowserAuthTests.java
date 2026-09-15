package dev.yerin.weeklymeter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Only synthetic codes/state on a loopback socket; never connects to OpenAI or a real account. */
public final class BrowserAuthTests {
    private static int checks;
    private static final String STATE = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static void check(boolean okay, String name) {
        checks++; if (!okay) throw new AssertionError(name);
    }
    private static String request(String query) {
        return "GET /auth/callback?" + query + " HTTP/1.1\r\nHost: localhost:1455\r\n\r\n";
    }
    private static String good() { return request("state=" + STATE + "&code=synthetic-code"); }
    private static void invalid(String request, String name) throws Exception {
        boolean rejected = false;
        try { BrowserAuth.parseRequest(request, STATE); } catch (BrowserAuth.BadRequest expected) { rejected = true; }
        check(rejected, name);
    }
    private static String repeated(char value, int count) {
        char[] result = new char[count]; java.util.Arrays.fill(result, value); return new String(result);
    }
    private static Map<String,String> urlQuery(String url) throws Exception {
        Map<String,String> fields = new HashMap<>();
        for (String field : new URI(url).getRawQuery().split("&")) {
            String[] kv = field.split("=", 2); fields.put(kv[0], URLDecoder.decode(kv[1], "UTF-8"));
        }
        return fields;
    }
    private static String exchange(int port, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            InputStream input = socket.getInputStream(); byte[] buffer = new byte[512]; int size;
            while ((size = input.read(buffer)) != -1) {
                if (response.size() + size > 8192) throw new AssertionError("Response is unbounded");
                response.write(buffer, 0, size);
            }
            return response.toString("UTF-8");
        }
    }
    private static FutureTask<BrowserAuth.Result> waitFor(BrowserAuth.Session session) {
        FutureTask<BrowserAuth.Result> result = new FutureTask<>(session::awaitCallback);
        Thread thread = new Thread(result, "synthetic-browser-callback"); thread.setDaemon(true); thread.start(); return result;
    }
    private static void localizedResponse(int status, boolean accepted, Locale locale, String language, String message) throws Exception {
        String response=BrowserAuth.response(status,accepted,locale);
        String[] parts=response.split("\r\n\r\n",2);
        check(parts.length==2,"Localized response has one HTTP header/body boundary");
        String headers=parts[0],body=parts[1];
        String phrase=status==200?"OK":status==408?"Request Timeout":"Bad Request";
        check(headers.startsWith("HTTP/1.1 "+status+" "+phrase+"\r\n"),"Locale does not change HTTP status");
        check(body.contains("<html lang=\""+language+"\">"),"Localized response declares its actual language");
        check(body.contains("<p>"+message+"</p>"),"Localized success/failure body matches selected language");
        check(body.contains("<meta charset=\"utf-8\">")&&headers.contains("\r\nContent-Type: text/html; charset=utf-8"),"Localized response is UTF8 throughout");
        int lengthHeaders=0,declared=-1;
        for(String header:headers.split("\r\n"))if(header.startsWith("Content-Length: ")){lengthHeaders++;declared=Integer.parseInt(header.substring("Content-Length: ".length()));}
        check(lengthHeaders==1&&declared==body.getBytes(StandardCharsets.UTF_8).length,"Localized UTF8 byte length is exact and unique");
        check(!"ko".equals(language)||declared>body.length(),"Korean byte length is not a character count");
        check(!"en".equals(language)||!java.util.regex.Pattern.compile("[가-힣]").matcher(body).find(),"English and fallback bodies contain no Korean text");
        check(headers.contains("\r\nCache-Control: no-store")&&headers.contains("\r\nPragma: no-cache"),"Every locale prevents response caching");
        check(headers.contains("\r\nReferrer-Policy: no-referrer")&&headers.contains("\r\nX-Content-Type-Options: nosniff"),"Every locale keeps referrer and MIME protections");
        check(headers.contains("\r\nConnection: close")&&!headers.contains("Location:")&&!headers.contains("Set-Cookie:"),"Localized response closes without redirect or cookies");
        check(headers.contains("default-src 'none'")&&headers.contains("base-uri 'none'")&&headers.contains("frame-ancestors 'none'")&&headers.contains("form-action 'none'"),"Every locale retains restrictive CSP");
        int scriptStart=body.indexOf("<script>"),scriptEnd=body.indexOf("</script>");
        check(scriptStart>=0&&scriptEnd>scriptStart&&body.indexOf("<script>",scriptStart+1)<0,"Exactly one fixed cleanup script");
        String script=body.substring(scriptStart+"<script>".length(),scriptEnd);
        String digest=java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(script.getBytes(StandardCharsets.UTF_8)));
        check(headers.contains("script-src 'sha256-"+digest+"'")&&!headers.contains("'unsafe-inline'")&&!headers.contains("'unsafe-eval'"),"Localized CSP hash independently matches actual cleanup script");
        check(script.contains("history.replaceState")&&!body.contains("src=")&&!body.contains("href="),"Locale preserves callback URL cleanup without remote resources");
        check(!response.contains(STATE)&&!response.contains("synthetic-code")&&!response.contains("network-synthetic-code")&&!response.contains("Do not reflect")&&!response.contains("access_token")&&!response.contains("refresh_token")&&!response.contains("id_token"),"Localized page never echoes callback secrets or provider error text");
    }
    public static void main(String[] args) throws Exception {
        check(STATE.length() == 43, "Fixture state length");
        check("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM".equals(
                BrowserAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")), "RFC7636 Appendix B S256 vector");
        BrowserAuth.Result okay = BrowserAuth.parseRequest(good(), STATE);
        check("synthetic-code".equals(okay.code) && !okay.denied, "Valid callback");
        check(BrowserAuth.parseRequest(good().replace("localhost", "127.0.0.1"), STATE).code != null, "Numeric loopback Host");
        check(BrowserAuth.parseRequest(good().replace("Host:", "hOsT:"), STATE).code != null, "Case-insensitive header name");
        check(BrowserAuth.parseRequest(good().replace("localhost", "LOCALHOST"), STATE).code != null, "Case-insensitive hostname");
        check(BrowserAuth.parseRequest(good().replace("HTTP/1.1", "HTTP/1.0"), STATE).code != null, "HTTP1.0 with validated Host");
        check(BrowserAuth.parseRequest(request("state=" + STATE + "&code=a%2Bb%2Fc%3D&scope=openid+profile"), STATE).code.equals("a+b/c="), "Strict query decoding");
        BrowserAuth.Result denied = BrowserAuth.parseRequest(request("state=" + STATE + "&error=access_denied&error_description=Do+not+reflect"), STATE);
        check(denied.denied && denied.code == null, "Valid provider denial");
        invalid(good().replace("GET ", "POST "), "POST rejected");
        invalid(good().replace("GET ", "HEAD "), "HEAD rejected");
        invalid(good().replace("HTTP/1.1", "HTTP/2"), "Unsupported request version");
        invalid(good().replace("GET ", "GET  "), "Ambiguous request spaces");
        invalid(good().replace("/auth/callback?", "/auth/callback/?"), "Exact callback path");
        invalid(good().replace("/auth/callback?", "/other?"), "Unknown path");
        invalid(good().replace("/auth/callback?", "http://localhost:1455/auth/callback?"), "Absolute request target");
        invalid(good().replace("/auth/callback?", "/auth/%63allback?"), "Encoded callback path");
        invalid(good().replace(" HTTP/1.1", "#fragment HTTP/1.1"), "Fragment rejected");
        invalid(good().replace("Host: localhost:1455\r\n", ""), "Host required");
        invalid(good().replace("localhost:1455", "localhost.evil:1455"), "External Host rejected");
        invalid(good().replace("localhost:1455", "localhost:80"), "Wrong port rejected");
        invalid(good().replace("localhost:1455", "localhost:1455@evil"), "Host userinfo rejected");
        invalid(good().replace("localhost:1455", "[::1]:1455"), "Unbound IPv6 Host rejected");
        invalid(good().replace("Host:", " Host:"), "Folded header rejected");
        invalid(good().replace("Host:", "Host :"), "Whitespace before header colon");
        invalid(good().replace("\r\n\r\n", "\r\nHost: localhost:1455\r\n\r\n"), "Duplicate Host rejected");
        invalid(good().replace("\r\n\r\n", "\r\nX-Test: a\n\r\n\r\n"), "Bare LF rejected before trimming");
        invalid(good().replace("\r\n\r\n", "\r\nX-Test: a\r\r\n\r\n"), "Bare CR rejected before trimming");
        invalid(good().replace("\r\n\r\n", "\r\nTransfer-Encoding: chunked\r\n\r\n"), "Request bodies rejected");
        invalid(good().replace("\r\n\r\n", "\r\nContent-Length: 8\r\n\r\n"), "Nonzero content length rejected");
        invalid(good().replace("\r\n", "\n"), "CRLF terminator required");
        invalid(good() + "body", "Trailing request body rejected");
        invalid(good().replace(STATE, repeated('X', 43)), "Mismatched state rejected");
        invalid(request("code=synthetic-code"), "Missing state rejected");
        invalid(request("state=&code=synthetic-code"), "Empty state rejected");
        invalid(request("state=" + STATE + "X&code=synthetic-code"), "Oversize state rejected");
        invalid(request("state=" + STATE + "&state=" + STATE + "&code=a"), "Duplicate state rejected");
        invalid(request("state=" + STATE + "&%73tate=" + STATE + "&code=a"), "Encoded duplicate state rejected");
        invalid(request("state=" + STATE + "&code=a&code=b"), "Duplicate code rejected");
        invalid(request("state=" + STATE + "&code=a&error=access_denied"), "Code and error ambiguity");
        invalid(request("state=" + STATE), "Code or error required");
        invalid(request("state=" + STATE + "&code="), "Empty code rejected");
        invalid(request("state=" + STATE + "&error="), "Empty provider error rejected");
        invalid(request("state=" + STATE + "&error=" + repeated('x', 257)), "Provider error bound");
        invalid(request("state=" + STATE + "&code=a%0D%0Ab"), "Code CRLF injection");
        invalid(request("state=" + STATE + "&code=a%00b"), "Code null injection");
        invalid(request("state=" + STATE + "&code=a+b"), "Code whitespace rejected");
        invalid(request("state=" + STATE + "&code=%FF"), "NonASCII code rejected");
        invalid(request("state=" + STATE + "&code=%xx"), "Malformed hex rejected");
        invalid(request("state=" + STATE + "&code=%1"), "Partial hex rejected");
        invalid(request("state=" + STATE + "&code=%"), "Incomplete escape rejected");
        invalid(request("state=" + STATE + "&code=x&access_token=secret"), "Access token callback rejected");
        invalid(request("state=" + STATE + "&code=x&id_token=secret"), "ID token callback rejected");
        invalid(request("state=" + STATE + "&code=x&refresh_token=secret"), "Refresh token callback rejected");
        invalid(request("state=" + STATE + "&code=x&bad"), "Malformed query pair rejected");
        invalid(request("state=" + STATE + "&code=x&&z=1"), "Empty query pair rejected");
        invalid(request("state=" + STATE + "&code=" + repeated('x', 4097)), "Code length bound");
        check(BrowserAuth.parseRequest(request("state=" + STATE + "&code=" + repeated('x', 4096)), STATE).code.length() == 4096, "Code length limit accepted");
        invalid(request("state=" + STATE + "&code=x&x=" + repeated('x', 8200)), "Request target bound");
        invalid(good().replace("\r\n\r\n", "\r\nX: " + repeated('x', 4100) + "\r\n\r\n"), "Header line bound");
        invalid(good().replace("\r\n\r\n", "\r\nX: " + repeated('x', 17000) + "\r\n\r\n"), "Total header bound");
        StringBuilder many = new StringBuilder("state=" + STATE + "&code=x");
        for (int i = 0; i < 31; i++) many.append("&x").append(i).append("=1");
        invalid(request(many.toString()), "Query count bound");
        StringBuilder headers = new StringBuilder(good().substring(0, good().length() - 2));
        for (int i = 0; i < 80; i++) headers.append("X-").append(i).append(": 1\r\n");
        invalid(headers.append("\r\n").toString(), "Header count bound");
        String response = BrowserAuth.response(200, true);
        check(!response.contains("synthetic-code") && !response.contains(STATE), "Response contains no code or state");
        check(response.contains("Cache-Control: no-store") && response.contains("Referrer-Policy: no-referrer"), "No cache or referrer leaks");
        check(response.contains("default-src 'none'") && response.contains("frame-ancestors 'none'"), "Restrictive CSP");
        check(response.contains("history.replaceState") && !response.contains("Location:"), "Clear callback URL without redirect");
        String[] responseParts = response.split("\r\n\r\n", 2);
        check(responseParts[0].contains("Content-Length: " + responseParts[1].getBytes(StandardCharsets.UTF_8).length), "UTF8 byte length correct");
        Locale[] locales={Locale.KOREAN,Locale.ENGLISH,Locale.JAPANESE,null};
        for(Locale locale:locales){
            boolean ko=locale!=null&&"ko".equals(locale.getLanguage());String language=ko?"ko":"en";
            localizedResponse(200,true,locale,language,ko?"브라우저 확인 완료. 앱에서 연결 결과를 확인해 주세요.":"Browser step complete. Return to the app to check the result.");
            for(int status:new int[]{400,408})localizedResponse(status,false,locale,language,ko?"요청을 확인할 수 없습니다. 원래 로그인 화면에서 계속해 주세요.":"Could not verify this request. Continue from the original sign-in page.");
        }
        for(boolean accepted:new boolean[]{true,false}){
            int status=accepted?200:400;
            check(BrowserAuth.response(status,accepted).equals(BrowserAuth.response(status,accepted,Locale.KOREAN)),"Legacy response default remains Korean");
            check(BrowserAuth.response(status,accepted,Locale.KOREA).equals(BrowserAuth.response(status,accepted,Locale.KOREAN)),"Korean region variant follows Korean language");
            check(BrowserAuth.response(status,accepted,Locale.JAPANESE).equals(BrowserAuth.response(status,accepted,Locale.ENGLISH)),"Unsupported locale response is identical to English fallback");
            check(BrowserAuth.response(status,accepted,null).equals(BrowserAuth.response(status,accepted,Locale.ENGLISH)),"Null locale response is identical to English fallback");
        }

        if (args.length > 0 && "--unit-only".equals(args[0])) {
            System.out.println("Browser auth checks passed: " + checks + " (parser/PKCE only; sockets skipped)"); return;
        }
        try (BrowserAuth.Session session = BrowserAuth.bind(0, 10000)) {
            check(session.localAddress().getHostAddress().equals("127.0.0.1"), "Listener binds only IPv4 loopback");
            Map<String,String> query = urlQuery(session.authorizeUrl());
            check(session.authorizeUrl().startsWith("https://auth.openai.com/oauth/authorize?"), "Exact HTTPS authorization endpoint");
            check(BrowserAuth.CLIENT_ID.equals(query.get("client_id")), "Public client ID");
            check(BrowserAuth.REDIRECT_URI.equals(query.get("redirect_uri")), "Exact redirect URI");
            check("openid profile email offline_access".equals(query.get("scope")), "Offline refresh requested");
            check("S256".equals(query.get("code_challenge_method")), "S256 enforced");
            check(query.get("code_challenge").equals(BrowserAuth.challenge(session.verifier())), "Generated challenge matches verifier");
            check(session.verifier().matches("[A-Za-z0-9_-]{43}") && query.get("state").matches("[A-Za-z0-9_-]{43}"), "Cryptographic material shape");
            check(!query.get("state").equals(session.verifier()), "State and verifier independent");
            check(!session.authorizeUrl().contains(session.verifier()), "Verifier absent from authorization URL");
            FutureTask<BrowserAuth.Result> waiting = waitFor(session);
            String rejected = exchange(session.localPort(), good());
            check(rejected.startsWith("HTTP/1.1 400"), "Loopback invalid-state probe rejected");
            check(!waiting.isDone(), "Invalid-state probe does not consume session");
            String accepted = exchange(session.localPort(), request("state=" + query.get("state") + "&code=network-synthetic-code"));
            check(accepted.startsWith("HTTP/1.1 200") && !accepted.contains("network-synthetic-code"), "Loopback valid callback generic response");
            check("network-synthetic-code".equals(waiting.get(3, TimeUnit.SECONDS).code), "Loopback valid code delivered");
            boolean consumed = false;
            try { session.awaitCallback(); } catch (java.io.IOException expected) { consumed = true; }
            check(consumed, "Callback session is single use");
        }
        try (BrowserAuth.Session session = BrowserAuth.bind(0, 100)) {
            long started = System.nanoTime(); boolean expired = false;
            try { session.awaitCallback(); } catch (SocketTimeoutException expected) { expired = true; }
            check(expired && System.nanoTime() - started < TimeUnit.SECONDS.toNanos(3), "Monotonic session expiry");
        }
        try (BrowserAuth.Session session = BrowserAuth.bind(0, 150)) {
            FutureTask<BrowserAuth.Result> waiting = waitFor(session);
            try (Socket socket = new Socket("127.0.0.1", session.localPort())) {
                socket.getOutputStream().write('G');
                boolean expired = false;
                try { waiting.get(3, TimeUnit.SECONDS); }
                catch (java.util.concurrent.ExecutionException expected) { expired = expected.getCause() instanceof SocketTimeoutException; }
                check(expired, "Incomplete request cannot extend session lifetime");
            }
        }
        try (BrowserAuth.Session session = BrowserAuth.bind(0, 10000)) {
            FutureTask<BrowserAuth.Result> waiting = waitFor(session);
            session.close(); boolean cancelled = false;
            try { waiting.get(3, TimeUnit.SECONDS); }
            catch (java.util.concurrent.ExecutionException expected) { cancelled = expected.getCause() instanceof java.io.IOException; }
            check(cancelled, "Close unblocks accept");
        }
        try (BrowserAuth.Session first = BrowserAuth.bind(0, 10000); BrowserAuth.Session second = BrowserAuth.bind(0, 10000)) {
            check(!first.verifier().equals(second.verifier()) && !urlQuery(first.authorizeUrl()).get("state").equals(urlQuery(second.authorizeUrl()).get("state")), "Fresh session secrets");
        }
        System.out.println("Browser auth checks passed: " + checks + " (includes synthetic loopback integration; no real login)");
    }
}
