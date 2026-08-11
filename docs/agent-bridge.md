# Pandora agent bridge

Pandora exposes a small HTTP API to agents inside its Debian environment. It listens only on `127.0.0.1:8765`; it is not available through Wi-Fi or another device.

Responses use JSON. A successful command returns HTTP 200 with `"ok": true`, except status, which returns the state directly. Callers should use `curl -f` so permission and validation errors fail the command.

## Phone-control session

Start the visible safety frame before issuing any ADB command:

```sh
curl -fsS -X POST http://127.0.0.1:8765/v1/control/start
```

Poll until `active` is `true`:

```sh
curl -fsS http://127.0.0.1:8765/v1/status
```

Example response:

```json
{"enabled":true,"connected":true,"active":true}
```

Stop immediately after the requested phone interaction, including after an error:

```sh
curl -fsS -X POST http://127.0.0.1:8765/v1/control/stop
```

The on-screen Stop control invokes the same stop operation. Stopping removes the overlay and disconnects the ADB transport.

## Agent notification

Send a notification when requested work is complete, when a result is ready, or when the agent genuinely needs the user's attention:

```sh
curl -fsS -X POST \
  --data-urlencode 'title=Pandora' \
  --data-urlencode 'message=Your report is ready to review.' \
  http://127.0.0.1:8765/v1/notify
```

`message` is required. `title` defaults to `Pandora`. Pandora limits titles to 80 characters and messages to 500 characters.

The endpoint returns `notifications_disabled` when Agent notifications is off in Pandora, Android notification permission is missing, or the Agent updates channel is disabled. Users control this under **Settings → Plugins → Agent notifications**. Agents must report the error once in chat and must not repeatedly retry or open Android settings themselves.

Notifications are for meaningful asynchronous updates, not narration. Do not notify after every tool call, intermediate step, or ordinary chat response.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/v1/control/start` | Connect ADB and show the safety overlay |
| `GET` | `/v1/status` | Read plugin, connection, and active-session state |
| `POST` | `/v1/control/stop` | Remove the overlay and disconnect ADB |
| `POST` | `/v1/notify` | Post a user-visible Android notification |
