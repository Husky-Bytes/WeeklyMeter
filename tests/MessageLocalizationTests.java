package dev.yerin.weeklymeter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java catalog tests and an executable source-message coverage audit. */
public final class MessageLocalizationTests {
    private static int count;
    private static final Pattern HANGUL = Pattern.compile("[\\u1100-\\u11ff\\u3130-\\u318f\\uac00-\\ud7af]");
    // Skip comments and character literals while capturing Java string literals.
    private static final Pattern SOURCE_TOKEN = Pattern.compile(
        "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|'(?:\\\\.|[^'\\\\])*'|\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final String[] RUNTIME_FILES = {
        "Api", "Repo", "Usage", "Json", "NetworkPolicy", "Scheduler",
        "WidgetRefreshService", "BrowserLoginService", "Vault", "UsageJob"
    };

    private static void check(boolean condition, String name) {
        count++;
        if (!condition) throw new AssertionError(name);
    }

    private static String unescape(String source) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value != '\\') { result.append(value); continue; }
            if (++i >= source.length()) throw new AssertionError("Incomplete Java escape");
            char escaped = source.charAt(i);
            switch (escaped) {
                case 'b': result.append('\b'); break;
                case 't': result.append('\t'); break;
                case 'n': result.append('\n'); break;
                case 'f': result.append('\f'); break;
                case 'r': result.append('\r'); break;
                case 's': result.append(' '); break;
                case '\"': result.append('\"'); break;
                case '\'': result.append('\''); break;
                case '\\': result.append('\\'); break;
                case 'u':
                    while (i + 1 < source.length() && source.charAt(i + 1) == 'u') i++;
                    result.append((char) Integer.parseInt(source.substring(i + 1, i + 5), 16));
                    i += 4;
                    break;
                default:
                    if (escaped < '0' || escaped > '7') throw new AssertionError("Unknown Java escape");
                    int octal = escaped - '0';
                    int remaining = escaped <= '3' ? 2 : 1;
                    while (remaining-- > 0 && i + 1 < source.length()
                            && source.charAt(i + 1) >= '0' && source.charAt(i + 1) <= '7') {
                        octal = octal * 8 + source.charAt(++i) - '0';
                    }
                    result.append((char) octal);
            }
        }
        return result.toString();
    }

    private static void catalogChecks() {
        check(Messages.ENGLISH.size() >= 75, "Known runtime message catalog is not accidentally truncated");
        check(Messages.KOREAN.keySet().equals(Messages.ENGLISH.keySet()), "Korean and English catalogs cover the same stored message IDs");
        Locale[] korean = {Locale.KOREAN, Locale.KOREA, Locale.forLanguageTag("ko-KP"), Locale.forLanguageTag("ko-Hang-KR")};
        Locale[] englishFallback = {Locale.ENGLISH, Locale.US, Locale.UK, Locale.ROOT, Locale.JAPANESE,
            Locale.SIMPLIFIED_CHINESE, Locale.FRENCH, Locale.forLanguageTag("ar-EG"), null};
        for (Map.Entry<String, String> entry : Messages.ENGLISH.entrySet()) {
            String raw = entry.getKey(), english = entry.getValue(), formalKorean = Messages.KOREAN.get(entry.getKey());
            check(raw != null && !raw.trim().isEmpty(), "Nonempty source message");
            check(english != null && !english.trim().isEmpty(), "Nonempty English translation: " + raw);
            check(HANGUL.matcher(raw).find(), "Korean source identifier: " + raw);
            check(!HANGUL.matcher(english).find(), "No untranslated Hangul: " + english);
            check(!raw.equals(english), "Known source is actually translated: " + raw);
            check(formalKorean != null && !formalKorean.trim().isEmpty(), "Nonempty formal Korean message: " + raw);
            check(!Pattern.compile("(?:해 줘[.]|했어[.]|됐어[.]|없어[.]|아니야[.]|달라졌어[.]|커[.]|불안정해[.]|있어[.]|필요해[.])$")
                .matcher(formalKorean).find(), "No legacy informal Korean ending: " + formalKorean);
            for (Locale locale : korean)
                check(Messages.localize(raw, locale).equals(formalKorean), "Formal Korean mapping: " + locale + ": " + raw);
            for (Locale locale : englishFallback)
                check(Messages.localize(raw, locale).equals(english), "English fallback: " + locale + ": " + raw);
            check(Messages.localize(english, Locale.ENGLISH).equals(english), "English idempotence");
            // Keep the original persisted string: display-language switching must not mutate it.
            String cached = new String(raw);
            check(Messages.localize(cached, Locale.KOREA).equals(formalKorean), "Cached Korean shown formally in Korean");
            check(Messages.localize(cached, Locale.US).equals(english), "Cached Korean translated after English switch");
            check(Messages.localize(cached, Locale.KOREA).equals(formalKorean), "Cached formal Korean restored after switch back");
        }
        for (Locale locale : Arrays.asList(Locale.KOREAN, Locale.ENGLISH, Locale.JAPANESE, null)) {
            check(Messages.localize(null, locale).isEmpty(), "Null message is empty");
            check(Messages.localize("", locale).isEmpty(), "Empty message stays empty");
            for (String unknown : new String[]{"server_feature_v2", "사용자가 만든 한도", "Custom limit 🌿", "  untouched  ", "<b>label</b>"})
                check(Messages.localize(unknown, locale).equals(unknown), "Unknown server/user label is preserved");
        }
        boolean immutable = false;
        try { Messages.ENGLISH.put("unexpected", "mutation"); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Catalog cannot be mutated accidentally");
        immutable = false;
        try { Messages.KOREAN.put("unexpected", "mutation"); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "Formal Korean catalog cannot be mutated accidentally");
    }

    private static void httpChecks() {
        // This tests the strict three-ASCII-digit message shape, not HTTP validity.
        for (String code : new String[]{"000", "099", "100", "200", "299", "301", "400", "401", "403", "404", "429", "500", "503", "599", "999"}) {
            String connection = "서버 연결 오류 (HTTP " + code + ")";
            String response = "서버 응답 HTTP " + code;
            for (Locale locale : Arrays.asList(Locale.ENGLISH, Locale.JAPANESE, null)) {
                check(Messages.localize(connection, locale).equals("Server connection error (HTTP " + code + ")"), "HTTP connection message");
                check(Messages.localize(response, locale).equals("Server response HTTP " + code), "HTTP response message");
            }
            check(Messages.localize(connection, Locale.KOREAN).equals(connection), "Korean HTTP connection identity");
            check(Messages.localize(response, Locale.KOREAN).equals(response), "Korean HTTP response identity");
        }
        for (String code : new String[]{"", "9", "99", "1000", "-01", "+99", "50.0", "５００", "٥٠٠", "5O0", " 500", "500 ", "500\n", "500\r\n", "500<script>"}) {
            for (String raw : new String[]{"서버 연결 오류 (HTTP " + code + ")", "서버 응답 HTTP " + code})
                check(Messages.localize(raw, Locale.ENGLISH).equals(raw), "Malformed HTTP status must not be interpolated");
        }
        for (String raw : new String[]{"prefix 서버 응답 HTTP 500", "서버 응답 HTTP 500 suffix",
                "서버 연결 오류 (HTTP 500) suffix", "prefix 서버 연결 오류 (HTTP 500)",
                "서버 연결 오류 HTTP 500", "서버 연결 오류 (HTTP 500", "서버 응답 HTTP 500\n"})
            check(Messages.localize(raw, Locale.ENGLISH).equals(raw), "Only the exact HTTP message is translated");
    }

    private static void sourceCoverage(Path project) throws Exception {
        Path sources = project.resolve("app/src/main/java/dev/yerin/weeklymeter");
        Set<String> fragments = new HashSet<>(Arrays.asList("서버 응답 HTTP ", "서버 연결 오류 (HTTP "));
        Set<String> observedFragments = new HashSet<>();
        int audited = 0;
        for (String name : RUNTIME_FILES) {
            Path file = sources.resolve(name + ".java");
            check(Files.isRegularFile(file), "Source exists for localization audit: " + file);
            String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Matcher matcher = SOURCE_TOKEN.matcher(source);
            while (matcher.find()) {
                if (matcher.group(1) == null) continue;
                String literal = unescape(matcher.group(1));
                if (!HANGUL.matcher(literal).find()) continue;
                if (fragments.contains(literal)) {
                    observedFragments.add(literal);
                    check((name.equals("Api") && literal.equals("서버 응답 HTTP "))
                        || (name.equals("Repo") && literal.equals("서버 연결 오류 (HTTP ")),
                        "Structured HTTP fragment used only in its audited formatter");
                    continue;
                }
                int line = 1;
                for (int i = 0; i < matcher.start(); i++) if (source.charAt(i) == '\n') line++;
                check(Messages.ENGLISH.containsKey(literal), name + ".java:" + line + " missing catalog entry: " + literal);
                check(!HANGUL.matcher(Messages.localize(literal, Locale.ENGLISH)).find(), "Runtime message translates: " + name);
                audited++;
            }
        }
        check(audited >= 70, "Runtime Korean-message audit actually inspected the expected messages");
        check(observedFragments.equals(fragments), "Both structured HTTP message formats are audited separately");
        System.out.println("Runtime source audit: " + audited + " Korean message occurrences covered; two structured HTTP fragments tested separately.");
    }

    public static void main(String[] args) throws Exception {
        catalogChecks();
        httpChecks();
        sourceCoverage(args.length == 0 ? Paths.get(".") : Paths.get(args[0]));
        System.out.println("PASS: " + count + " message localization checks. No Android/device verification.");
    }
}
