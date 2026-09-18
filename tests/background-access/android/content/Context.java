package android.content;
import java.util.*;
public class Context {
 public final Map<Class<?>,Object> services=new HashMap<>();public final Map<Class<?>,Throwable> failures=new HashMap<>();public final List<Class<?>> reads=new ArrayList<>();
 public <T>T getSystemService(Class<T> type){reads.add(type);raise(failures.get(type));return type.cast(services.get(type));}
 public String getPackageName(){return "dev.yerin.weeklymeter";}
 public static void raise(Throwable failure){if(failure instanceof RuntimeException)throw (RuntimeException)failure;if(failure instanceof Error)throw (Error)failure;}
}
