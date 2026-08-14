# EV Terminal

EV is an Android terminal-style assistant for Project Aether. It combines a local chat interface with a small agent loop and explicit tools for math, time, weather, web search, mail, and location.

## Current model

The APK bundles the Qwen model below and runs it on-device through `llama.cpp`:

```text
qwen3.5:0.8b
```

The model is packaged as an uncompressed GGUF asset and copied to private app storage on first use. No Ollama server is required.

## Requirements

- Android Studio or the Android SDK
- Java 17
- Gradle 8.9 through the included wrapper
- A local `qwen3.5:0.8b` GGUF file when building locally

The model binary is intentionally not committed to Git because it is about 1 GB and exceeds GitHub's regular file limit. Set `EV_MODEL_FILE` to the GGUF path before building:

```bash
EV_MODEL_FILE=/path/to/qwen3.5-0.8b.gguf ./gradlew assembleDebug
```

The GitHub Actions workflow downloads the same model from the model release asset and passes it to Gradle automatically.

## Build and test

```bash
./gradlew test
EV_MODEL_FILE=/path/to/qwen3.5-0.8b.gguf ./gradlew assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Architecture

```text
ChatFragment
    -> AgentRunner
        -> ModelSupervisor
            -> LlamaCppBackend -> bundled Qwen GGUF
        -> ToolRegistry
            -> math, time, weather, web, mail, location tools
```

- `ChatFragment` owns the Android chat UI and session display.
- `AgentRunner` decides whether the model should answer directly or emit a tool command, executes allowed tools, and feeds tool results back to the model.
- `ModelSupervisor` serializes model tasks and records runtime events.
- `LlamaCppBackend` runs the bundled model with the native `llama-cli` binary and streams generated output.
- `BundledModelInstaller` copies the APK asset to app-private storage and removes the obsolete LFM model file from older installations.
- `ToolRegistry` is the extension point for new capabilities.

## Useful commands in the app

- `/help` — show available commands
- `/status` — show session and runtime state
- `/model` — show the bundled model state
- `/model load` — show bundled model details
- `/tools` — show available tools
- `/new` — start a new session

## Project status

This is an active student project focused on learning backend boundaries, model orchestration, tool execution, and Android integration. The next useful improvements are streamed model cancellation, device-specific performance tuning, and integration tests against a real Android runtime.
