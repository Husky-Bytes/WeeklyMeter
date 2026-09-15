package dev.yerin.weeklymeter;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** External-browser PKCE. No passwords, cookies, callback codes or tokens are logged. */
public final class BrowserAuth {
    public static final String CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
    public static final String REDIRECT_URI = "http://localhost:1455/auth/callback";
    private static final long LIFETIME_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_HEADERS = 16384, MAX_TARGET = 8192;
    private static final String CLEAN_URL_SCRIPT = "history.replaceState(null, \"\", \"/auth/complete\");";

    /** The socket is bound before this method returns; launch authorizeUrl() only afterwards. */
    public static Session bind() throws IOException { return bind(1455, LIFETIME_MILLIS, Locale.KOREAN); }
    public static Session bind(Locale locale) throws IOException { return bind(1455, LIFETIME_MILLIS, locale); }

    // Package-visible binding seam permits isolated, synthetic loopback tests on an ephemeral port.
    static Session bind(int port, long lifetimeMillis) throws IOException {
        return bind(port, lifetimeMillis, Locale.KOREAN);
    }

    static Session bind(int port, long lifetimeMillis, Locale locale) throws IOException {
        if (lifetimeMillis <= 0 || lifetimeMillis > LIFETIME_MILLIS) throw new IllegalArgumentException("Invalid duration");
        ServerSocket server = new ServerSocket();
        try {
            server.setReuseAddress(false);
            server.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), port), 8);
            return new Session(server, randomUrlSafe(), randomUrlSafe(), lifetimeMillis, locale);
        } catch (IOException | RuntimeException e) {
            try { server.close(); } catch (IOException ignored) { }
            throw new IOException("브라우저 로그인 연결을 열지 못했어. 다른 로그인 창을 닫고 다시 시도해 줘.");
        }
    }

    public static final class Result {
        public final String code;
        public final boolean denied;
        private Result(String code, boolean denied) { this.code = code; this.denied = denied; }
    }

    public static final class Session implements Closeable {
        private final ServerSocket server;
        private final String state, verifier;
        private final Locale locale;
        private final long expiresNanos;
        private volatile Socket activeSocket;
        private volatile boolean closed;
        private boolean awaited;

        private Session(ServerSocket server, String state, String verifier, long lifetimeMillis, Locale locale) {
            this.server = server; this.state = state; this.verifier = verifier;
            this.locale = locale!=null&&"ko".equals(locale.getLanguage())?Locale.KOREAN:Locale.ENGLISH;
            expiresNanos = System.nanoTime() + lifetimeMillis * 1_000_000L;
        }

        public String authorizeUrl() {
            if (closed || expired()) throw new IllegalStateException("브라우저 로그인을 다시 시작해 줘.");
            return "https://auth.openai.com/oauth/authorize?response_type=code&client_id=" + CLIENT_ID
                    + "&redirect_uri=" + encode(REDIRECT_URI)
                    + "&scope=" + encode("openid profile email offline_access")
                    + "&code_challenge=" + challenge(verifier) + "&code_challenge_method=S256"
                    + "&state=" + state + "&id_token_add_organizations=true"
                    + "&codex_cli_simplified_flow=true&originator=codex_cli";
        }

        /** Keep in memory only, then send to the exact HTTPS token endpoint with the code. */
        public String verifier() { return verifier; }

        /** Run on a background thread. Invalid callbacks do not consume or cancel the session. */
        public Result awaitCallback() throws IOException {
            synchronized (this) {
                if (awaited) throw new IOException("브라우저 로그인을 다시 시작해 줘.");
                awaited = true;
            }
            try {
                while (!closed && !expired()) {
                    server.setSoTimeout((int)Math.max(1, Math.min(1000, remainingMillis())));
                    Socket socket;
                    try { socket = server.accept(); }
                    catch (SocketTimeoutException ignored) { continue; }
                    activeSocket = socket;
                    try (Socket connection = socket) {
                        if (closed) break;
                        if (!connection.getInetAddress().isLoopbackAddress()) continue;
                        Result result;
                        try {
                            String request = readRequest(connection, Math.min(expiresNanos, System.nanoTime() + 3_000_000_000L));
                            result = parseRequest(request, state);
                            if (expired()) throw new SocketTimeoutException();
                        } catch (SocketTimeoutException invalid) {
                            respond(connection, 408, false, locale);
                            continue;
                        } catch (BadRequest invalid) {
                            respond(connection, 400, false, locale);
                            continue;
                        } catch (IOException disconnected) {
                            continue;
                        }
                        // Returning a code is deliberately independent of the browser receiving HTML.
                        // A broken connection must not lose a valid one-use authorization code.
                        respond(connection, 200, true, locale);
                        return result;
                    } finally { activeSocket = null; }
                }
                if (closed) throw new IOException("브라우저 로그인이 취소됐어.");
                throw new SocketTimeoutException("로그인 시간이 지났어. 다시 로그인해 줘.");
            } catch (IOException e) {
                if (closed) throw new IOException("브라우저 로그인이 취소됐어.");
                if (e instanceof SocketTimeoutException) throw e;
                throw new IOException("브라우저 로그인 연결이 종료됐어. 다시 시도해 줘.");
            } finally { close(); }
        }

        private boolean expired() { return expiresNanos - System.nanoTime() <= 0; }
        private long remainingMillis() { return (expiresNanos - System.nanoTime()) / 1_000_000L; }
        int localPort() { return server.getLocalPort(); }
        InetAddress localAddress() { return server.getInetAddress(); }

        @Override public void close() {
            closed = true;
            try { server.close(); } catch (IOException ignored) { }
            Socket socket = activeSocket;
            if (socket != null) try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private static String randomUrlSafe() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(); }
    }

    private static String readRequest(Socket socket, long deadlineNanos) throws IOException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int tail = 0;
        while (bytes.size() < MAX_HEADERS) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) throw new SocketTimeoutException();
            socket.setSoTimeout((int)Math.max(1, Math.min(3000, remaining / 1_000_000L)));
            int value = in.read();
            if (value == -1) throw new BadRequest();
            bytes.write(value); tail = (tail << 8) | value;
            if (tail == 0x0d0a0d0a) return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
            if (value > 126 || (value < 32 && value != 9 && value != 10 && value != 13)) throw new BadRequest();
        }
        throw new BadRequest();
    }

    static Result parseRequest(String request, String expectedState) throws BadRequest {
        if (request == null || request.length() > MAX_HEADERS || !request.endsWith("\r\n\r\n")) throw new BadRequest();
        for (int i = 0; i < request.length(); i++) {
            char c = request.charAt(i);
            if (c > 126 || (c < 32 && c != '\r' && c != '\n' && c != '\t')) throw new BadRequest();
        }
        String[] lines = request.substring(0, request.length() - 4).split("\r\n", -1);
        if (lines.length < 2 || lines.length > 81) throw new BadRequest();
        String[] first = lines[0].split(" ", -1);
        if (first.length != 3 || !"GET".equals(first[0]) || first[1].length() > MAX_TARGET
                || !("HTTP/1.1".equals(first[2]) || "HTTP/1.0".equals(first[2]))) throw new BadRequest();
        String target = first[1];
        int separator = target.indexOf('?');
        if (separator < 0 || !"/auth/callback".equals(target.substring(0, separator)) || target.indexOf('#') >= 0) throw new BadRequest();
        for (int i = 0; i < target.length(); i++) if (target.charAt(i) <= 32 || target.charAt(i) > 126) throw new BadRequest();
        Map<String,String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i]; int colon = line.indexOf(':');
            if (line.length() > 4096 || colon <= 0 || line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) throw new BadRequest();
            String name = line.substring(0, colon);
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw new BadRequest();
            String value = line.substring(colon + 1).trim();
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) throw new BadRequest();
            if (headers.put(name.toLowerCase(Locale.ROOT), value) != null) throw new BadRequest();
        }
        String host = headers.get("host");
        if (!("localhost:1455".equalsIgnoreCase(host) || "127.0.0.1:1455".equals(host))) throw new BadRequest();
        if (headers.containsKey("transfer-encoding")
                || (headers.containsKey("content-length") && !"0".equals(headers.get("content-length")))) throw new BadRequest();
        Map<String,String> query = new HashMap<>();
        String[] fields = target.substring(separator + 1).split("&", -1);
        if (fields.length > 32) throw new BadRequest();
        for (String field : fields) {
            int equals = field.indexOf('=');
            if (equals < 1) throw new BadRequest();
            String name = decode(field.substring(0, equals)), value = decode(field.substring(equals + 1));
            if (name.isEmpty() || name.length() > 64 || value.length() > 4096 || query.put(name, value) != null) throw new BadRequest();
        }
        String state = query.get("state");
        if (state == null || state.length() != 43 || expectedState == null || expectedState.length() != 43
                || !MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII), expectedState.getBytes(StandardCharsets.US_ASCII))) throw new BadRequest();
        if (query.containsKey("access_token") || query.containsKey("id_token") || query.containsKey("refresh_token")) throw new BadRequest();
        boolean code = query.containsKey("code"), error = query.containsKey("error");
        if (code == error) throw new BadRequest();
        String credential = query.get(code ? "code" : "error");
        if (credential.isEmpty() || (!code && credential.length() > 256)) throw new BadRequest();
        for (int i = 0; i < credential.length(); i++) if (credential.charAt(i) <= 32 || credential.charAt(i) > 126) throw new BadRequest();
        return new Result(code ? credential : null, error);
    }

    private static String decode(String encoded) throws BadRequest {
        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '%') {
                if (i + 2 >= encoded.length()) throw new BadRequest();
                int high = hex(encoded.charAt(++i)), low = hex(encoded.charAt(++i));
                if (high < 0 || low < 0) throw new BadRequest();
                c = (char)((high << 4) | low);
            } else if (c == '+') c = ' ';
            if (c < 32 || c > 126) throw new BadRequest();
            decoded.append(c);
        }
        return decoded.toString();
    }

    private static int hex(char value) {
        if (value >= '0' && value <= '9') return value - '0';
        if (value >= 'a' && value <= 'f') return value - 'a' + 10;
        if (value >= 'A' && value <= 'F') return value - 'A' + 10;
        return -1;
    }

    static String response(int status, boolean accepted) {
        return response(status, accepted, Locale.KOREAN);
    }

    static String response(int status, boolean accepted, Locale locale) {
        boolean korean=locale!=null&&"ko".equals(locale.getLanguage());
        String text = accepted ? (korean?"브라우저 확인 완료. 앱에서 연결 결과를 확인해 주세요.":"Browser step complete. Return to the app to check the result.")
                : (korean?"요청을 확인할 수 없습니다. 원래 로그인 화면에서 계속해 주세요.":"Could not verify this request. Continue from the original sign-in page.");
        String html = "<!doctype html><html lang=\""+(korean?"ko":"en")+"\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>WeeklyMeter</title><h1>WeeklyMeter</h1><p>" + text + "</p><script>" + CLEAN_URL_SCRIPT + "</script></html>";
        String phrase = status == 200 ? "OK" : status == 408 ? "Request Timeout" : "Bad Request";
        return "HTTP/1.1 " + status + " " + phrase + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                + html.getBytes(StandardCharsets.UTF_8).length + "\r\nConnection: close\r\nCache-Control: no-store\r\nPragma: no-cache\r\n"
                + "Referrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\n"
                + "Content-Security-Policy: default-src 'none'; script-src 'sha256-" + sha256Base64(CLEAN_URL_SCRIPT)
                + "'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'\r\n\r\n" + html;
    }

    private static String sha256Base64(String value) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static void respond(Socket socket, int status, boolean accepted, Locale locale) {
        try {
            OutputStream output = socket.getOutputStream();
            output.write(response(status, accepted, locale).getBytes(StandardCharsets.UTF_8)); output.flush();
        } catch (IOException ignored) { }
    }

    static final class BadRequest extends IOException {
        BadRequest() { super("Invalid browser callback"); }
    }
    private BrowserAuth() { }
}
