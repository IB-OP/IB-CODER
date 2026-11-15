# AI Coder — On-Device AI Coding Agent for Android

AI Coder is an Android AI coding agent built with Kotlin, Jetpack Compose, and Material 3. Chat with frontier or local LLMs that can inspect, create, edit, search, and manage complete projects—including full Gradle/Compose Android apps—directly inside an on-device workspace.

## Features
- **23 Built-in Providers Behind One Interface:** Coarena ($5/day free tier), Google Gemini, Groq, OpenRouter, NVIDIA NIM, Cloudflare Workers AI, Z AI (Zhipu), Aion Labs, Gonka Broker, Kilo Code, LLM7.io, OVHcloud AI Endpoints, Anthropic (native Messages API + streaming + tool use), OpenAI, Mistral, DeepSeek, xAI (Grok), Together AI, Fireworks AI, Perplexity, Azure OpenAI, Ollama (local), and Custom OpenAI-compatible endpoints.
- **Live `/models` Discovery:** Fetches models live from provider endpoints with manual entry fallback—no hardcoded model lists.
- **On-Device Keystore Security:** API keys are encrypted via Android Keystore (AES-256-GCM) and never logged or sent anywhere except the selected provider.
- **Agentic Tool Calling:** `list_files`, `read_file`, `write_file`, `edit_file`, `create_folder`, `delete_file` (with interactive user confirmation), and `search_files`, rendered inline in chat with streaming SSE and Stop support.
- **Tool-Calling Capability Checks:** Models/providers without tool-calling support (such as Perplexity Sonar or reasoning-only models) are clearly flagged in the UI.
- **Project Workspace & Syntax Viewer:** Create, open, rename, delete, edit, syntax-highlight code files, and import/export `.zip` archives.
- **Multi-Session History:** Room-backed chat sessions with search, token usage tracking, and delete.

---

## Local Build

Requirements: JDK 17, Android SDK Platform 36 (`platforms;android-36.1` or `platforms;android-36`), Build Tools `36.0.0`.

```bash
./gradlew assembleDebug
./gradlew bundleRelease
```

To change the `applicationId` or bump `versionCode` / `versionName`, edit the top constants in `app/build.gradle.kts` (`APP_APPLICATION_ID`, `APP_VERSION_CODE`, `APP_VERSION_NAME`).

---

## Release Signing & GitHub Actions Setup

The workflow `.github/workflows/android.yml` builds:
- `src/app-test.apk` (debug APK, committed to repo and uploaded as artifact `app-test-apk`)
- `release/app-release.aab` (signed release bundle, committed to repo and uploaded as artifact `play-bundle`)

### Step 1: Create the Upload Keystore with `keytool`

Run the following command on your machine (choose strong passwords and store the keystore safely outside Git):

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Keep `upload-keystore.jks` and its passwords backed up safely outside the repository. Never commit `.jks` or `.keystore` files to Git.

### Step 2: Base64-Encode the Keystore

- **Linux:**
  ```bash
  base64 -w 0 upload-keystore.jks
  ```
- **macOS:**
  ```bash
  base64 -i upload-keystore.jks | tr -d '\n'
  ```
- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("upload-keystore.jks"))
  ```

Copy the single-line Base64 output string.

### Step 3: Add the Four GitHub Repository Secrets

1. Open your GitHub repository.
2. Go to **Settings** → **Secrets and variables** → **Actions**.
3. Click **New repository secret** and add all four secrets:
   - `KEYSTORE_BASE64`: The single-line Base64 string from Step 2.
   - `KEYSTORE_PASSWORD`: The keystore store password you entered in Step 1.
   - `KEY_ALIAS`: The key alias (e.g., `upload`).
   - `KEY_PASSWORD`: The key password (if you pressed Enter to match the store password, use the same password).

### Step 4: Run the Workflow and Download `.aab` and APK

1. Push any commit to GitHub, **or** open the **Actions** tab → **Build Android APK & Play Store Bundle** → **Run workflow**.
2. Wait for the workflow run to finish (if any of the 4 secrets are missing, the workflow fails immediately with a clear error).
3. Download the build outputs either:
   - From the Actions run summary under **Artifacts**:
     - `app-test-apk` (contains `app-test.apk`)
     - `play-bundle` (contains `app-release.aab`)
   - Or directly from the repository paths `src/app-test.apk` and `release/app-release.aab`.

### Step 5: Upload the `.aab` to Google Play Console (Internal Testing First)

1. Bump `APP_VERSION_CODE` (and `APP_VERSION_NAME` if appropriate) in `app/build.gradle.kts` before every new release.
2. Sign in to [Google Play Console](https://play.google.com/console) and select (or create) your app.
3. In the left menu, go to **Testing** → **Internal testing**.
4. Click **Create new release**.
5. If this is your first release, opt in to **Play App Signing** (recommended by Google Play; the key created in Step 1 serves as your upload key).
6. Under **App bundles**, upload `app-release.aab` downloaded from the `play-bundle` artifact.
7. Enter release notes, click **Next** → **Save** → **Start rollout to Internal testing**.
8. Add your tester email list under the **Testers** tab and share the opt-in URL to verify the build on real devices before promoting to Closed, Open, or Production tracks.
