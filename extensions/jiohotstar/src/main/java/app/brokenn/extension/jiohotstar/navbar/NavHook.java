package app.brokenn.extension.jiohotstar.navbar;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime hook for the bottom navigation items.
 *
 * The app assembles its bottom bar items (BottomMenuNavItem) from the BFF menu and
 * hands them to the bar composable. Two hook points use this class:
 *
 * - processNavItems: right before the bar renders its list. It records the tab
 *   titles (and their icon glyphs) the app currently shows so the settings screen
 *   can offer them, then applies the saved layout.
 * - applyNavItems: on the assembler sites that store intermediate menu lists. It
 *   only applies the layout, so unrelated menu widgets can never pollute the tab
 *   editor's discovered list.
 *
 * Layout format:  Home:1|Search:1|My Space:0   (title:visible, in display order)
 */
@SuppressWarnings("unused")
public final class NavHook {

    private static final String LAYOUT_PREF = "nav_tab_layout";
    private static final String DISCOVERY_PREF = "nav_items_discovered";
    private static final String GLYPH_PREF = "nav_item_glyphs";
    private static final String LAST_TAB_PREF = "nav_last_tab";

    private static Method getTitle;

    private NavHook() {
    }

    /**
     * Called from the patched assembler right before a nav item list is stored.
     * Applies the saved layout only; discovery is recorded exclusively by the
     * render-side hook.
     */
    public static void applyNavItems(Object list) {
        processNavItems(list, false);
    }

    /** Called from the patched bottom bar composable right before the list is rendered. */
    public static void processNavItems(Object list) {
        processNavItems(list, true);
    }

    private static void processNavItems(Object list, boolean record) {
        try {
            if (!(list instanceof List)) {
                return;
            }
            List<?> items = (List<?>) list;
            if (items.isEmpty()) {
                return;
            }

            if (record) recordDiscovered(items);

            SharedPreferences p = prefs();
            String layout = p == null ? null : p.getString(LAYOUT_PREF, null);
            if (layout == null || layout.trim().length() == 0) {
                return;
            }

            Map<String, Boolean> order = parseLayout(layout);
            if (order.isEmpty()) {
                return;
            }

            List<Object> current = new ArrayList<>(items);
            List<Object> result = new ArrayList<>();
            // Items in saved order first (visible ones only).
            for (String key : order.keySet()) {
                if (!Boolean.TRUE.equals(order.get(key))) continue;
                Object item = findByTitle(current, key);
                if (item != null) {
                    result.add(item);
                    current.remove(item);
                }
            }
            // Items the server added after the layout was saved keep their relative
            // order at the end, unless the user explicitly hid them.
            for (Object item : current) {
                String title = titleOf(item);
                Boolean visible = title == null ? null : order.get(keyOf(title));
                if (visible == null || visible) {
                    result.add(item);
                }
            }
            if (result.isEmpty()) {
                // Never blank the bar.
                return;
            }

            @SuppressWarnings("unchecked")
            List<Object> backing = (List<Object>) list;
            backing.clear();
            backing.addAll(result);
            rememberLastTab(p, backing);
        } catch (Throwable ignored) {
            // Fail open: the app keeps its own order.
        }
    }

    /** Stores the title of the right-most tab, used as the settings fallback tap. */
    private static void rememberLastTab(SharedPreferences p, List<Object> items) {
        if (p == null || items.isEmpty()) return;
        String title = titleOf(items.get(items.size() - 1));
        if (title == null || title.trim().length() == 0) return;
        title = title.trim();
        if (!title.equals(p.getString(LAST_TAB_PREF, null))) {
            p.edit().putString(LAST_TAB_PREF, title).apply();
        }
    }

    private static void recordDiscovered(List<?> items) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        // The rendered list is the complete truth for the bar: replace, never merge,
        // so lists from other menu widgets cannot pollute it.
        StringBuilder sb = new StringBuilder();
        StringBuilder glyphs = new StringBuilder();
        for (Object item : items) {
            String title = titleOf(item);
            if (title == null || title.trim().length() == 0) continue;
            title = title.trim();
            if (sb.length() > 0) sb.append('\u0002');
            sb.append(title);
            char glyph = glyphOf(item);
            if (glyph != 0) {
                if (glyphs.length() > 0) glyphs.append('\u0002');
                glyphs.append(title).append('\u0001').append((int) glyph);
            }
        }
        String value = sb.toString();
        if (value.length() == 0) return;
        if (!value.equals(p.getString(DISCOVERY_PREF, null))) {
            p.edit().putString(DISCOVERY_PREF, value).apply();
        }
        // Merge: hidden tabs are not rendered any more, so their glyphs have to be
        // carried over from the previous run or the editor would show a dot instead.
        String previousGlyphs = p.getString(GLYPH_PREF, null);
        java.util.LinkedHashMap<String, Integer> merged = new java.util.LinkedHashMap<>();
        if (previousGlyphs != null) {
            for (String entry : previousGlyphs.split("\u0002")) {
                int sep = entry.indexOf('\u0001');
                if (sep <= 0) continue;
                try {
                    merged.put(entry.substring(0, sep),
                        Integer.parseInt(entry.substring(sep + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (glyphs.length() > 0) {
            for (String entry : glyphs.toString().split("\u0002")) {
                int sep = entry.indexOf('\u0001');
                if (sep <= 0) continue;
                try {
                    merged.put(entry.substring(0, sep),
                        Integer.parseInt(entry.substring(sep + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (!merged.isEmpty()) {
            StringBuilder out = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> e : merged.entrySet()) {
                if (out.length() > 0) out.append('\u0002');
                out.append(e.getKey()).append('\u0001').append(e.getValue());
            }
            String value2 = out.toString();
            if (!value2.equals(previousGlyphs)) {
                p.edit().putString(GLYPH_PREF, value2).apply();
            }
        }
    }

    /**
     * Icon glyph codepoint: nav items carry an icon font reference whose obfuscated
     * holder keeps the codepoint in field "b". The settings preview renders the same
     * glyphs with the app's soul_icons font, so it looks like the real bar.
     */
    private static char glyphOf(Object item) {
        try {
            Method icon = item.getClass().getMethod("getDefaultIcon");
            icon.setAccessible(true);
            Object holder = icon.invoke(item);
            if (holder == null) return 0;
            // The holder keeps its font codepoint in a plain char field; find it by type
            // instead of by its obfuscated name so app updates cannot break it.
            Field[] fields = holder.getClass().getDeclaredFields();
            for (Field f : fields) {
                if (f.getType() != char.class) continue;
                f.setAccessible(true);
                return f.getChar(holder);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static Map<String, Boolean> parseLayout(String layout) {
        Map<String, Boolean> order = new LinkedHashMap<>();
        for (String part : layout.split("\\|")) {
            if (part.trim().length() == 0) continue;
            int sep = part.lastIndexOf(':');
            if (sep <= 0) continue;
            String id = part.substring(0, sep).trim();
            boolean visible = !part.substring(sep + 1).trim().equals("0");
            if (id.length() > 0) order.put(keyOf(id), visible);
        }
        return order;
    }

    private static Object findByTitle(List<?> items, String key) {
        for (Object item : items) {
            String title = titleOf(item);
            if (title != null && keyOf(title).equals(key)) return item;
        }
        return null;
    }

    private static String keyOf(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Item title: BottomMenuNavItem has an unobfuscated getTitle(); the server-side
     * BFF models keep their display name in the obfuscated field "b".
     */
    private static String titleOf(Object item) {
        try {
            if (getTitle == null) {
                try {
                    getTitle = item.getClass().getMethod("getTitle");
                    getTitle.setAccessible(true);
                } catch (NoSuchMethodException ignored) {
                    // Fall through to the field lookup below.
                }
            }
            if (getTitle != null) {
                Object v = getTitle.invoke(item);
                if (v instanceof String) return (String) v;
            }
            Field f = item.getClass().getDeclaredField("b");
            f.setAccessible(true);
            Object v = f.get(item);
            return v instanceof String ? (String) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

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
}
