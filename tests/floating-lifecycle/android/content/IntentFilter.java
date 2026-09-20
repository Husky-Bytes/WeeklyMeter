package android.content;
import java.util.HashSet;
import java.util.Set;
public final class IntentFilter { final Set<String> actions=new HashSet<>(); public void addAction(String action){actions.add(action);} }
