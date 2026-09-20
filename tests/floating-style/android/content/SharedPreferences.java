package android.content;

public interface SharedPreferences {
    String getString(String key,String fallback);
    int getInt(String key,int fallback);
    boolean getBoolean(String key,boolean fallback);
    Editor edit();
    interface Editor {
        Editor putString(String key,String value);
        Editor putInt(String key,int value);
        Editor putBoolean(String key,boolean value);
        void apply();
    }
}
