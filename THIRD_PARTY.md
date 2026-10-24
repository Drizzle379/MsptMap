# Third-Party Components

| Component | Version | License | Source |
| :--- | :--- | :--- | :--- |
| owo-lib (owo-ui only) | 0.13.1+26.2 | MIT | https://github.com/wisp-forest/owo-lib |
| endec | 0.1.12 | MIT | https://github.com/wisp-forest/endec |
| MixinExtras | 0.5.5 | MIT | https://github.com/LlamaLad7/MixinExtras |

- owo-ui is vendored and trimmed; its package was renamed from `io.wispforest.owo` to `msptmap.vendor.owo`.
- endec (including its gson/bytebuf format modules) and MixinExtras are bundled as nested jars under `META-INF/jars/` and are unmodified.

```text
MIT License

Copyright (c) 2021 (owo-lib)
Copyright (c) 2024 Wisp Forest (endec)
Copyright (c) 2022-present LlamaLad7 (MixinExtras)

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
