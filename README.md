# Nimbus

```
               ___  _  __
            .-~   ~~ ~  ~-.
          .~                ~.
        .~   N I M B U S    ~.
    .--~                      ~--.
   (______________________________ )

    The lightweight Minecraft cloud   v0.1.0
```

A lightweight, console-only Minecraft cloud system for small-to-medium networks. Single JAR, TOML config, Velocity-first, Kotlin + Coroutines.

## Features

- **Single JAR** — `java -jar nimbus.jar` starts everything
- **TOML config** — one file per server group, human-readable
- **Auto-scaling** — scales instances up/down based on player count
- **Crash recovery** — auto-restarts crashed servers with attempt limits
- **Velocity integration** — auto-manages proxy server list and forwarding
- **Version compatibility** — supports 1.8.8 to latest via adaptive forwarding (modern/legacy)
- **Auto-download** — fetches Paper, Purpur, and Velocity JARs automatically
- **Via plugins** — auto-downloads ViaVersion/ViaBackwards/ViaRewind for cross-version support
- **Interactive console** — JLine3-powered REPL with tab completion, screen sessions, and live events
- **Setup wizard** — guided first-time setup with template selection and version picking

## Requirements

- Java 21+
- No other dependencies — everything is bundled in the Shadow JAR

## Quick Start

```bash
# 1. Download or build the JAR
java -jar nimbus.jar

# 2. The setup wizard guides you through first-time configuration
#    - Choose a network name
#    - Select server software (Paper, Purpur, Velocity)
#    - Pick Minecraft versions
#    - Configure groups (Lobby, Game servers, etc.)

# 3. Nimbus starts all configured services automatically
```

## Build from Source

```bash
git clone https://github.com/your-org/nimbus.git
cd nimbus
./gradlew shadowJar

# Output: nimbus-core/build/libs/nimbus-core-0.1.0-all.jar
java -jar nimbus-core/build/libs/nimbus-core-0.1.0-all.jar
```

## Directory Structure

```
nimbus/
├── nimbus.jar              # The executable
├── nimbus.toml             # Main configuration
├── groups/                 # One TOML file per server group
│   ├── proxy.toml
│   ├── lobby.toml
│   └── survival.toml
├── templates/              # Server templates (JARs, plugins, worlds)
│   ├── proxy/
│   ├── lobby/
│   └── survival/
├── running/                # Auto-created isolated service directories
│   ├── Proxy-1/
│   ├── Lobby-1/
│   └── Survival-1/
└── logs/                   # Controller logs
    └── nimbus.log
```

## Configuration

### nimbus.toml — Main Config

```toml
[network]
name = "MyNetwork"
bind = "0.0.0.0"

[controller]
max_memory = "10G"
max_services = 20
heartbeat_interval = 5000   # ms

[console]
colored = true
log_events = true
history_file = ".nimbus_history"

[paths]
templates = "templates"
running = "running"
logs = "logs"
```

### Group Config — groups/lobby.toml

```toml
[group]
name = "Lobby"
type = "DYNAMIC"              # DYNAMIC or STATIC
template = "lobby"
software = "PAPER"            # PAPER, PURPUR, or VELOCITY
version = "1.21.4"

[group.resources]
memory = "1G"
max_players = 50

[group.scaling]
min_instances = 1
max_instances = 4
players_per_instance = 40
scale_threshold = 0.8         # Scale up at 80% fill
idle_timeout = 0              # 0 = never stop (lobby), >0 = seconds until empty shutdown

[group.lifecycle]
stop_on_empty = false         # true for game servers
restart_on_crash = true
max_restarts = 5

[group.jvm]
args = ["-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50"]
```

### Group Types

| Type | Behavior |
|------|----------|
| `STATIC` | Always exactly `min_instances` running (e.g., Proxy) |
| `DYNAMIC` | Auto-scales between `min_instances` and `max_instances` |

### Server Software

| Software | Description |
|----------|-------------|
| `VELOCITY` | Velocity proxy — gets port 25565 |
| `PAPER` | Paper server — auto-downloaded from PaperMC API |
| `PURPUR` | Purpur server — auto-downloaded from Purpur API |

## Console Commands

### Service Commands

| Command | Description |
|---------|-------------|
| `list` | Show all running services with status, port, players |
| `start <group>` | Start a new instance of a group |
| `stop <service>` | Gracefully stop a service |
| `restart <service>` | Stop and restart a service |
| `screen <service>` | Attach to service console (ESC or Ctrl+Q to detach) |
| `exec <service> <cmd>` | Execute a command on a service |
| `logs <service>` | Show recent log output |

### Group Commands

| Command | Description |
|---------|-------------|
| `groups` | List all configured groups with instance counts |
| `info <group>` | Show group config, scaling rules, active instances |
| `create` | Interactive group creation wizard |

### Network Commands

| Command | Description |
|---------|-------------|
| `status` | Full cluster overview: groups, resources, uptime |
| `players` | List all connected players and their server |
| `send <player> <service>` | Transfer a player to another service |

### System Commands

| Command | Description |
|---------|-------------|
| `reload` | Hot-reload group TOML files without restart |
| `shutdown` | Ordered shutdown: game servers, lobbies, then proxy |
| `clear` | Clear console output |
| `help` | Show command list |

## Architecture

```
┌────────────────────────────────────────────┐
│              Nimbus Console                 │
│          (Interactive JLine3 REPL)         │
└──────────────────┬─────────────────────────┘
                   │
┌──────────────────▼─────────────────────────┐
│            Nimbus Controller               │
│                                            │
│  Group Manager  │  Service Registry        │
│  Scaling Engine │  Process Manager         │
│  Template Store │  Event Bus               │
└──────────────────┬─────────────────────────┘
                   │
┌──────────────────▼─────────────────────────┐
│        Dynamic Services (JVM Processes)    │
│                                            │
│  [Velocity]  [Lobby-1]  [Lobby-2]  [BW-1] │
└────────────────────────────────────────────┘
```

### Velocity Forwarding

Nimbus automatically manages Velocity forwarding mode:

- **All servers 1.13+** → `modern` forwarding (shared secret, most secure)
- **Any server pre-1.13** → `legacy` (BungeeCord) forwarding (compatible with all versions)

### Port Allocation

- Proxy: `25565` (standard Minecraft port)
- Backend servers: `30000+` (sequential, hidden from direct access)

## Tech Stack

| Component | Choice |
|-----------|--------|
| Language | Kotlin 2.1.x |
| Build | Gradle 8.x + Shadow plugin |
| JVM | Java 21 |
| Async | kotlinx-coroutines |
| Config | ktoml (TOML parsing) |
| Console | JLine 3 |
| Logging | slf4j + logback |
| HTTP | ktor-client |

## License

MIT — see [LICENSE](LICENSE)
