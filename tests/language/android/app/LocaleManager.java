package android.app;
import android.os.LocaleList;
import java.util.Locale;
public final class LocaleManager {
    public LocaleList system=new LocaleList(Locale.ENGLISH),app=new LocaleList();
    public int writes;
    public LocaleList getSystemLocales(){return system;}
    public LocaleList getApplicationLocales(){return app;}
    public void setApplicationLocales(LocaleList locales){app=locales;writes++;}
}
