# Збірка з телефона через GitHub Actions

Це спосіб отримати APK без Android Studio на телефоні. Сам APK ще не
зібраний; можливі помилки Android-білду, які тут неможливо перевірити.

1. Створи порожній приватний GitHub-репозиторій у браузері.
2. Завантаж у корінь репозиторію отриманий архів з ТОЧНОЮ назвою
   `TomRunPilot-source.zip`. Не розпаковуй його для цього способу.
3. Через Add file → Create new file створи
   `.github/workflows/phone-build.yml`.
4. Скопіюй у нього YAML нижче та збережи (Commit changes).
5. Відкрий Actions → Build APK from source ZIP → Run workflow.
6. Після успішної збірки відкрий її результат і завантаж artifact
   `TomRunPilot-experimental-APK`. Розпакуй artifact та встанови APK.

Якщо Actions завершується червоною помилкою, надішли лог помилки.
Приватний репозиторій потребує доступних хвилин Actions у твоєму акаунті.

```yaml
name: Build APK from source ZIP
on:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v4
      - run: unzip TomRunPilot-source.zip -d source
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.9'
      - run: sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
      - run: gradle :app:assembleDebug --no-daemon
        working-directory: source/TomRunPilot
      - uses: actions/upload-artifact@v4
        with:
          name: TomRunPilot-experimental-APK
          path: source/TomRunPilot/app/build/outputs/apk/debug/app-debug.apk
```
