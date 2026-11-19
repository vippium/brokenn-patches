package app.brokenn.extension.jiohotstar.settings;

/**
 * Patch presence flags (Piko-style SettingsStatus).
 *
 * Every feature patch injects a call to its enable method into {@link #load} at
 * patch time, so a flag is true only when the user actually included that patch.
 * The settings screen reads these flags to decide which sections to render;
 * patches that were excluded leave their flag false and their entry is hidden.
 */
public final class BrokennStatus {
    private BrokennStatus() {}

    /** "Hide player buttons" patch was applied. */
    public static boolean hidePlayerButtons = false;
    public static void hidePlayerButtons() { hidePlayerButtons = true; }

    /** "Hide watch page elements" patch was applied (tabs, brand tabs, pills). */
    public static boolean hideWatchPageElements = false;
    public static void hideWatchPageElements() { hideWatchPageElements = true; }

    public static boolean feedFilter = false;
    public static void feedFilter() { feedFilter = true; }

    /**
     * Entry point for the injected enable calls. Called by the settings screen
     * before rendering; the patcher prepends one enable call per included patch.
     */
    public static void load() {}
}
