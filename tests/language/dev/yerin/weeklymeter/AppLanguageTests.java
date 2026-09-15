package dev.yerin.weeklymeter;

import android.app.LocaleManager;
import android.content.*;
import android.content.res.*;
import android.os.*;
import java.util.*;

public final class AppLanguageTests {
    private static int checks;
    private static void equal(Object expected,Object actual){checks++;if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void clearProcess()throws Exception{java.lang.reflect.Field field=AppLanguage.class.getDeclaredField("current");field.setAccessible(true);field.set(null,null);}
    public static void main(String[] args)throws Exception{
        Build.VERSION.SDK_INT=33;FakeContext c=new FakeContext();c.manager.system=new LocaleList(Locale.KOREA,Locale.ENGLISH);clearProcess();
        equal(Locale.KOREAN,Texts.locale(c));equal("system",AppLanguage.choice(c));equal("ko",c.manager.app.toLanguageTags());equal(1,c.manager.writes);
        equal("한국어",Texts.t(c,"한국어","English"));AppLanguage.synchronize(c);equal(1,c.manager.writes);
        AppLanguage.apply(c,"en");equal("en",AppLanguage.choice(c));equal(Locale.ENGLISH,Texts.locale(c));equal("en",c.manager.app.toLanguageTags());equal(1,WeeklyWidget.renders);
        c.manager.system=new LocaleList(Locale.JAPANESE,Locale.KOREAN);AppLanguage.synchronize(c);equal(Locale.ENGLISH,Texts.locale(c));equal("en",AppLanguage.choice(c));
        AppLanguage.apply(c,"system");equal("system",AppLanguage.choice(c));equal("en",c.manager.app.toLanguageTags());
        c.manager.system=new LocaleList(Locale.KOREA);AppLanguage.synchronize(c);equal(Locale.KOREAN,Texts.locale(c));equal("system",AppLanguage.choice(c));equal("ko",c.manager.app.toLanguageTags());
        // Android Settings is authoritative when it changes the platform value.
        c.manager.app=new LocaleList(Locale.ENGLISH);AppLanguage.synchronize(c);equal("en",AppLanguage.choice(c));equal(Locale.ENGLISH,Texts.locale(c));
        c.manager.app=new LocaleList();AppLanguage.synchronize(c);equal("system",AppLanguage.choice(c));equal(Locale.KOREAN,Texts.locale(c));
        clearProcess();AppLanguage.synchronize(c);equal("system",AppLanguage.choice(c));equal(Locale.KOREAN,Texts.locale(c));
        c.manager.system=new LocaleList(Locale.FRENCH,Locale.KOREAN);AppLanguage.synchronize(c);equal("en",c.manager.app.toLanguageTags());equal(Locale.ENGLISH,Texts.locale(c));
        Context wrapped=AppLanguage.wrap(c);equal("en",wrapped.getResources().getConfiguration().getLocales().get(0).getLanguage());
        // System setting made before first launch must be retained.
        FakeContext preset=new FakeContext();preset.manager.app=new LocaleList(Locale.KOREAN);clearProcess();AppLanguage.synchronize(preset);equal("ko",AppLanguage.choice(preset));equal(Locale.KOREAN,Texts.locale(preset));
        // API 26 fallback uses the real system resources, not a previously wrapped activity.
        Build.VERSION.SDK_INT=26;FakeContext old=new FakeContext();Resources.getSystem().getConfiguration().setLocales(new LocaleList(Locale.KOREA));clearProcess();
        equal(Locale.KOREAN,Texts.locale(old));AppLanguage.apply(old,"en");equal(Locale.ENGLISH,Texts.locale(old));equal("en",AppLanguage.choice(old));equal(0,old.manager.writes);
        Resources.getSystem().getConfiguration().setLocales(new LocaleList(Locale.JAPANESE,Locale.KOREAN));AppLanguage.apply(old,"system");equal(Locale.ENGLISH,Texts.locale(old));equal("system",AppLanguage.choice(old));
        Resources.getSystem().getConfiguration().setLocales(new LocaleList(Locale.KOREA));AppLanguage.synchronize(old);equal(Locale.KOREAN,Texts.locale(old));
        old.getResources().getConfiguration().setLocales(new LocaleList(Locale.FRENCH));equal(Locale.KOREAN,Texts.locale(old));
        clearProcess();equal(Locale.KOREAN,Texts.locale(old));AppLanguage.apply(old,"ko");clearProcess();equal(Locale.KOREAN,Texts.locale(old));equal("ko",AppLanguage.choice(old));
        equal("한국어",Texts.t(old,"한국어","English"));AppLanguage.apply(old,"en");equal("English",Texts.t(old,"한국어","English"));
        // A locale broadcast is needed when the merged per-app configuration does not change.
        Build.VERSION.SDK_INT=33;FakeContext broadcast=new FakeContext();broadcast.manager.system=new LocaleList(Locale.KOREAN,Locale.ENGLISH);clearProcess();AppLanguage.synchronize(broadcast);
        int before=WeeklyWidget.renders;broadcast.manager.system=new LocaleList(Locale.ENGLISH,Locale.KOREAN);
        BootReceiver receiver=new BootReceiver();receiver.onReceive(broadcast,new Intent(Intent.ACTION_LOCALE_CHANGED));
        equal(Locale.ENGLISH,Texts.locale(broadcast));equal("system",AppLanguage.choice(broadcast));equal("en",broadcast.manager.app.toLanguageTags());equal(before+1,WeeklyWidget.renders);
        equal(0,Repo.operations);equal(0,Repo.reconciles);equal(0,Scheduler.ensures);equal(0,Store.errors);equal(0,BroadcastReceiver.asyncCalls);
        AppLanguage.apply(broadcast,"ko");before=WeeklyWidget.renders;receiver.onReceive(broadcast,new Intent(Intent.ACTION_LOCALE_CHANGED));
        equal(Locale.KOREAN,Texts.locale(broadcast));equal("ko",AppLanguage.choice(broadcast));equal(before+1,WeeklyWidget.renders);equal(0,Repo.operations);
        receiver.onReceive(broadcast,new Intent("android.intent.action.BOOT_COMPLETED"));
        equal(1,Repo.operations);equal(1,Repo.reconciles);equal(1,Scheduler.ensures);equal(1,BroadcastReceiver.asyncCalls);equal(1,BroadcastReceiver.finishes);
        System.out.println("AppLanguageTests: "+checks+" checks passed");
    }
    private static final class Prefs implements SharedPreferences {
        final Map<String,String> data=new HashMap<>();
        public String getString(String key,String fallback){return data.containsKey(key)?data.get(key):fallback;}
        public Editor edit(){return new Editor(){public Editor putString(String key,String value){data.put(key,value);return this;}public void apply(){}};}
    }
    private static final class FakeContext extends Context {
        final LocaleManager manager;final Map<String,Prefs> prefs;final Resources resources;
        FakeContext(){this(new LocaleManager(),new HashMap<String,Prefs>(),new Configuration());}
        FakeContext(LocaleManager manager,Map<String,Prefs> prefs,Configuration configuration){this.manager=manager;this.prefs=prefs;resources=new Resources(configuration);}
        public SharedPreferences getSharedPreferences(String name,int mode){if(!prefs.containsKey(name))prefs.put(name,new Prefs());return prefs.get(name);}
        public <T>T getSystemService(Class<T> type){return type==LocaleManager.class?type.cast(manager):null;}
        public Resources getResources(){return resources;}
        public Context createConfigurationContext(Configuration value){return new FakeContext(manager,prefs,value);}
    }
}
