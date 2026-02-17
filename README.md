# ArtApps Custom Adapter for Unity

This package integrates the ArtApps Ad Network via AppLovin MAX Mediation.

## Installation Instructions

1.  **Import the Package**
    -   Open your Unity project.
    -   Go to **Assets** -> **Import Package** -> **Custom Package...**
    -   Select the `ArtAppsUnityAdapter.unitypackage` file.
    -   Click **Import** (include all files).

    -   **iOS**: Supported. The native adapter source files are included and will be automatically added to the Xcode project during the build process. No manual steps required.
    -   **Android**: Supported. The `.aar` library, `AndroidManifest.xml`, and ProGuard rules are included in `Plugins/Android/`. No manual steps required.

4.  **Configuration (AppLovin Dashboard)**
    -   In your AppLovin MAX dashboard, go to **Manage Networks**.
    -   Select your **Custom Network** (e.g., ArtApps).
    -   Set the **Adapter Class Name** for **iOS** to: `ArtAppsMaxAdapter`.
    -   Set the **Adapter Class Name** for **Android** to: `com.artapps.sdk.ArtAppsMaxAdapter`.

5.  **Troubleshooting**
    -   **"Invalid Request: Ineligible Ad Unit" (Code 1035)**: This is normal for new Ad Units. It may take 30-60 minutes for AppLovin servers to propagate the new ID.
    -   **Android Build Errors**: Ensure `ArtAppsWebViewActivity` is declared in your Manifest (included by default).
