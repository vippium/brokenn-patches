# Add monochrome icon support (JioHotstar)

Adds a themed-icon (`<monochrome>`) layer to the JioHotstar launcher icon.

## Target

- App: JioHotstar, package `in.startv.hotstar`
- Version: **26.10.12.2**
- Stock icon: `res/mipmap-anydpi-v26/ic_launcher.xml` — background +
  foreground only, no monochrome layer.

## What it does

1. Copies `ic_launcher_monochrome.webp` (in
   `patches/src/main/resources/jiohotstar/`) to
   `res/drawable/ic_launcher_monochrome.webp` in the patched APK.
   The image is the stock foreground star
   (`res/drawable/ic_launcher_foreground.webp`, 324×324) with colour
   and gold shading removed — a plain white silhouette with the
   original alpha mask, same size and shape as stock. The system
   tints it for themed icons (Android 13+).
2. Adds `<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>`
   to `ic_launcher.xml`. An existing monochrome layer, if any, is
   repointed rather than duplicated.

Only the main icon is patched; the seasonal variants
(`ic_launcher_tadka_ipl_2026`, `ic_launcher_tiranga_2025`) are untouched.

## Notes

- This is a `resourcePatch`, not a bytecode patch — it decodes and
  rebuilds resources, so it is slower than the other JioHotstar patches.
