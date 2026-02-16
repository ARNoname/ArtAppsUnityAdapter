# ArtApps Custom Adapter for Unity

This package integrates the ArtApps Ad Network via AppLovin MAX Mediation.

## Installation Instructions

1.  **Import the Package**
    -   Open your Unity project.
    -   Go to **Assets** -> **Import Package** -> **Custom Package...**
    -   Select the `ArtAppsUnityAdapter.unitypackage` file.
    -   Click **Import** (include all files).

2.  **Configuration (AppLovin Dashboard)**
    -   In your AppLovin MAX dashboard, go to **Manage Networks**.
    -   Select your **Custom Network**.
    -   Set the **Adapter Class Name** to: `ArtAppsMaxAdapter`.

3.  **Platform Support**
    -   **iOS**: Supported. The native adapter source files are included and will be automatically added to the Xcode project during the build process. No manual steps required.
    -   **Android**: *Not included in this version.* Please contact support if Android integration is required.
