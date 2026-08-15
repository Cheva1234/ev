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
JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk ./gradlew testDebugUnitTest assembleDebug --no-daemon
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

The unit-test suite currently covers the agent loop, model backend, tool
dispatch, calculus engine, and LaTeX HTML generation. Keep the unit tests and
APK build in the same verification command so a UI change cannot accidentally
ship without its supporting logic being checked.

## Calculus and LaTeX

The math tool supports numeric expressions plus symbolic derivatives, common
antiderivatives, definite integrals, and numerical limits:

```text
@math diff(x^2+sin(x),x)
@math integrate(x^2,x)
@math integral(x^2,0,3,x)
@math limit(sin(x)/x,x,0)
```

Natural requests such as `find the derivative of x squared` are routed to the
math tool by the agent policy. Successful calculus results are returned as
LaTeX and rendered visually in both Chat and Console. The APK bundles KaTeX
0.18.0 and its fonts, so formula rendering works offline and does not require
another download. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for
the KaTeX license attribution.

## Device verification with ADB

Use wireless ADB or USB ADB to install the debug APK without deleting the
downloaded model:

```bash
ADB=/home/nobara-user/Android/Sdk/platform-tools/adb
SERIAL=PHONE_SERIAL

"$ADB" devices -l
"$ADB" -s "$SERIAL" install -r -g app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$SERIAL" shell am force-stop com.ev.terminal
"$ADB" -s "$SERIAL" shell am start -n com.ev.terminal/.MainActivity
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" logcat -d | rg -i 'AndroidRuntime|FATAL EXCEPTION|com\\.ev\\.terminal|EV_MODEL'
```

`install -r` preserves the app's private storage. Do not uninstall the app or
clear its data when the goal is to keep the roughly 563 MB Qwen model. A model
download can be verified from the app-private directory with:

```bash
"$ADB" -s "$SERIAL" shell run-as com.ev.terminal \
  sha256sum files/.ev/models/qwen3.5-0.8b-q4_0-v2.gguf
```

The current branch was verified locally on an SM-A736B running Android 16:
the APK installed successfully, the existing model remained present with the
expected SHA-256, Chat displayed a typeset derivative, and Console displayed
the matching successful `MATH` task event. The check completed without an
app `FATAL EXCEPTION` in logcat.

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
