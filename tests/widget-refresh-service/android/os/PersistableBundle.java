package android.os;
public class PersistableBundle {
    private final java.util.Map<String,Long> values=new java.util.HashMap<>();
    public void putLong(String key,long value){values.put(key,value);}
    public long getLong(String key,long fallback){return values.containsKey(key)?values.get(key):fallback;}
}
