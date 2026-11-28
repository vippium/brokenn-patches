package app.brokenn.extension.jiohotstar.navbar;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import app.brokenn.extension.jiohotstar.settings.BrokennSettingsActivity;

import java.lang.reflect.Method;
import java.util.Locale;

@SuppressWarnings("unused")
public final class NavPress {

    private static final long DOUBLE_TAP_MS = 180L;
    private static final String PROFILE = "my space";

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static Method titleMethod = null;
    private static long lastProfileTap = 0L;

    private NavPress() {
    }

    public static Object navClick(Object lambda, Object item) {
        boolean profile = false;
        try {
            profile = PROFILE.equals(titleOf(item));
        } catch (Throwable ignored) {
        }
        // The Profile tab is the normal entry point, but it can be hidden from Brokenn
        // settings - then the bar's last tab answers the double tap instead.
        if (!profile && profileTabHidden() && isLastTab(item)) {
            profile = true;
        }
        if (!profile) {
            cancelPending();
            return invoke(lambda, item);
        }
        try {
            long now = SystemClock.elapsedRealtime();
            if (now - lastProfileTap <= DOUBLE_TAP_MS) {
                lastProfileTap = 0L;
                vibrate();
                launchSettings();
                return null;
            }
            lastProfileTap = now;
            handler.postDelayed(new DeferredTap(lambda, item, now), DOUBLE_TAP_MS);
        } catch (Throwable t) {
            return invoke(lambda, item);
        }
        return null;
    }

    private static void cancelPending() {
        lastProfileTap = 0L;
        handler.removeCallbacksAndMessages(null);
    }

    private static final class DeferredTap implements Runnable {

        private final Object lambda;
        private final Object item;
        private final long stamp;

        DeferredTap(Object lambda, Object item, long stamp) {
            this.lambda = lambda;
            this.item = item;
            this.stamp = stamp;
        }

        @Override
        public void run() {
            try {
                if (stamp != lastProfileTap) {
                    return;
                }
                lastProfileTap = 0L;
                invoke(lambda, item);
            } catch (Throwable t) {
            }
        }
    }

    private static Object invoke(Object lambda, Object item) {
        try {
            if (lambda == null) {
                return null;
            }
            Method method = lambda.getClass().getMethod("invoke", Object.class);
            method.setAccessible(true);
            return method.invoke(lambda, item);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isLastTab(Object item) {
        try {
            String title = titleOf(item);
            Context context = appContext();
            if (title == null || context == null) return false;
            String last = context.getSharedPreferences("brokenn_prefs", Context.MODE_PRIVATE)
                .getString("nav_last_tab", null);
            return last != null && last.equalsIgnoreCase(title.trim());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean profileTabHidden() {
        try {
            Context context = appContext();
            if (context == null) return false;
            String layout = context.getSharedPreferences("brokenn_prefs", Context.MODE_PRIVATE)
                .getString("nav_tab_layout", null);
            if (layout == null) return false;
            for (String part : layout.split("\\|")) {
                int sep = part.lastIndexOf(':');
                if (sep <= 0) continue;
                String id = part.substring(0, sep).trim();
                if (PROFILE.equalsIgnoreCase(id)
                    && part.substring(sep + 1).trim().equals("0")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String titleOf(Object item) {
        try {
            if (item == null) {
                return null;
            }
            if (titleMethod == null) {
                titleMethod = item.getClass().getMethod("getTitle");
                titleMethod.setAccessible(true);
            }
            Object title = titleMethod.invoke(item);
            if (!(title instanceof String)) {
                return null;
            }
            return ((String) title).trim().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void vibrate() {
        try {
            Context context = appContext();
            if (context == null) {
                return;
            }
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            }
        } catch (Throwable t) {
        }
    }

    private static void launchSettings() {
        try {
            Context context = appContext();
            if (context == null) {
                return;
            }
            Intent intent = new Intent();
            intent.setClassName(context, BrokennSettingsActivity.class.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
        }
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
