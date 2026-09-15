package android.content.res;
import android.os.LocaleList;
import java.util.Locale;
public class Configuration {
    private LocaleList locales=new LocaleList(Locale.ENGLISH);
    public Configuration(){}
    public Configuration(Configuration other){locales=other.locales;}
    public LocaleList getLocales(){return locales;}
    public void setLocales(LocaleList value){locales=value;}
    public void setLayoutDirection(Locale locale){}
}
