# TODO

## Concurrent Codex tasks

- Support multiple active Codex chat sessions instead of a single `current` session.
- Keep a task running when its chat is closed, when another chat is opened, or while a terminal is in the foreground.
- Show each task's live state on the home screen: starting, working, ready, failed, or stopped.
- Reopen a chat by reconnecting to its existing session without creating a second writer.
- Allow each running task to be stopped explicitly.
- Clean up all session processes safely when Pandora is terminated.
