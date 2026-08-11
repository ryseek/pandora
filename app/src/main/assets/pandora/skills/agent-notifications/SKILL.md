---
name: agent-notifications
description: Send a Pandora Android notification when the user asks to be notified, when asynchronous work is complete or ready for review, or when long-running work genuinely requires attention.
---

# Agent Notifications

Send one concise notification after the relevant event occurs:

```sh
curl -fsS -X POST \
  --data-urlencode 'title=Pandora' \
  --data-urlencode 'message=Your report is ready to review.' \
  http://127.0.0.1:8765/v1/notify
```

Use a specific result-oriented message. Notify only when the user requested it, when asynchronous work finishes outside an active exchange, or when progress cannot continue without attention. Do not notify for routine tool calls, intermediate progress, or every chat response.

If the endpoint reports `notifications_disabled`, mention it once in chat. Do not retry repeatedly or open Android settings without permission.

