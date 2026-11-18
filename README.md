<div align="center">
  <img src="app/src/main/res/drawable/ib_coder_logo.png" width="112" alt="IB CODER Logo" />
  <h1>IB CODER</h1>
  <p><strong>On-Device AI Coding Agent, Multi-Domain IDE, GitHub Hub &amp; Cloud-Synced Workspace for Android</strong></p>

  <p>
    <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.2" />
    <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose M3" />
    <img src="https://img.shields.io/badge/Android-SDK%2026%E2%80%9336-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android SDK 26 to 36" />
    <img src="https://img.shields.io/badge/Firebase-Auth%20%26%20Firestore-FFCA28?style=flat-square&logo=firebase&logoColor=black" alt="Firebase Auth & Firestore" />
    <img src="https://img.shields.io/badge/MCP-Streamable%20HTTP%20%26%20SSE-111827?style=flat-square" alt="MCP Support" />
    <img src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white" alt="GitHub Actions CI" />
  </p>

  <p>
    <a href="#features">Features</a> •
    <a href="#supported-ai-providers">AI Providers</a> •
    <a href="#architecture">Architecture</a> •
    <a href="#building-from-source">Build from Source</a> •
    <a href="#github-actions--play-store-release">CI &amp; Play Bundle</a> •
    <a href="PRIVACY_POLICY.md">Privacy Policy</a>
  </p>
</div>

---

**IB CODER** is an Android-native AI coding workspace and general-purpose research agent built with **Kotlin**, **Jetpack Compose**, and **Material Design 3**. Manage complete software projects directly from your phone or tablet, browse and edit code with syntax highlighting, stream responses and tool calls from 23+ built-in LLM providers or any custom API endpoint, connect Model Context Protocol (MCP) servers, control your entire GitHub account and CI workflows—and carry all of your API keys, GitHub tokens, and configurations across devices via Google Sign-In and Cloud Firestore.

---

## Features

### 1. Unified Sidebar IDE & Responsive Workspace
- **App-Wide Sidebar Navigation:** Access every tool from a unified navigation drawer (`ModalNavigationDrawer` on phones with a one-tap menu button in every top bar; persistent `PermanentNavigationDrawer` on tablets `>= 840dp`):
  - **Code Workbench** — Project file explorer, syntax-highlighted editor, and inline AI coding pane.
  - **Projects & Files** — Create, switch, import/export `.zip` archives, or bind workspaces to device folders or GitHub repos.
  - **AI Chat** — Real-time SSE streaming chat with collapsible reasoning traces, inline tool execution cards, live internet lookup indicators, and cancellation/stop control.
  - **Chat Sessions** — Multi-session Room-backed history with search, token usage metrics, and quick switching.
  - **AI Providers & APIs** — Manage 23 built-in providers, fetch `/models` dynamically, or add custom API URLs.
  - **GitHub Hub & CI** — Manage repositories, auto-sync changes, dispatch Actions workflows, and auto-prompt AI bug fixes.
  - **MCP Servers** — Connect external MCP servers and inspect live discovered tools.
  - **Settings & Sync** — Live Cloud Vault sync status, manual **Sync Now**, internet toggle, and account controls.

### 2. Three Flexible Project Storage Modes
- **Device Folder (Android Storage Access Framework):** Tap **Create New Project → Pick Device Folder** to grant folder access through the system Files picker and edit files directly in place on your device.
- **Direct-to-GitHub Workspace:** Link a repository as your live project root so file reads, writes, edits, and deletions operate directly against your GitHub repository without manual exports.
- **Local App-Private Workspace:** Fast sandboxed workspaces stored locally with full `.zip` import and export support.

### 3. Multi-Domain AI Agent & Session Presets
When starting a new session, choose between:
- **Normal Session (Q&A & Research):** Concise, general-purpose assistant with live web search and URL reading for any question or topic.
- **Special Session (System Prompt Presets):** Start tailored agent workflows out of the box:
  - `Multi-Domain Engineer` (`ALL STACK`) — Web apps, Python backends, scripts, APIs, docs, or mobile apps.
  - `Full-Stack Web` (`WEB`) — React, TypeScript, Vite, Node.js, Next.js, Tailwind/CSS, REST & GraphQL.
  - `Python, Data & Automation` (`PYTHON`) — Python 3, FastAPI/Flask, CLI tools, data pipelines, and `pytest`.
  - `Android & Compose` (`ANDROID`) — Kotlin, Jetpack Compose, Material 3, Navigation Compose, and Gradle Kotlin DSL.
  - `Internet Researcher` (`WEB LIVE`) — Primary-source web research, API spec inspection, and source-cited summaries.
  - `Bug Hunter & CI Reviewer` (`DEBUG`) — Root-cause tracing, Gradle/CI diagnostics, and targeted code patches.
  - `Custom System Prompt` (`CUSTOM`) — Define your own rules, persona, or task brief.

### 4. Built-In Agentic Tool Calling & Live Internet Access
Models with tool-calling support can invoke **9 built-in tools** (with streaming visibility and confirmation safeguards):

| Tool | Capability |
| :--- | :--- |
| `web_search` | Live internet search for documentation, package APIs, endpoints, pricing, and current facts |
| `fetch_url` | Fetch and inspect `http://` / `https://` web pages, docs, JSON endpoints, or raw GitHub files (`GET`/`POST`/`PUT`/`DELETE`/`HEAD`) |
| `list_files` | List files and folders inside the active workspace |
| `read_file` | Read file contents with line ranges and line numbers |
| `write_file` | Create or overwrite files (auto-creating parent directories) |
| `edit_file` | Exact snippet search-and-replace edits (`replace_all` or unique match) |
| `search_files` | Plain-text or regex search across workspace files |
| `create_folder` | Create nested directory structures |
| `delete_file` | Delete files or folders with **mandatory interactive user confirmation** |

> Models or providers that do not support tool calling (such as search-only or pure reasoning endpoints) are clearly surfaced in the provider UI.

### 5. Full GitHub Control & AI CI Auto-Fix
- **Sign In to GitHub:** Connect via Personal Access Token or GitHub OAuth Device Flow (`Account`, `Repositories`, and `Actions & Sync` tabs in **GitHub Hub & CI**).
- **Repository & File Management:** Browse public and private repositories, create new repos, switch branches, edit existing repository files, and push workspace changes.
- **Automatic GitHub Sync:** Keep local changes automatically pushed and synchronized with your target GitHub repository.
- **GitHub Actions Control & Bug Auto-Fix:** Dispatch Actions workflows straight from the app, poll run status/logs, and tap **Fix with AI** whenever a workflow fails or fails to dispatch to send diagnostics straight into an AI debugging session.

### 6. Model Context Protocol (MCP) Servers
- Add external MCP servers using **Streamable HTTP** or **Legacy HTTP+SSE** transports.
- Store server bearer tokens securely (Android Keystore + Cloud Vault sync), discover server tools dynamically via `tools/list`, and expose enabled MCP tools directly to the coding agent alongside built-in tools.

### 7. Cross-Device Google Sign-In & Zero-Trust Cloud Vault
- Sign in with Google via **Jetpack Credential Manager** + **Firebase Authentication**.
- Syncs your personal developer setup seamlessly across devices under owner-only **Cloud Firestore** security rules (`request.auth.uid == userId`), including:
  - Encrypted-at-rest local Keystore API keys synced cleanly into your private Firestore vault on authenticated sessions
  - Active provider & model selections and saved provider configurations
  - Custom OpenAI-compatible provider URLs and defaults
  - GitHub token, username, and linked repository settings
  - MCP server definitions and authentication tokens
  - Session prompt presets and workspace toggles
- Signing out cleans up device-local vault secrets while keeping your cloud account vault ready on any device you sign into next.

---

## Supported AI Providers

IB CODER includes **23 built-in providers** plus **unlimited custom OpenAI-compatible endpoints**, with live `/models` discovery and manual model ID fallback:

| Category | Built-In Providers |
| :--- | :--- |
| **Free-Tier / Developer Friendly** | Coarena, Google Gemini, Groq, OpenRouter, NVIDIA NIM, Cloudflare Workers AI, Z AI (Zhipu), Aion Labs, Gonka Broker, Kilo Code, LLM7.io, OVHcloud AI Endpoints |
| **Frontier & Cloud APIs** | Anthropic (native Claude Messages API + streaming + tool use), OpenAI, Mistral, DeepSeek, xAI (Grok), Together AI, Fireworks AI, Perplexity, Azure OpenAI |
| **Local Inference** | Ollama (`localhost` / LAN endpoints) |
| **Custom Endpoints** | Built-in custom adapter + **Custom Provider Builder** (paste any API base URL or docs link to test, discover models, and save) |

---

## Architecture

- **Language & UI:** Kotlin 2.2, Jetpack Compose, Material Design 3, Type-Safe Navigation Compose (`@Serializable` routes)
- **State & Async:** MVVM (`ViewModel`, `StateFlow` / `collectAsStateWithLifecycle`), Kotlin Coroutines & Flow
- **Networking & Streaming:** OkHttp with custom SSE parsers for OpenAI-compatible and native Anthropic Messages streams
- **Local Persistence & Security:** Room Database (sessions & messages), Jetpack DataStore Preferences, Android Keystore AES-256-GCM (`KeystoreCryptoManager`)
- **Cloud Auth & Sync:** Firebase Authentication, Jetpack Credential Manager (`googleid`), Cloud Firestore (`CloudAccountVaultRepository`)

```text
app/src/main/java/com/example/
├── data/
│   ├── agent/          # Agent loop, prompt building, built-in & MCP tool execution
│   ├── local/          # Room entities, DAOs, database
│   ├── mcp/            # MCP JSON-RPC client (Streamable HTTP & HTTP+SSE)
│   ├── network/        # 23 provider adapters, live web tools, GitHub REST/Actions client
│   ├── preferences/    # Jetpack DataStore + Keystore-encrypted credential storage
│   ├── remote/         # CloudAccountVaultRepository & Firestore error handling
│   ├── security/       # KeystoreCryptoManager (AES-256-GCM)
│   └── workspace/      # Project sandboxes, SAF DocumentFile adapter, ZIP utilities
├── domain/
│   ├── model/          # Provider, Chat, GitHub, MCP, Session, and Workspace models
│   └── prompt/         # Default prompts & selectable session prompt presets
└── ui/
    ├── AiCoderApp.kt   # Auth gate + responsive Modal/Permanent NavigationDrawer shell
    ├── navigation/     # Type-safe Compose Navigation routes
    ├── screens/        # Workspace, Projects, Chat, Sessions, Providers, GitHub, MCP, Settings, SignIn
    └── theme/          # IB CODER pixel-dark Material 3 theme & JetBrains Mono typography
```

---

## Building from Source

### Prerequisites
- **JDK 17**
- **Android SDK:** `compileSdk = 36` (`platforms;android-36.1` or `platforms;android-36`), `build-tools;36.0.0`, `minSdk = 26`
- **Gradle:** 9.3.1 (via wrapper `./gradlew` or system `gradle`)

### Clone & Build

```bash
git clone <your-repo-url>
cd ib-coder

# Run JVM / Robolectric unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Build release Android App Bundle (.aab)
./gradlew bundleRelease
```

To customize the package identifier or app version, update the constants at the top of `app/build.gradle.kts`:
- `APP_APPLICATION_ID`
- `APP_VERSION_CODE`
- `APP_VERSION_NAME`

### Firebase & Firestore Setup (For Forks / Self-Hosting)
Because Firebase Authentication (Google Sign-In) and Cloud Firestore power cross-device sync, anyone forking or self-hosting IB CODER with their own Firebase project should:
1. Create a Firebase project and enable **Authentication → Google Sign-In** plus **Cloud Firestore**.
2. Register your Android app (`applicationId` + debug/release SHA-1 fingerprints) and place your `google-services.json` under `app/`.
3. Update `app/src/main/res/values/firebase_applet_config.xml` with your Firestore database ID if using a named database (default is `(default)`).
4. Deploy the included owner-only security rules from `firestore.rules` (schema blueprint in `firebase-blueprint.json`).
5. Optionally run the local Firestore security rule tests against the Firebase Emulator:
   ```bash
   firebase emulators:exec --only firestore "node --test firestore.test.js"
   ```

### Environment Variables
The Secrets Gradle Plugin reads `.env` locally (falling back to `.env.example`). Never commit real API keys, `.env`, or `.jks` keystore files.

---

## GitHub Actions & Play Store Release

This repository includes `.github/workflows/android.yml`, which automatically builds deliverables on every push or manual workflow dispatch:
- **`src/app-test.apk`** (`app-test-apk` artifact) — Debug APK built on every push without requiring Play Store secrets.
- **`src/app-release.aab`** (`play-bundle` artifact) — Signed Google Play App Bundle built automatically whenever release signing secrets are configured.

### 1. Generate an Upload Keystore

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

> Keep `upload-keystore.jks` and its passwords stored safely outside Git.

### 2. Base64-Encode the Keystore

- **Linux:** `base64 -w 0 upload-keystore.jks`
- **macOS:** `base64 -i upload-keystore.jks | tr -d '\n'`
- **Windows (PowerShell):** `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload-keystore.jks"))`

### 3. Configure GitHub Repository Secrets
Open **GitHub Repository → Settings → Secrets and variables → Actions → Repository secrets** and add:

| Secret Name | Value |
| :--- | :--- |
| `KEYSTORE_BASE64` | Single-line Base64 output from Step 2 |
| `KEYSTORE_PASSWORD` | Keystore store password |
| `KEY_ALIAS` | Key alias (e.g., `upload`) |
| `KEY_PASSWORD` | Key password |
| `GOOGLE_SERVICES_JSON` | *(Optional)* Raw or Base64 contents of `app/google-services.json` if omitted from a public repo |

### 4. Download Artifacts & Upload to Google Play Console
1. Bump `APP_VERSION_CODE` and `APP_VERSION_NAME` in `app/build.gradle.kts`.
2. Push to GitHub or trigger the workflow manually (either on GitHub or directly inside **IB CODER → GitHub Hub & CI → Actions & Sync**).
3. Download `src/app-release.aab` (or the `play-bundle` artifact) and upload it under **Google Play Console → Testing → Internal testing** before promoting to production.

---

## Security & Privacy

- API keys and tokens stored on device are encrypted with **Android Keystore (AES-256-GCM)** and excluded from Android Auto Backup.
- Cross-device Cloud Vault documents in Firestore enforce strict owner-only isolation (`isOwner(userId)`), deny collection listing, and validate schemas and payload lengths in `firestore.rules`.
- Model requests, web fetches, GitHub API calls, and MCP requests go directly from your device to the endpoints you configure.
- Read the full [Privacy Policy](PRIVACY_POLICY.md) for details on third-party AI provider data retention.

---

## Contributing

Contributions, bug reports, and feature ideas are welcome!
1. Fork the repository and create a feature branch (`git checkout -b feat/your-feature`).
2. Run unit tests (`./gradlew testDebugUnitTest`) and verify the build (`./gradlew assembleDebug`).
3. Open a Pull Request describing your changes clearly.
