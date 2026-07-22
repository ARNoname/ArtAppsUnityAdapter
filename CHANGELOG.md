# Changelog

## [1.0.5] - 2026-07-22

### Changed
- Replaced the one-shot iOS reachability check with a continuously updated network path monitor.
- Delayed iOS WebView loading until fullscreen presentation completes and rechecked connectivity before loading.
- Reported an interstitial as displayed only after the initial WebView navigation fully finishes.
- Enforced a single valid AppLovin MAX terminal callback sequence during offline and WebKit failures.
- Added iOS adapter version and lifecycle diagnostics for integration verification.

## [1.0.4] - 2026-05-04

### Changed
- Fixed Android interstitial presentation by launching the ad activity on the main thread.
- Kept the active MAX display listener alive while the Android ad activity is being shown.
- Added Android activity export metadata and consumer keep rules to the AAR.

## [1.0.3] - 2026-05-04

### Changed
- Bumped the Unity package version to 1.0.3 so Unity Package Manager reports the current release.
- Fixed iOS and Android interstitial display lifecycle handling for AppLovin MAX mediation.
- Improved invalid ad URL handling and display failure callbacks.

## [1.0.0] - 2026-03-03

### Added
- Initial UPM package release.
- iOS native adapter (Swift) for AppLovin MAX Mediation.
- Android native adapter (.aar) for AppLovin MAX Mediation.
- ProGuard rules for Android.
- External Dependency Manager configuration for Android dependencies.
