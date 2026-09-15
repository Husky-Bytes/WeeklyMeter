package android.content;
public interface SharedPreferences {
    boolean getBoolean(String key,boolean fallback);
    int getInt(String key,int fallback);
}
