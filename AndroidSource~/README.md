# Android adapter source

Unity ignores this directory because its name ends with `~`.

Build the release AAR with Android Studio's JDK and a configured Android SDK:

```sh
./gradlew :artapps-adapter:assembleRelease
```

Copy the resulting artifact from
`artapps-adapter/build/outputs/aar/artapps-adapter-release.aar` to
`Runtime/Plugins/Android/artapps-adapter-release.aar`.
