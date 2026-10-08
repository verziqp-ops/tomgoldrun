# APK update signing

The APK delivered as `TomRunPilot-0.8-learning.apk` is signed with a retained project development key. Its signer SHA-256 is:

`80a5845a49ca3cf0c7f3f39d43944cb8d11cb3ed83dbcbda50847b47f49c9bae`

The private keystore is kept outside the public repository. Do not commit it, publish it as a GitHub Actions artifact, or generate a replacement key for future delivered APKs. The retained file is named `TomRunPilot-update-key.keystore`, with alias `tomrunpilot`.

GitHub Actions currently produces a normal debug-signed build with an ephemeral runner key. Before delivering a future update, re-sign the downloaded build using the retained project key and `tools/sign-apk.sh`, then verify the signature and that code/resources match the tested build.

Android can preserve the app's learned data across updates only when the package ID and compatible signing identity are maintained. Versions 0.1–0.7 used other debug keys and cannot be directly updated to this delivered APK; remove the old app once before installing 0.8. There is no online-learning data in those older versions. Do not uninstall 0.8 for subsequent updates: that deletes its experience and model.

This is development signing for this prototype, not a Play Store production release setup.
