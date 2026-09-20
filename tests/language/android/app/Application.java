package android.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
/** Delegates framework context access to an isolated fake context in lifecycle tests. */
public class Application extends Context {
    public Context base;
    public void onCreate(){}
    public void onConfigurationChanged(Configuration configuration){}
    public SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences(name,mode);}
    public <T>T getSystemService(Class<T> type){return base.getSystemService(type);}
    public Resources getResources(){return base.getResources();}
    public Context createConfigurationContext(Configuration value){return base.createConfigurationContext(value);}
}
