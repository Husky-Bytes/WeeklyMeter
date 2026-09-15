package dev.yerin.weeklymeter;

import android.content.Context;
import java.util.Locale;

/** Every background component uses the same effective app locale as the UI. */
final class Texts {
    static String t(Context c,String ko,String en){return "ko".equals(locale(c).getLanguage())?ko:en;}
    static Locale locale(Context c){return AppLanguage.locale(c);}
    private Texts(){}
}
