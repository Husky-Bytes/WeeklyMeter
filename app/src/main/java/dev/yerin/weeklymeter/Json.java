package dev.yerin.weeklymeter;

import java.util.*;

/** Small, bounded JSON codec. No network, reflection, or third-party dependencies. */
public final class Json {
    private final String text;
    private int pos;
    private Json(String text) { this.text = text; }
    public static Object parse(String text) {
        if (text == null || text.length() > 1_048_576) throw bad();
        Json p = new Json(text);
        Object result = p.value(0);
        p.space();
        if (p.pos != text.length()) throw bad();
        return result;
    }
    public static Map<String,Object> object(Object value) {
        if (!(value instanceof Map)) return Collections.emptyMap();
        @SuppressWarnings("unchecked") Map<String,Object> map = (Map<String,Object>) value;
        return map;
    }
    public static List<Object> array(Object value) {
        if (!(value instanceof List)) return Collections.emptyList();
        @SuppressWarnings("unchecked") List<Object> list = (List<Object>) value;
        return list;
    }
    public static String string(Object value) { return value instanceof String ? (String)value : ""; }
    public static long integer(Object value, long fallback) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) return ((Number)value).longValue();
        if (value instanceof Number) {
            double d = ((Number)value).doubleValue();
            if (Double.isFinite(d) && d >= Long.MIN_VALUE && d < Long.MAX_VALUE && d == Math.rint(d)) return (long)d;
        }
        return fallback;
    }
    public static double number(Object value, double fallback) {
        return value instanceof Number ? ((Number)value).doubleValue() : fallback;
    }
    public static Map<String,Object> map(Object... pairs) {
        if (pairs.length % 2 != 0) throw bad();
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0; i<pairs.length; i+=2) result.put((String)pairs[i], pairs[i+1]);
        return result;
    }
    public static String encode(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }
    private static void write(Object v, StringBuilder b, int depth) {
        if (depth > 32) throw bad();
        if (v == null) { b.append("null"); return; }
        if (v instanceof String) {
            b.append('"');
            for (char c : ((String)v).toCharArray()) {
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c < 32 || c == '\u2028' || c == '\u2029') b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
                else b.append(c);
            }
            b.append('"');
        } else if (v instanceof Number) {
            if (!Double.isFinite(((Number)v).doubleValue())) throw bad();
            b.append(v);
        } else if (v instanceof Boolean) b.append(v);
        else if (v instanceof Map) {
            b.append('{'); boolean first=true;
            for (Map.Entry<String,Object> e : object(v).entrySet()) {
                if (!first) b.append(','); first=false;
                write(e.getKey(),b,depth+1); b.append(':'); write(e.getValue(),b,depth+1);
            }
            b.append('}');
        } else if (v instanceof List) {
            b.append('['); boolean first=true;
            for (Object item : array(v)) { if (!first) b.append(','); first=false; write(item,b,depth+1); }
            b.append(']');
        } else throw bad();
    }
    private Object value(int depth) {
        if (depth > 32) throw bad(); space();
        if (pos >= text.length()) throw bad();
        char c=text.charAt(pos);
        if (c=='"') return quoted();
        if (c=='{') {
            pos++; Map<String,Object> m=new LinkedHashMap<>(); space();
            if (take('}')) return m;
            do {
                space(); if (pos>=text.length() || text.charAt(pos)!='"') throw bad();
                String key=quoted(); space(); require(':');
                if (m.containsKey(key)) throw bad();
                m.put(key,value(depth+1)); space();
                if (take('}')) return m;
                require(',');
            } while (true);
        }
        if (c=='[') {
            pos++; List<Object> list=new ArrayList<>(); space(); if (take(']')) return list;
            do { list.add(value(depth+1)); space(); if (take(']')) return list; require(','); } while (true);
        }
        if (text.startsWith("true",pos)) {pos+=4;return Boolean.TRUE;}
        if (text.startsWith("false",pos)) {pos+=5;return Boolean.FALSE;}
        if (text.startsWith("null",pos)) {pos+=4;return null;}
        int start=pos; take('-');
        if (take('0')) { if (pos<text.length() && Character.isDigit(text.charAt(pos))) throw bad(); }
        else digits();
        boolean decimal=false;
        if (take('.')) {decimal=true;digits();}
        if (take('e') || take('E')) {decimal=true;if (!take('+')) take('-');digits();}
        try {
            String s=text.substring(start,pos);
            if (!decimal) return Long.valueOf(s);
            double d=Double.parseDouble(s); if (!Double.isFinite(d)) throw bad(); return d;
        } catch (NumberFormatException e) {throw bad();}
    }
    private String quoted() {
        require('"'); StringBuilder b=new StringBuilder();
        while (pos<text.length()) {
            char c=text.charAt(pos++); if (c=='"') return b.toString();
            if (c<32) throw bad();
            if (c!='\\') {b.append(c);continue;}
            if (pos>=text.length()) throw bad(); c=text.charAt(pos++);
            switch(c) {
                case '"':case '\\':case '/':b.append(c);break;
                case 'b':b.append('\b');break;case 'f':b.append('\f');break;
                case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;
                case 'u':
                    if (pos+4>text.length()) throw bad();
                    int code=0;
                    for(int i=0;i<4;i++) {
                        char h=text.charAt(pos+i);
                        int digit=h>='0'&&h<='9'?h-'0':h>='a'&&h<='f'?h-'a'+10:h>='A'&&h<='F'?h-'A'+10:-1;
                        if(digit<0)throw bad();
                        code=code*16+digit;
                    }
                    b.append((char)code);
                    pos+=4;break;
                default:throw bad();
            }
        }
        throw bad();
    }
    private void digits() {
        int start=pos;
        while (pos<text.length() && text.charAt(pos)>='0' && text.charAt(pos)<='9') pos++;
        if (start==pos) throw bad();
    }
    private void space() {while(pos<text.length() && " \n\r\t".indexOf(text.charAt(pos))>=0)pos++;}
    private boolean take(char c) {if(pos<text.length() && text.charAt(pos)==c){pos++;return true;}return false;}
    private void require(char c) {if(!take(c))throw bad();}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("JSON 응답 형식을 확인할 수 없어.");}
}
