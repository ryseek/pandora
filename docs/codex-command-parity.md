# Codex slash-command parity

Pandora talks to `codex app-server`, not the Codex terminal UI. Slash commands are therefore a Pandora client feature: recognized commands must be translated into app-server requests or native navigation rather than sent to Codex as prompt text.

This document records parity against the Codex CLI installed on the development machine during implementation (`codex-cli 0.147.0`). Pandora installs `@openai/codex` without pinning a version on first setup, so devices can have different command surfaces. Additions should be checked against the target device version and its generated app-server schema.

## Implemented commands

| Command | Pandora behavior | Implementation boundary |
| --- | --- | --- |
| `/compact` | Compacts the current conversation and reports progress in the transcript. | `thread/compact/start` |
| `/review` | Reviews staged, unstaged, and untracked workspace changes inline. | `review/start` with an `uncommittedChanges` target |
| `/status` | Shows the active model, working directory, thread ID, and Pandora's fixed access policy. | Local session state |
| `/model` | Opens the existing model selector. `/model <name>` selects an exact model ID or display name. | Existing `model/list` data and per-turn model selection |
| `/new` | Starts a new chat in the current working directory. The previous chat remains active and accessible from Home. | Pandora navigation and `thread/start` |

Typing `/` at the beginning of the composer opens a discoverable command palette. Selecting a result fills the composer so execution still requires an explicit send action. Commands cannot be combined with attachments. An unrecognized slash-prefixed string remains an ordinary prompt, which preserves paths and future/custom prompt syntax.

## Remaining CLI parity

| Codex command | Status in Pandora | Likely implementation |
| --- | --- | --- |
| `/resume` | Existing UI equivalent | Home and Archive already resume stored threads. A command alias could open that surface. |
| `/fork` | Not implemented | `thread/fork`, then navigate to the returned thread. |
| `/diff` | Not implemented | Read-only repository diff view; avoid the unsandboxed `thread/shellCommand` API. |
| `/mention` | Not implemented | `fuzzyFileSearch` plus attachment/mention insertion. |
| `/skills` | Not implemented | `skills/list` and a native list/detail surface. |
| `/mcp` | Not implemented | `mcpServerStatus/list` and OAuth/status handling. |
| `/apps` | Not implemented | `app/list` and app detail/connection UI. |
| `/permissions` | Intentionally deferred | Pandora currently starts threads with `danger-full-access` and `approvalPolicy = never`; a selector would be misleading until those policies are enforceable. |
| `/plan` | Not implemented | Depends on Codex collaboration-mode support and an appropriate mode indicator. |
| `/personality` | Not implemented | Requires a persisted thread setting and picker. |
| `/init` | Not implemented | Needs the same repository-instruction semantics as the CLI, not a synthetic prompt approximation. |
| `/experimental` | Not implemented | `experimentalFeature/list` and enablement APIs, with version-aware labels. |
| `/debug-config` | Not implemented | `config/read`; should redact secrets and clearly label config layers. |
| `/feedback` | Not implemented | `feedback/upload` plus consent and diagnostic preview. |
| `/logout` | Existing settings/terminal path only | `account/logout`, confirmation, and post-logout navigation are needed for command parity. |

## Architecture rules

1. Parse only at submission time; draft text remains ordinary editable text.
2. Keep command metadata in one registry so parsing, autocomplete, tests, and documentation use the same names and descriptions.
3. Route server-owned operations through typed `CodexChatSession` methods. Do not send their slash spelling through `turn/start`.
4. Route navigation-owned operations through callbacks from `ChatScreen`.
5. Render successful command feedback as informational system messages and failures as error messages.
6. Track long-running command requests separately from ordinary turns so errors restore the session to `Ready` and review/compact turns remain interruptible.
7. Treat app-server schema changes as compatibility work: request and notification names should be verified whenever the bundled Codex CLI is upgraded.

## Verification expectations

- Parser unit tests cover supported commands, arguments, suggestions, unknown commands, and multiline prompts.
- Session tests should use a fake JSON-RPC transport if command coverage expands further; starting a real app-server is too heavy for local unit tests.
- Android verification should cover palette visibility, attachment rejection, model selection, navigation after `/new`, compact completion, review output, error recovery, and restoration of the previous chat from Home.
