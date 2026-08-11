# Security policy

## Supported versions

Security fixes are currently provided for the latest tagged release and the
`main` branch.

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Use GitHub's
private vulnerability reporting for this repository. If that feature is not
available yet, wait for the public repository to be configured rather than
posting exploit details publicly.

Include the affected version, Android version and device, reproduction steps,
impact, and any suggested mitigation. Reports will be acknowledged as soon as
practical; release timing depends on severity and the scope of the fix.

## Security model

Pandora runs a Debian userspace through PRoot. It is not a VM or a security
boundary. Codex runs inside that environment without an additional sandbox and
can read and modify the complete Pandora workspace.

Workspace backups contain the complete persistent `/root`, which can include
Codex and GitHub sessions, credentials, and private source code. Treat backup
files as secrets.
