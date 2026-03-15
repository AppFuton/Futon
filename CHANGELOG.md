# Changelog

All notable changes to this project are documented in this file.

The format is based on "Keep a Changelog" and follows semantic versioning where possible.

## [Unreleased]
## 9.7
Date: Late March 2026

### Highlights
- UI reworks and new features.

- (Work in progress)


---
## v9.6.13
Date: 2026-03-15

### Highlights
- Made Crash Analytics opt-in only, disabled by default.

### Fixes
- Fixed Build Fail due to missing Sentry auth token.

---

## v9.6.12
Date: 2026-03-12

### Highlights
- Added opt-in crash analytics (Sentry) — disabled by default, can be enabled in Settings.

### Maintenance
- Parser upgrades and dependency/maintenance updates.

---

## v9.6.11
Date: 2026-02-27

### Highlights
- Added 'Reading' quick filter (#30).
- Disabled empty sources in search results by default.

### Fixes
- Resolved chapter progress display issue (#28).
- Refactored coroutine jobs to use IO dispatchers instead of Default.
- Various internal bug fixes and performance tweaks.

### Maintenance
- Parser upgrades and dependency/maintenance updates.
- Documentation updates / refactoring.

---

## v9.6.10
Date: 2026-01-02

### Highlights
- Resource cleanup and translation fixes.

### Fixes
- Removed noisy resource tags and warnings that appeared during builds.
- Updated a number of translation summaries to improve localization clarity.

### Maintenance
- Small dependency and resource tidy-ups.

---

## v9.6.9
Date: 2025-12-31

### Highlights
- Parser upgrade and stability improvements.

### Fixes
- Bumped futon-parsers to a newer revision to address multiple source parsing issues.
- Small compatibility fixes and stability improvements related to the parser upgrade.

---

## v9.6.8
Date: 2025-12-30

### Highlights
- Packaging and version alignment.

### Fixes
- Updated AndroidManifest versionName and versionCode to align with build tooling.
- Minor packaging fixes to ensure release artifacts are consistent across CI.

---

## v9.6.7
Date: 2025-12-29

### Highlights
- Stability fixes and minor packaging updates.

### Fixes
- Integrated several small bugfix PRs and packaging tweaks.

---

## v9.6.6
Date: 2025-12-27

### Highlights
- Build and tooling maintenance.

### Fixes
- Small build system and tooling fixes to improve CI and local builds.

---

## v9.6.5
Date: 2025-12-27

### Highlights
- Build file updates and minor fixes.

### Fixes
- Corrected build-time issues affecting release generation.

---

## v9.6.4
Date: 2025-12-24

### Highlights
- Hotfix: build/syntax correction.

### Fixes
- Emergency fix to resolve a build-syntax error that blocked releases.

---

## v9.6.3
Date: 2025-12-23

### Highlights
- Downloads UX and wording updates.

### Fixes
- Added/clarified downloads disclaimer and related wording shown to users.
- Small UX improvements around downloads and offline content handling.

---

## v9.6.2
Date: 2025-12-22

### Highlights
- EventFlow reliability improvements.

### Fixes
- Replaced generic event handling with observeEvent for EventFlow to prevent missed or duplicate events.

---

## v9.6.1
Date: 2025-12-22

### Highlights
- Rebrand to Futon; packaging and translation updates.

### Fixes
- Rebranded app resources and package names (Kotatsu → Futon); updated icons and assets.
- Fixed IzzyOnDroid / F-Droid packaging issues; applied release workflow permission fixes.
- Parser and dependency updates, and a large set of translations from Weblate.
- Multiple crash fixes, UI tweaks, and reader improvements.

---

## v9.6
Date: 2025-12-21

### Highlights
- Major 9.6 milestone: UI, downloads, and CI improvements.

### Fixes
- Introduced Downloads UI and multiple usability improvements across the reader and settings.
- Significant CI and build workflow enhancements to stabilize releases.

---

## v9.5
Date: 2025-12-11

### Highlights
- Added Downloads viewer and UX improvements.

### Fixes
- New Downloads button and viewer to manage downloaded manga.
- Various UI and behavior fixes; parser maintenance and translation updates.
