package android.util;

/** JVM auth-test stub, never packaged in the Android app. */
public final class Base64 {
    public static final int URL_SAFE=8,NO_WRAP=2;
    public static byte[] decode(String value,int flags){return java.util.Base64.getUrlDecoder().decode(value);}
}
