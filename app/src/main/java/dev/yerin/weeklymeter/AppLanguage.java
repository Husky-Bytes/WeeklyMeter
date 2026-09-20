package dev.yerin.weeklymeter;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;

/** Permission-free language preference, shared by activities, services and widgets. */
final class AppLanguage {
    static final String SYSTEM=LanguagePolicy.SYSTEM,KO=LanguagePolicy.KO,EN=LanguagePolicy.EN;
    private static volatile Locale current;
    private static SharedPreferences preferences(Context c){return c.getSharedPreferences("language_settings",Context.MODE_PRIVATE);}
    static String choice(Context c){return LanguagePolicy.normalize(preferences(c).getString("choice",SYSTEM));}
    static Locale locale(Context c){
        Locale value=current;
        if(value==null){synchronize(c);value=current;}
        return value;
    }
    static Context wrap(Context c){
        Configuration config=new Configuration(c.getResources().getConfiguration());
        config.setLocales(new LocaleList(locale(c)));
        config.setLayoutDirection(locale(c));
        return c.createConfigurationContext(config);
    }
    static synchronized void apply(Context c,String selected){
        SharedPreferences.Editor edit=preferences(c).edit().putString("choice",LanguagePolicy.normalize(selected));
        // A user choice in this app must not be mistaken for a change made in Android Settings.
        if(Build.VERSION.SDK_INT>=33){LocaleManager manager=c.getSystemService(LocaleManager.class);if(manager!=null)edit.putString("platform_snapshot",manager.getApplicationLocales().toLanguageTags());}
        edit.apply();synchronize(c);
        // A rejected launcher update must not undo or crash a saved language change.
        try{WeeklyWidget.renderAll(c);}catch(RuntimeException unavailable){}
    }
    static synchronized void synchronize(Context c){
        SharedPreferences preferences=preferences(c);
        String selected=choice(c);
        LocaleManager manager=Build.VERSION.SDK_INT>=33?c.getSystemService(LocaleManager.class):null;
        String platformTags=manager==null?"":manager.getApplicationLocales().toLanguageTags();
        if(manager!=null){
            String previous=preferences.getString("platform_snapshot",null);
            if((previous==null&&!platformTags.isEmpty())||(previous!=null&&!previous.equals(platformTags)))selected=LanguagePolicy.platformChoice(platformTags);
        }
        LocaleList system=manager!=null?manager.getSystemLocales():Resources.getSystem().getConfiguration().getLocales();
        Locale primary=system.isEmpty()?null:system.get(0);
        current=LanguagePolicy.resolve(selected,primary);
        SharedPreferences.Editor edit=preferences.edit().putString("choice",selected);
        if(manager!=null){
            // Pin the resolved single language, even in automatic mode. Passing an empty list
            // would let Android select a secondary Korean language on a non-Korean phone.
            String wanted=current.toLanguageTag();
            edit.putString("platform_snapshot",wanted).apply();
            if(!platformTags.equals(wanted))manager.setApplicationLocales(new LocaleList(current));
        }else edit.apply();
    }
    private AppLanguage(){}
}
