# ArtApps Custom Adapter for Unity

ArtApps Ad Network adapter for AppLovin MAX Mediation.

## Installation (Unity Package Manager)

1. Open your Unity project.
2. Go to **Window** → **Package Manager**.
3. Click the **+** button in the top-left corner.
4. Select **Add package from git URL...**
5. Enter the following URL:

```
https://github.com/ARNoname/ArtAppsUnityAdapter.git
```

6. Click **Add** and wait for the package to import.

> **Platforms:**
> - **iOS**: The native Swift adapter source files are included and will be automatically added to the Xcode project during the build process. No manual steps required.
> - **Android**: The `.aar` library, `AndroidManifest.xml`, and ProGuard rules are included. No manual steps required.

## Configuration (AppLovin Dashboard)

1. In your AppLovin MAX dashboard, go to **Manage Networks**.
2. Select your **Custom Network** (e.g., ArtApps).
3. Set the **Adapter Class Name** for **iOS** to: `ArtAppsMaxAdapter`.
4. Set the **Adapter Class Name** for **Android** to: `com.artapps.sdk.ArtAppsMaxAdapter`.

## Troubleshooting

- **"Invalid Request: Ineligible Ad Unit" (Code 1035)**: This is normal for new Ad Units. It may take 30–60 minutes for AppLovin servers to propagate the new ID.
- **Android Build Errors**: Ensure `ArtAppsWebViewActivity` is declared in your Manifest (included by default).
