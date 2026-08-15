# Third-party notices

EV includes or downloads the following third-party components. The Apache-2.0
license in the repository applies to EV's own source code; it does not replace
the licenses for these components.

## Qwen3.5-0.8B model

The app downloads the `qwen3.5-0.8b-q4_0.gguf` model package separately from the
APK. The package currently used by the app is identified by this digest:

```text
SHA-256: 57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf
```

The upstream Qwen3.5-0.8B model card identifies the model as Apache-2.0:

- Model card: <https://huggingface.co/Qwen/Qwen3.5-0.8B>
- License: <https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/main/LICENSE>
- GGUF conversion source: <https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF>
- Download package used by EV: <https://github.com/Cheva1234/ev/releases/tag/v0.1.4>

The model is optional runtime data. It is not part of the source repository or
the APK; users download it through the in-app model setup dialog.

## llama.cpp

EV uses the `llama.cpp` native command-line backend for local inference.
`llama.cpp` is distributed under the MIT License by the ggml authors:

- Project: <https://github.com/ggml-org/llama.cpp>
- License: <https://github.com/ggml-org/llama.cpp/blob/master/LICENSE>

The MIT License notice is reproduced here for the native backend attribution:

```text
MIT License

Copyright (c) 2023-2026 The ggml authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## KaTeX

EV bundles KaTeX 0.18.0 JavaScript, CSS, and font assets in the APK to render
calculus results offline in the chat and console WebViews.

- Project: <https://katex.org/>
- Source package: <https://www.npmjs.com/package/katex>
- License: MIT
- License text: <https://github.com/KaTeX/KaTeX/blob/main/LICENSE>
