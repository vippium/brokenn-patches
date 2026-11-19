package app.brokenn.extension.jiohotstar.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Runtime settings store read by injected patch code.
 *
 * Values live in SharedPreferences "brokenn_prefs", written by
 * {@link BrokennSettingsActivity}. All readers fail open (return false)
 * so navigation/rendering never breaks if the context is unavailable.
 */
public final class BrokennPrefs {
    private BrokennPrefs() {}

    private static SharedPreferences prefs() {
        try {
            Context ctx = (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            if (ctx == null) return null;
            return ctx.getSharedPreferences("brokenn_prefs", Context.MODE_PRIVATE);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when the watch-page tab strip should drop the given tab title. */
    public static boolean isTabHidden(String title) {
        if (title == null) return false;
        String key;
        switch (title) {
            case "For You":  key = "hide_tab_for_you";  break;
            case "Jeeto":    key = "hide_tab_jeeto";    break;
            case "Memes":    key = "hide_tab_memes";    break;
            case "Live Chat": key = "hide_tab_live_chat"; break;
            default: return false;
        }
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(key, false);
    }

    /** True when the fullscreen player bottom bar should drop the given button title. */
    public static boolean isPlayerButtonHidden(String title) {
        if (title == null) return false;
        String key;
        switch (title) {
            case "Watch Next":   key = "hide_player_watch_next";    break;
            case "Episodes":     key = "hide_player_episodes";      break;
            case "Shop":         key = "hide_player_shop";          break;
            case "Order Food":   key = "hide_player_shop";          break;
            case "Speed":        key = "hide_player_speed";         break;
            case "Rate":         key = "hide_player_rate";          break;
            case "Next Episode": key = "hide_player_next_episode";  break;
            default: return false;
        }
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(key, false);
    }
    /**
     * Drops home feed tray widgets whose header title matches the hidden-shelf
     * list. Called on the feed assembler's widget list right before it is
     * consumed, so both the parallel-stream and iterator paths are covered;
     * returns the filtered list (the same instance when nothing is hidden).
     *
     * [Implementation] reflects on BffTabbedFeedWidget.d (BffTabbedFeedHeader)
     * and reads the header's title from field b — class and field names are
     * stable in the pinned app version's dexes.
     */
    public static java.util.List filterShelfList(java.util.List listObj) {

        java.util.List<?> list = (java.util.List<?>) listObj;
        SharedPreferences p = prefs();
        String raw = p == null ? null : p.getString("hidden_shelves", "");
        if (raw == null || raw.length() == 0) return list;

        java.util.ArrayList<String> needles = new java.util.ArrayList<String>();
        for (String part : raw.split("[,\\n]")) {
            String needle = normalizeTitle(part);
            if (needle.length() > 0) needles.add(needle);
        }
        if (needles.isEmpty()) return list;

        java.util.ArrayList<Object> keep = new java.util.ArrayList<Object>();
        int dropped = 0;
        for (Object item : list) {
            if (item != null && isHiddenWidget(item, needles)) {
                dropped++;
                android.util.Log.i("Brokenn", "feed filter: dropped tray "
                    + item.getClass().getSimpleName());
                continue;
            }
            keep.add(item);
        }
        if (dropped > 0) {
            android.util.Log.i("Brokenn", "feed filter: " + dropped + " tray(s) dropped");
        }
        return keep;
    }

    /**
     * True when any plausible title string inside the widget's model tree
     * matches the hidden list. Tray headers come in several variants
     * (regular/surface/filter) with the title at different depths, so the
     * walk is generic instead of model-specific.
     */
    private static boolean isHiddenWidget(Object widget, java.util.List<String> needles) {
        java.util.ArrayList<String> found = new java.util.ArrayList<String>();
        collectStrings(widget, found, 0);
        for (String s : found) {
            if (isHiddenTitle(s, needles)) return true;
        }
        return false;
    }

    /**
     * Collects candidate title strings from the object tree: String fields
     * that are neither empty, URLs, nor UUIDs, plus nested model objects up
     * to depth 3. Static fields and collections are skipped.
     */
    private static void collectStrings(Object obj, java.util.List<String> out, int depth) {
        if (obj == null || depth > 3 || out.size() > 24) return;
        Class<?> c = obj.getClass();
        String cname = c.getName();
        if (cname.startsWith("java.") || cname.startsWith("kotlin.")) return;
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof String) {
                    String s = (String) v;
                    if (s.length() > 1 && !s.startsWith("/") && !s.contains("://")
                        && !(s.length() == 36 && s.charAt(8) == '-')) {
                        out.add(s);
                    }
                } else if (v != null && !(v instanceof Number) && !(v instanceof Boolean)
                    && !(v instanceof java.util.List)) {
                    collectStrings(v, out, depth + 1);
                }
            } catch (Throwable t) {
                // skip unreadable field
            }
        }
    }

    private static boolean isHiddenTitle(String title, java.util.List<String> needles) {
        String lower = normalizeTitle(title);
        for (String needle : needles) {
            if (lower.equals(needle)) return true;
        }
        return false;
    }

    private static String normalizeTitle(String title) {
        if (title == null) return "";
        return title.replace('\u2019', '\'').replace('\u00A0', ' ')
            .trim().toLowerCase().replaceAll(" +", " ");
    }

    /** True when sponsored brand tabs (Shop, Order Food, ...) should be dropped. */
    public static boolean hideBrandTabs() {
        return flag("hide_brand_tabs");
    }

    /** True when floating sponsored pills should be dropped. */
    public static boolean hideSponsoredPills() {
        return flag("hide_sponsored_pills");
    }

    private static boolean flag(String key) {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(key, false);
    }
}
