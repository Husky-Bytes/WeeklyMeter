package android.content.res;
public class Resources {
    private static final Resources SYSTEM=new Resources(new Configuration());
    private final Configuration configuration;
    public Resources(Configuration value){configuration=value;}
    public static Resources getSystem(){return SYSTEM;}
    public Configuration getConfiguration(){return configuration;}
}
