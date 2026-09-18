package android.os;
import java.util.*;
public class Bundle {private final Map<String,Object> values=new HashMap<>();public int getInt(String key,int fallback){Object value=values.get(key);return value==null?fallback:(Integer)value;}public void putInt(String key,int value){values.put(key,value);}public <T>void putParcelableArrayList(String key,ArrayList<T> value){values.put(key,value);}@SuppressWarnings("unchecked")public <T>ArrayList<T> getParcelableArrayList(String key){return (ArrayList<T>)values.get(key);}}
