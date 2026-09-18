package android.app;
import android.content.Context;
public class ActivityManager {public boolean restricted;public Throwable failure;public int reads;public boolean isBackgroundRestricted(){reads++;Context.raise(failure);return restricted;}}
