# EV Terminal

EV is an Android terminal-style assistant for Project Aether. It combines a local chat interface with a small agent loop and explicit tools for math, time, weather, web search, mail, and location.

## Current model

The app downloads the Qwen model below on first use and runs it on-device through
`llama.cpp`:

```text
qwen3.5:0.8b
```

The model is not bundled in the APK. This keeps the install package small. Open
the model dialog and press **DOWNLOAD** (or run `/model load` in the app) to
download it into private app storage. The download shows progress, resumes a
partial `.part` file, verifies SHA-256, and only then installs the model. No
Ollama server is required.

The current APK is available from the
[v0.1.8 GitHub release](https://github.com/Cheva1234/ev/releases/tag/v0.1.8).
The model package is served from the separate
[v0.1.4 model release](https://github.com/Cheva1234/ev/releases/tag/v0.1.4).
It uses the official llama.cpp-compatible Q4_0 conversion published by
[ggml-org](https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF):

```text
File:   qwen3.5-0.8b-q4_0.gguf
Size:   563,036,064 bytes
SHA256: 57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf
```

## Requirements

- Android Studio or the Android SDK
- Java 17
- Gradle 8.9 through the included wrapper
- Network access on the device when downloading the model in the app

## Build and test

```bash
./gradlew test
./gradlew assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Architecture

```text
ChatFragment
    -> AgentRunner
        -> ModelSupervisor
            -> ModelPackageInstaller -> downloaded Qwen GGUF
            -> LlamaCppBackend -> app-private Qwen GGUF
        -> ToolRegistry
            -> math, time, weather, web, mail, location tools
```

- `ChatFragment` owns the Android chat UI and session display.
- `AgentRunner` decides whether the model should answer directly or emit a tool command, executes allowed tools, and feeds tool results back to the model.
- `ModelSupervisor` serializes model tasks and records runtime events.
- `ModelPackageInstaller` downloads the model with resume support, checks its size and SHA-256 digest, and atomically moves it into app-private storage.
- `LlamaCppBackend` runs the downloaded model with the native `llama-cli` binary and streams generated output.
- `ToolRegistry` is the extension point for new capabilities.

## Useful commands in the app

- `/help` — show available commands
- `/status` — show session and runtime state
- `/model` — show the downloaded model state
- `/model load` — show model details and open the download dialog when needed
- `/tools` — show available tools
- `/new` — start a new session

## Project status

This is an active student project focused on learning backend boundaries, model orchestration, tool execution, and Android integration. The next useful improvements are device-specific performance tuning and integration tests against a real Android runtime.

## License

The project source is licensed under the Apache License 2.0. See
[LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for
the project and dependency/model attribution details.
