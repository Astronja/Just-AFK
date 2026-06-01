# JustAFK

A **server-side Minecraft Fabric mod** (1.21.11) that adds an `/afk` command with invulnerability, movement blocking, and automatic exit on activity.

## Features

- `/afk` command — toggle AFK mode on/off
- **Invulnerability** — no damage while AFK
- **Movement lock** — velocity zeroed every tick, position/rotation snapped back
- **Auto-exit** — detects keyboard input, mouse movement, and interactions (attack/use/break)
- **On-screen title** — periodic "You are now AFK" reminder
- **Tab-list indicator** — AFK names shown in grey/italic
- **Chat broadcasts** — notifies other players when someone goes AFK or returns

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | ≥0.19.2 |
| Fabric API | Any |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Place the mod `.jar` in your server's `mods/` folder
3. Restart the server

No client-side installation is required — the mod runs entirely on the server.

## Usage

- `/afk` — toggle AFK mode
- Move your mouse, press any movement key, or interact with the world to automatically exit AFK

## Building

```bash
./gradlew build
```

The compiled jar will be at `build/libs/just-afk-1.0.0.jar`.

## License

MIT
