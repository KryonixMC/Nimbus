---
name: pre-commit-docs
description: >
  Automatically checks if documentation (docs/) or CLAUDE.md need updating before creating a git commit.
  Checks if documentation (docs/) or CLAUDE.md need updating before a git commit.
  Triggered automatically via PreToolUse hook on git commit — no need to invoke manually.
  Can also be invoked manually via /pre-commit-docs.
---

# Pre-Commit Documentation Check

Before every git commit, you MUST run this documentation check. This is a hard requirement for this project — the user wants docs and CLAUDE.md to always stay in sync with the code.

## When This Triggers

Any time you are about to create a git commit. No exceptions.

## Process

### 1. Analyze What Changed

Run `git diff --cached` for staged changes. If nothing is staged yet, run `git diff` to see what will be committed. Identify which code areas were modified.

### 2. Map Changes to Documentation

Use this table to find which docs might be affected:

| Changed code area | Check these docs |
|---|---|
| `api/routes/` or `api/ApiModels.kt` | `docs/reference/api.md`, `docs/reference/websocket.md` |
| `event/Events.kt` | `docs/reference/events.md` |
| `console/commands/` or `CommandDispatcher.kt` | `docs/reference/commands.md` |
| `config/NimbusConfig.kt` or `config/GroupConfig.kt` | `docs/config/nimbus-toml.md`, `docs/config/groups.md` |
| `scaling/` | `docs/guide/scaling.md` |
| `service/` | `docs/guide/concepts.md`, `docs/guide/server-groups.md` |
| `template/` | `docs/config/templates.md` |
| `proxy/` | `docs/guide/proxy-setup.md`, `docs/config/nimbus-toml.md` |
| `stress/` | `docs/reference/api.md` (stress section) |
| `database/` | `docs/config/nimbus-toml.md` (database section) |
| `setup/` | `docs/guide/installation.md`, `docs/guide/quickstart.md` |
| `loadbalancer/` | `docs/reference/api.md`, `docs/config/nimbus-toml.md` |
| `nimbus-bridge/` | `docs/developer/bridge.md` |
| `nimbus-sdk/` | `docs/developer/sdk.md` |
| `nimbus-signs/` | `docs/developer/signs.md` |
| `nimbus-perms/` | `docs/config/permissions.md` |
| `nimbus-agent/` or `nimbus-protocol/` | `docs/guide/multi-node.md` |
| `velocity/` | `docs/guide/proxy-setup.md` |
| New/moved packages under `nimbus-core/` | `docs/developer/architecture.md` |
| `build.gradle.kts` or `gradle.properties` | `docs/guide/installation.md` |

### 3. Check CLAUDE.md

`CLAUDE.md` is the AI project reference. Verify these sections still match the code:

- **Modules** — added, removed, or renamed?
- **Architecture tree** — new or removed packages?
- **Tech Stack** — dependency changes?
- **Configuration** — new config files or sections?
- **Key Patterns** — behavioral changes (ports, naming, shutdown, plugins)?
- **API section** — endpoints added/removed/changed?
- **Code Style** — new patterns introduced?

### 4. Read and Compare

For each potentially affected doc: read the relevant section, compare with the code change, and determine if an update is needed. Only flag docs where the documented content actually contradicts or is missing information about the new code. Don't flag unchanged behavior.

### 5. Act on Findings

**If everything is in sync:** Briefly confirm ("Docs und CLAUDE.md sind aktuell.") and proceed with the commit.

**If updates are needed:** List what's outdated, make the updates, and include the doc changes in the same commit. Tell the user what you updated.

Keep doc updates minimal and precise — only change what's actually wrong or missing. Don't rewrite entire sections when a single line needs adjusting.
