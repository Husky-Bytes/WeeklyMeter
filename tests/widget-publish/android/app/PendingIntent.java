package android.app;
import android.content.*;
public final class PendingIntent {public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;public final Intent intent;public final int flags;private PendingIntent(Intent i,int f){intent=i;flags=f;}public static PendingIntent getForegroundService(Context c,int request,Intent i,int flags){return new PendingIntent(i,flags);}}
