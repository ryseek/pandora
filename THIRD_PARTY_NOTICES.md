# Third-party notices

## PRoot

The Android runtime bundles PRoot from the OpenMinis project, which is derived from the Termux/PRoot project and distributed under GPL-2.0.

- Source: https://github.com/OpenMinis/proot
- Upstream: https://proot-me.github.io/
- Runtime binary obtained from the OpenMinis 0.22-preview Android release.

## Alpine Linux

The application bundles the Alpine Linux ARM64 minirootfs prepared by OpenMinis. Alpine packages are distributed under their respective licenses.

- Source: https://alpinelinux.org/
- Package license information: https://pkgs.alpinelinux.org/

## OpenMinis

The tested Android PRoot packaging approach, runtime artifacts, PTY bridge,
terminal emulator, renderer, and keyboard input bridge are adapted from
OpenMinis, GPL-3.0.

- Source: https://github.com/OpenMinis/OpenMinis
- Release: https://github.com/OpenMinis/OpenMinis/releases/tag/0.22-preview

## OpenAI Codex CLI

Pandora installs the OpenAI Codex CLI from the `@openai/codex` npm package
into the user's persistent Linux workspace. Codex CLI is distributed under
Apache-2.0.

- Documentation: https://learn.chatgpt.com/docs/codex/cli
- Source: https://github.com/openai/codex

## sherpa-onnx

Pandora uses the sherpa-onnx Android runtime for offline speech recognition and speech synthesis. The runtime and downloadable model metadata are distributed under Apache-2.0; individual model cards remain authoritative for model-specific terms.

- Source: https://github.com/k2-fsa/sherpa-onnx
- Models: https://github.com/k2-fsa/sherpa-onnx/releases

## Apache Commons Compress

Pandora uses Apache Commons Compress to safely unpack user-selected speech model archives. It is distributed under Apache-2.0.

- Source: https://commons.apache.org/proper/commons-compress/
