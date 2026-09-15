package android.content;
import android.content.res.Configuration;
import android.content.res.Resources;
public abstract class Context {
    public static final int MODE_PRIVATE=0;
    public abstract SharedPreferences getSharedPreferences(String name,int mode);
    public abstract <T>T getSystemService(Class<T> type);
    public abstract Resources getResources();
    public abstract Context createConfigurationContext(Configuration value);
}
