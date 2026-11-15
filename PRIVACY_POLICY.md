# Privacy Policy — AI Coder

**Effective Date:** November 15, 2026

AI Coder ("the App") is an on-device AI coding assistant and workspace manager. Your privacy and control over your credentials and source code are central to how the App is designed.

## 1. Data Stored on Your Device
- **API Keys and Credentials:** Any API keys, account IDs, or deployment names you enter are stored exclusively on your device. API keys are encrypted using Android Keystore AES-256-GCM cryptography and persisted only in app-private storage, excluded from cloud backups and device transfers.
- **Projects and Code:** All workspaces, files, and folders you create or import are stored locally in app-private storage.
- **Chat Sessions and Settings:** Chat history, tool execution logs, theme preferences, and custom system prompts are stored locally in the App's Room database and Jetpack DataStore.

## 2. Data Sent Over the Network
- **No Developer Backend:** The App does not operate any developer-controlled servers, analytics pipelines, crash telemetry, or tracking SDKs. We never receive, collect, or view your API keys, prompts, or source code.
- **Direct Provider Communication:** When you send a chat message, test a connection, or fetch models, requests are sent directly from your device over HTTPS (or HTTP only when you configure a local endpoint such as Ollama on localhost) **solely to the AI provider endpoint you explicitly selected**.
- **Workspace Context:** When the coding agent executes tools (such as reading, writing, searching, or listing files in the currently selected project), the resulting file contents or snippets are included in the conversation context sent to your selected AI provider so it can assist with your task.

## 3. Third-Party AI Providers & Free-Tier Notice
Each AI provider operates under its own Privacy Policy and Terms of Service. **Some free-tier providers may log, retain, review, or use prompts and responses for abuse monitoring or model training/improvement.** Before sending proprietary or sensitive code, review the data-retention and training policies of the provider you select, or use a local provider such as Ollama.

## 4. Security & Deletion
- You can remove any API key, delete any chat session, or delete any project workspace at any time inside the App.
- Uninstalling the App permanently removes all locally stored encrypted keys, databases, settings, and workspaces.

## 5. Contact
If you publish or distribute a modified build of this App, update this section with your support email or repository URL.
