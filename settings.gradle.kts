rootProject.name = "nimbus"

// ── Core ────────────────────────────────────────────────
include("nimbus-core")
include("nimbus-protocol")
include("nimbus-agent")
include("nimbus-cli")

// ── Plugins (Minecraft server JARs) ────────────────────
include("plugins:sdk")
include("plugins:bridge")
include("plugins:display")
include("plugins:perms")

// ── Modules (controller modules) ───────────────────────
include("modules:api")
include("modules:perms")
include("modules:display")
include("modules:scaling")
include("modules:players")
include("modules:notifications")
include("modules:backup")
include("modules:anomaly")
