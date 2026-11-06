# Remove top bar switcher (JioHotstar)

Removes the whole mode-switcher row at the top of the home screen — both
the "JioHotstar" pill and the "TADKA" pill — so the home feed starts
directly, with no switcher above it.

## Fingerprints

The patch scans for stable literals and method signatures instead of
hardcoding obfuscated names. A scan miss fails loudly instead of
patching an unrelated instruction.

The patch is verified for JioHotstar `26.10.12.2` only.

## Test checklist

1. Patch applies cleanly — no fingerprint failure.
2. Home opens with no switcher pills; a small gap remains at the very
   top (the banner does not slide under the status bar).
3. Scrolling: the bar collapses; it must NOT come back mid-feed or
   partway through the first item — only at the very top (offset 0).
4. Tab switches cause no bounce or ghosting.
5. Feed, search, bottom nav, and playback still work.
6. On failure, the first failing `require()` message identifies the
   missing scan.
