package android.os;
import java.util.Locale;
public final class LocaleList {
    private final Locale[] values;
    public LocaleList(Locale... locales){values=locales.clone();}
    public boolean isEmpty(){return values.length==0;}
    public Locale get(int index){return values[index];}
    public String toLanguageTags(){StringBuilder s=new StringBuilder();for(Locale value:values){if(s.length()>0)s.append(',');s.append(value.toLanguageTag());}return s.toString();}
}
