# EV Terminal

EV is an Android terminal-style assistant for Project Aether. It combines a local chat interface with a small agent loop and explicit tools for math, time, weather, web search, mail, and location.

## Current model

The app uses Ollama with:

```text
qwen3.5:0.8b
```

Thinking is disabled for every request with Ollama's top-level `think: false` option. The app uses the Ollama chat API so the system and user messages remain separate.

## Requirements

- Android Studio or the Android SDK
- Java 17
- Gradle 8.9 through the included wrapper
- Ollama running on a computer reachable by the Android device or emulator

Install the model on the Ollama host:

```bash
ollama pull qwen3.5:0.8b
```

The default server address is `http://10.0.2.2:11434`, which is the Android Emulator address for the host machine. For a physical device, configure `SettingsStore.modelServerUrl` with the host computer's LAN address and allow Ollama to accept connections from that network.

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
            -> OllamaBackend -> Ollama /api/chat
        -> ToolRegistry
            -> math, time, weather, web, mail, location tools
```

- `ChatFragment` owns the Android chat UI and session display.
- `AgentRunner` decides whether the model should answer directly or emit a tool command, executes allowed tools, and feeds tool results back to the model.
- `ModelSupervisor` serializes model tasks and records runtime events.
- `OllamaBackend` translates the model contract into Ollama requests and enforces no-think mode.
- `ToolRegistry` is the extension point for new capabilities.

## Useful commands in the app

- `/help` — show available commands
- `/status` — show session and runtime state
- `/model` — show the configured Ollama model and server
- `/model load` — show Ollama setup instructions
- `/tools` — show available tools
- `/new` — start a new session

## Project status

This is an active student project focused on learning backend boundaries, model orchestration, tool execution, and Android integration. The next useful improvements are an editable Ollama server setting, streamed Ollama responses, and integration tests using a local fake Ollama server.
