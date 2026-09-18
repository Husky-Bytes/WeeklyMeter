package android.os;
import android.content.Context;
public class PowerManager {
 public boolean powerSave,batteryExempt;public Throwable powerFailure,exemptFailure;public int powerReads,exemptReads;public String queriedPackage;
 public boolean isPowerSaveMode(){powerReads++;Context.raise(powerFailure);return powerSave;}
 public boolean isIgnoringBatteryOptimizations(String packageName){exemptReads++;queriedPackage=packageName;Context.raise(exemptFailure);return batteryExempt;}
}
