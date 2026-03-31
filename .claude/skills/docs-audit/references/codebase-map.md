# Nimbus Codebase Map for Documentation Audit

Quick reference for where to find things when verifying docs.

## Source Locations

| What to verify | Where to look |
|---|---|
| Config keys & defaults | `nimbus-core/src/main/kotlin/dev/nimbus/config/NimbusConfig.kt`, `GroupConfig.kt` |
| API routes | `nimbus-core/src/main/kotlin/dev/nimbus/api/` (route files) |
| API data models | `nimbus-core/src/main/kotlin/dev/nimbus/api/ApiModels.kt` |
| Console commands | `nimbus-core/src/main/kotlin/dev/nimbus/console/commands/` |
| Command registry | `nimbus-core/src/main/kotlin/dev/nimbus/console/CommandDispatcher.kt` |
| Events | `nimbus-core/src/main/kotlin/dev/nimbus/event/Events.kt` |
| Package structure | `nimbus-core/src/main/kotlin/dev/nimbus/` (all subdirectories) |
| Bridge commands | `nimbus-bridge/src/main/java/dev/nimbus/bridge/` |
| SDK API | `nimbus-sdk/src/main/kotlin/dev/nimbus/sdk/` |
| Signs plugin | `nimbus-signs/src/main/kotlin/dev/nimbus/signs/` |
| Permissions | `nimbus-perms/src/main/kotlin/dev/nimbus/perms/` |
| Agent/protocol | `nimbus-agent/`, `nimbus-protocol/` |
| Build config | `build.gradle.kts`, `gradle.properties` |
| Templates/software | `nimbus-core/src/main/kotlin/dev/nimbus/template/SoftwareResolver.kt` |
| Load balancer | `nimbus-core/src/main/kotlin/dev/nimbus/loadbalancer/` |
| Scaling | `nimbus-core/src/main/kotlin/dev/nimbus/scaling/` |
| Database | `nimbus-core/src/main/kotlin/dev/nimbus/database/` |

## Documentation Structure

```
docs/
├── index.md                    — Landing page
├── guide/                      — User-facing guides
│   ├── introduction.md
│   ├── installation.md
│   ├── quickstart.md
│   ├── modpacks.md
│   ├── concepts.md
│   ├── proxy-setup.md
│   ├── server-groups.md
│   ├── scaling.md
│   └── multi-node.md
├── config/                     — Configuration reference
│   ├── nimbus-toml.md
│   ├── groups.md
│   ├── templates.md
│   ├── permissions.md
│   └── display.md
├── reference/                  — Technical reference
│   ├── commands.md
│   ├── api.md
│   ├── websocket.md
│   └── events.md
└── developer/                  — Developer docs
    ├── architecture.md
    ├── sdk.md
    ├── bridge.md
    └── signs.md
```
