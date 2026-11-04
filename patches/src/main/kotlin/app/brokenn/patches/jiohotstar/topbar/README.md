# Remove top bar switcher (JioHotstar)

Removes the whole mode-switcher row at the top of the home screen — both
the "JioHotstar" pill and the "TADKA" pill — so the home feed starts
directly, with no switcher above it.

## Universal fingerprints (26.08.17.3 + 26.10.12.2)

Version-agnostic: the change between builds was pure R8 renaming; the
code is identical in both (verified by full-dex scan — the content
literal pair and the container restart key each match exactly 1 method
per build). Fingerprints use only restart keys, literals, a stable
androidx call, and signatures — no obfuscated names. The patch body
finds its targets by scanning and fails loudly on a scan miss instead
of patching the wrong instruction.

Compatibility is topbar-only: 26.08 + 26.10 confirmed, any future
version attempted experimentally (scan miss = loud failure); the other patches are
verified for 26.10 only.

## Test checklist (confirm on BOTH builds in Morphe)

1. Patch applies cleanly — no fingerprint failure.
2. Home opens with no switcher pills; a small gap remains at the very
   top (the banner does not slide under the status bar).
3. Scrolling: the bar collapses; it must NOT come back mid-feed or
   partway through the first item — only at the very top (offset 0).
4. Tab switches cause no bounce or ghosting.
5. Feed, search, bottom nav, and playback still work.
6. On failure, the first failing require() message names the scan that
   missed — send it with the exact app version.
