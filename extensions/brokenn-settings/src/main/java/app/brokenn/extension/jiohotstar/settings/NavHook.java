package app.brokenn.extension.jiohotstar.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

// Nav click hook: a double-tap on Home opens Brokenn settings, single taps pass through.
@SuppressWarnings("unused")
public final class NavHook {

    // Second Home tap within this window (ms) opens settings.
    private static final long DOUBLE_TAP_MS = 200L;

    private static long lastHomeTapAt = 0L;
    private static java.lang.reflect.Method getTitleMethod;

    private NavHook() {
    }

    // Called before the nav click is dispatched; true when the tap was consumed.
    public static boolean intercept(Object item) {
        try {
            if (item == null) {
                return false;
            }

            Object title = getTitle(item);
            if (!(title instanceof String) || !"home".equalsIgnoreCase(((String) title).trim())) {
                return false;
            }

            long now = SystemClock.elapsedRealtime();
            long last = lastHomeTapAt;
            lastHomeTapAt = now;
            if (last == 0L || now - last > DOUBLE_TAP_MS) {
                return false;
            }
            lastHomeTapAt = 0L;

            vibrate();
            return launchSettings();
        } catch (Throwable t) {
            // Never break navigation because of the hook.
            return false;
        }
    }

    /** Haptic click confirming the double-tap was recognized. */
    private static void vibrate() {
        try {
            Context context = appContext();
            if (context == null) return;
            Object service = context.getSystemService(Context.VIBRATOR_SERVICE);
            if (!(service instanceof Vibrator)) return;
            Vibrator vibrator = (Vibrator) service;
            if (Build.VERSION.SDK_INT >= 29) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            } else if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(80,
                    VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(80);
            }
        } catch (Throwable t) {
            // Haptics are cosmetic; never break navigation.
        }
    }

    /** Opens the Brokenn settings screen; true when the launch was issued. */
    private static boolean launchSettings() {
        Context context = appContext();
        if (context == null) {
            return false;
        }
        Intent intent = new Intent();
        intent.setClassName(context, BrokennSettingsActivity.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    private static Object getTitle(Object item) throws Exception {
        if (getTitleMethod == null) {
            getTitleMethod = item.getClass().getMethod("getTitle");
            getTitleMethod.setAccessible(true);
        }
        return getTitleMethod.invoke(item);
    }

    private static Context appContext() {
        try {
            return (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
