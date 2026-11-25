# NSE STREAM (`v1.0.1`)

**NSE STREAM** is a dark-cinema Android movie discovery and streaming application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **AndroidX Media3 ExoPlayer**.

- **Version:** `1.0.1` (`versionCode = 2`)
- **Release Tag:** `v1.0.1`
- **Application ID:** `com.aistudio.nsestream.kxvqpm`
- **Android Support:** Min SDK 24 (Android 7.0) · Target & Compile SDK 36.1

---

## Features & v1.0.1 Updates

- **Clean Dark & Crimson UI:** Responsive Jetpack Compose layout with dedicated horizontal category rows (**Bangla Movies**, **Hollywood Movies**, **Bollywood Movies**, **South Indian**, **Dual Audio**), infinite scroll, **See All** grids, **My List** watchlist, and **Continue Watching** history.
- **Centralized Server Configuration:** Configurable API base URL (`BuildConfig.DEFAULT_API_BASE_URL` + in-app **Server Settings** dialog) with zero hardcoded private credentials in the APK.
  - Default catalog uses legitimate public-domain and open-archive cinema via Internet Archive's official JSON Search & Metadata APIs (`https://archive.org`).
  - Supports custom authorized JSON REST servers exposing `/api/movies`, `/api/search`, and `/api/movies/{id}`.
- **Media3 ExoPlayer Playback:**
  - Supports **HLS (`.m3u8`)** and **MP4** streams with automatic MIME detection and fast-start buffering.
  - Dedicated **Rotate** toggle (`Portrait ⇄ Landscape`) with zero-crop `RESIZE_MODE_FIT` preserving the original video aspect ratio.
  - Double-tap ±10s seek gestures, scrub bar, quality switcher, and 25-second stall timeout guard.
  - Clear **Stream Unavailable** error recovery overlay with **Retry**, **Switch Quality**, and **Go Back** actions.
- **Stability & Error Handling:** Defensive JSON parsing, connectivity checks, debounced live search, HEIF/AVIF safe image decoding, and network error cards with **Retry** across Home, Category, Search, and Detail screens.

---

## Building & Releasing on GitHub (`v1.0.1`)

This repository includes two ready-to-run GitHub Actions workflows in `.github/workflows/`:

1. **`.github/workflows/android-apk.yml`** — Builds `NSE-Stream-<tag>-debug.apk` (plus a signed release APK when signing secrets are provided), generates `SHA256SUMS.txt`, uploads downloadable workflow artifacts, and automatically publishes a **GitHub Release** for any `v*` tag push.
2. **`.github/workflows/android-tests.yml`** — Runs the full JVM unit test and Robolectric Compose UI test suite (`gradle :app:testDebugUnitTest`) on every push, pull request, and tag.

### Option A — Trigger Automatically via Git Tag (`v1.0.1`)

Push your commits and create the `v1.0.1` tag:

```bash
git add .
git commit -m "Release NSE STREAM v1.0.1"
git push origin main

git tag -a v1.0.1 -m "NSE STREAM v1.0.1"
git push origin v1.0.1
```

GitHub Actions will automatically:
1. Set up **JDK 21**, **Gradle 9.3.1**, and **Android SDK 36.1**.
2. Build `NSE-Stream-v1.0.1-debug.apk` and compute `SHA256SUMS.txt`.
3. Attach the APK directly to the **GitHub Release** for tag `v1.0.1` under **Releases** and as a build artifact under **Actions**.

### Option B — Build Manually from GitHub Actions UI

1. Open your repository on GitHub and click the **Actions** tab.
2. Select **Build & Release NSE Stream APK**.
3. Click **Run workflow**, keep `release_tag` as `v1.0.1`, check **Create or update GitHub Release with APK attached**, and click **Run workflow**.
4. Download the APK from **Releases → v1.0.1** or from the workflow run's **Artifacts** section.

### Optional: Signed Release APK on GitHub Actions

Debug APKs build out of the box with no secrets required. To also produce a signed `NSE-Stream-v1.0.1-release.apk`, add these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret Name | Description |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64-encoded `.jks` upload keystore (`base64 -w 0 my-upload-key.jks`) |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password for alias `upload` |

---

## Building Locally

### Prerequisites
- **JDK 21** (Temurin / OpenJDK 21)
- **Gradle 9.3.1**
- **Android SDK** with `platforms;android-36`, `platforms;android-36.1`, and `build-tools;36.0.0`

### Commands

```bash
# Build Debug APK
gradle :app:assembleDebug

# Run Unit & Robolectric UI Tests
gradle :app:testDebugUnitTest
```

The compiled debug APK is output to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Structure

```text
├── .github/workflows/
│   ├── android-apk.yml        # Automated APK build & GitHub Release workflow (v1.0.1)
│   └── android-tests.yml      # Automated JVM & Robolectric test workflow
├── app/
│   ├── build.gradle.kts       # App config (versionName 1.0.1, versionCode 2)
│   └── src/
│       ├── main/java/com/example/
│       │   ├── data/local/    # SQLite watchlist & watch progress persistence
│       │   ├── data/remote/   # Centralized ApiConfig & NseMovieApiClient
│       │   ├── data/repository/
│       │   ├── model/         # MovieItem, MovieCategory, ApiResult models
│       │   ├── ui/components/ # Header, ServerSettings, Catalog, Details, ExoPlayer
│       │   ├── ui/theme/      # Dark & crimson Material 3 theme + typography
│       │   ├── viewmodel/     # NseStreamViewModel state & error recovery
│       │   └── MainActivity.kt
│       └── test/java/com/example/
│           ├── ExampleUnitTest.kt
│           └── NseStreamRobolectricTest.kt
└── README.md
```
