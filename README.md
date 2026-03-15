> [!NOTE]
> **Futon is a fork of [Kotatsu](https://github.com/KotatsuApp/Kotatsu)** — a free and open-source manga reader for Android.
> 
> This fork continues development and maintenance of the manga reader, building upon the excellent foundation created by the Kotatsu team.

> [!WARNING]
> Most of the sources are currently outdated or just straight up nonexistent. I am sorry for this as I've never been able to completely fix all the sources.(They will be fixed as soon as im done with some work that I've got)
>
> But a lot of the sources do actually still work and work really well so dont worry about you not being able to enjoy manga
>
> We welcome contributions if anyone is willing to provide fixes for any sources.
---

<div align="center">

**Futon is a free and open-source manga reader for Android with built-in online content sources.**

![Android 6.0](https://img.shields.io/badge/android-6.0+-brightgreen) [![Sources count](https://img.shields.io/badge/dynamic/yaml?url=https%3A%2F%2Fraw.githubusercontent.com%2FAppFuton%2Ffuton-parsers%2Frefs%2Fheads%2Fmaster%2F.github%2Fsummary.yaml&query=total&label=manga%20sources&color=%23E9321C)](https://github.com/AppFuton/futon-parsers) [![License](https://img.shields.io/github/license/AppFuton/Futon)](https://github.com/AppFuton/Futon/blob/devel/LICENSE) [![GitHub Release](https://img.shields.io/github/v/release/appfuton/futon?sort=date&display_name=tag&style=flat&link=https%3A%2F%2Fgithub.com%2FAppFuton%2FFuton%2Freleases%2Flatest)](https://github.com/AppFuton/Futon/releases/latest) [![IzzyOnDroid Yearly Downloads](https://img.shields.io/badge/dynamic/json?url=https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/yearly/rolling.json&query=$.['io.github.landwarderer.futon']&label=IzzyOnDroid%20yearly%20downloads)](https://apt.izzysoft.de/packages/io.github.landwarderer.futon) [![F-Droid Version](https://img.shields.io/badge/F--Droid-%2311AB00.svg?logo=f-droid&logoColor=white)](https://f-droid.org/en/packages/io.github.landwarderer.futon/) [![Open Source Helpers](https://www.codetriage.com/appfuton/futon/badges/users.svg)](https://www.codetriage.com/appfuton/futon) [![Discord](https://img.shields.io/discord/1452862077134700628)
](https://discord.gg/9sqBHXhwzz)


### Main Features

<div align="left">

* Online [manga catalogues](https://github.com/AppFuton/futon-parsers) (with 1200+ manga sources)
* Search manga by name, genres and more filters
* Favorites organized by user-defined categories
* Reading history, bookmarks and incognito mode support
* Download manga and read it offline. Third-party CBZ archives are also supported
* Clean and convenient Material You UI, optimized for phones, tablets and desktop
* Standard and Webtoon-optimized customizable reader, gesture support on reading interface
* Notifications about new chapters with updates feed, manga recommendations (with filters)
* Integration with manga tracking services: Shikimori, AniList, MyAnimeList, Kitsu
* Password / fingerprint-protected access to the app
* Automatically sync app data with other devices on the same account
* Support for older devices running Android 6.0+

</div>
<div align="left">
## Development Setup

### Prerequisites

- **JDK 17** (recommended: [Temurin](https://adoptium.net/temurin/releases/) distribution)
- **Android SDK** (compile SDK 36, build tools 35.0.0, minimum SDK 23)
- **Android Studio** (recommended) or Android SDK command-line tools

### Building the Project

1. **Clone the repository:**
   ```bash
   git clone https://github.com/AppFuton/Futon.git
   cd Futon
   ```

2. **Build debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`

3. **Build release APK:**
   ```bash
   ./gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`
   
   *Note: Requires keystore setup via environment variables or `local.properties`*

4. **Build nightly APK:**
   ```bash
   ./gradlew assembleNightly
   ```
   Output: `app/build/outputs/apk/nightly/app-nightly.apk`

### Running Tests

- **Unit tests:**
  ```bash
  ./gradlew test
  ```

- **Instrumented tests:**
  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

### Code Quality

- **Lint check:**
  ```bash
  ./gradlew lint
  ```

- **Full check (lint + tests):**
  ```bash
  ./gradlew check
  ```

For detailed contribution guidelines, see [CONTRIBUTING.md](./CONTRIBUTING.md).
</div>
### In-App Screenshots

<div align="center">
    <img src="./metadata/en-US/images/phoneScreenshots/1.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/2.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/3.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/4.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/5.png" alt="Mobile view" width="250"/>
    <img src="./metadata/en-US/images/phoneScreenshots/6.png" alt="Mobile view" width="250"/>
</div>

<br>

<div align="center">
    <img src="./metadata/en-US/images/tenInchScreenshots/1.png" alt="Tablet view" width="400"/>
    <img src="./metadata/en-US/images/tenInchScreenshots/2.png" alt="Tablet view" width="400"/>
</div>

### Downloads

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="80" alt="Get it at IzzyOnDroid">](https://apt.izzysoft.de/packages/io.github.landwarderer.futon)
[<img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/io.github.landwarderer.futon)


### Contributing

<br>

**📌 Pull requests are welcome. See [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.**

### Community

Join our Discord for support, discussion, and announcements: https://discord.gg/9sqBHXhwzz

### Certificate fingerprints

```plaintext
EF:48:B2:2E:F2:C5:40:45:53:1F:6E:76:00:C2:7E:C3:D0:3B:71:22:1E:0B:05:FF:B6:8E:33:57:CF:8E:4D:40
```

### License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

<div align="left">

You may copy, distribute and modify the software as long as you track changes/dates in source files. Any modifications
to or software including (via compiler) GPL-licensed code must also be made available under the GPL along with build &
install instructions.

</div>

### DMCA disclaimer

<div align="left">

The developers of this application do not have any affiliation with the content available in the app and does not store
or distribute any content. This application should be considered a web browser, all content that can be found using this
application is freely available on the Internet. All DMCA takedown requests should be sent to the owners of the website
where the content is hosted.

</div>

---

### Acknowledgments

<div align="left">

**Futon is built upon the exceptional work of the [Kotatsu](https://github.com/KotatsuApp/Kotatsu) project.**

We are deeply grateful to:

* **The original Kotatsu developers** for creating such an outstanding manga reader and making it open source
* **The Kotatsu community** for their contributions, testing, and support
* **All translators** who helped localize Kotatsu through [Weblate](https://hosted.weblate.org/engage/kotatsu/)
* **Parser contributors** who maintain the extensive library of [manga sources](https://github.com/AppFuton/futon-parsers)

This project stands on the shoulders of giants. The Kotatsu team's dedication to creating a feature-rich, user-friendly manga reader has provided an incredible foundation for Futon to build upon.

**Thank you to everyone who contributed to Kotatsu — your work continues to benefit the manga reading community!**

For the original Kotatsu project, please visit: [github.com/KotatsuApp/Kotatsu](https://github.com/KotatsuApp/Kotatsu)

</div>
