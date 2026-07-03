# Offline Reader 📚

A beautiful, lightweight, and extremely fast offline PDF and CBZ reader for comics, books, and manga. Built with modern Android technologies (Kotlin, Jetpack Compose, Room Database, Coroutines, and Material 3 design).

---

## 🚀 Getting Started

### Prerequisites

To build and run the application locally, you need:
- **Java Development Kit (JDK):** JDK 17 or higher.
- **Android SDK:** Installed via Android Studio or command-line tools.
- **Android Studio (recommended):** Android Studio Ladybug or newer.

### Local Installation & Build

1. **Clone the repository:**
   ```bash
   git clone <your-repository-url>
   cd offline-reader
   ```

2. **Build the Debug APK:**
   Using the standard Gradle Wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
   The built APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Build the Release APK:**
   To build the Release APK locally, provide the required signing environment variables:
   ```bash
   KEYSTORE_PATH="/path/to/your/release.keystore" \
   STORE_PASSWORD="your-keystore-password" \
   KEY_PASSWORD="your-key-password" \
   ./gradlew assembleRelease
   ```
   The signed APK will be located at:
   `app/build/outputs/apk/release/app-release.apk`

---

## 🛠️ CI/CD with GitHub Actions

This project includes a fully-automated, production-ready CI/CD pipeline using **GitHub Actions**.

### Workflow Configuration
The workflow file is located at `.github/workflows/build.yml`. It:
- **Triggers automatically** on every push or pull request to the `main` or `master` branches.
- Supports **manual execution** via GitHub's `workflow_dispatch` trigger.
- Automatically caches Gradle dependencies to ensure lightning-fast build execution.
- Compiles both **Debug** and **Release** versions.
- Uploads the successfully generated APKs as GitHub Action Artifacts.

---

## 🔑 Preparing Release Signing (GitHub Secrets)

To sign your production releases automatically on GitHub Actions, configure the following secrets in your repository settings (**Settings > Secrets and variables > Actions > New repository secret**):

| Secret Name | Description | Example / Instructions |
|-------------|-------------|------------------------|
| `KEYSTORE_BASE64` | Base64-encoded string of your `.jks` or `.keystore` file | Encode your keystore to Base64 (see instructions below) |
| `KEYSTORE_PASSWORD` | The password protecting your keystore file | `my_secure_keystore_password_123` |
| `KEY_PASSWORD` | The password for your key alias | `my_secure_key_password_123` |

> ⚠️ **Note:** The release key alias is pre-configured to `upload` in `build.gradle.kts`. Please ensure your release keystore's alias is set to `upload` or match the value defined in `build.gradle.kts`.

### How to Encode your Keystore to Base64

Run the appropriate command in your terminal to get the Base64 string for the `KEYSTORE_BASE64` secret:

* **macOS / Linux:**
  ```bash
  base64 -i my-upload-key.jks | tr -d '\n'
  ```
  *(or `openssl base64 -A -in my-upload-key.jks`)*

* **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks"))
  ```

Copy the printed text block and save it as the value of the `KEYSTORE_BASE64` secret.

### Fail-Safe Fallback Mechanism
If you have **not** set up the GitHub Secrets yet (for example, on newly created repositories, or inside Pull Requests from external contributors/forks), the GitHub Actions pipeline is designed to **never fail**. 

It will automatically detect that secrets are missing and dynamically generate a secure, temporary self-signed keystore on-the-fly to sign the Release APK. This guarantees a smooth build, continuous feedback, and downloadable builds under all circumstances.

---

## 📁 Artifact Locations & Deployment

Once a GitHub Actions run finishes, you can download the compiled binaries from the **Summary** tab of the workflow execution page:
- **`app-debug-apk`**: Ready for local debugging, testing, or sideloading on devices.
- **`app-release-apk`**: Production release build, fully optimized, zip-aligned, and signed.
