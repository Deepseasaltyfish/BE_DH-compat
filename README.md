this mod uses Distant Horizons API for dev:https://modrinth.com/mod/distanthorizonsapi
this mod needs Distant Horizons as dependency:https://modrinth.com/mod/distanthorizons

Current Supported mod:
LittleTiles
ImmersiveRailroading

# BeLodCompat

This mod improves how Distant Horizons renders blocks from certain mods. Without it, those blocks may look wrong or disappear entirely in the distance.

## Features

Without this mod, LittleTiles blocks are treated as avoided blocks by Distant Horizons (similar to flowers), making them mostly invisible or appearing as gray when visible. Immersive Railroading rails appear as pure white.

- **LittleTiles** – Extracts one tile's block state and color from LittleTiles blocks. Color blending requires database caching to be enabled and the Distant Horizons config option `common.lodBuilding.worldCompression` set to `"MERGE_SAME_BLOCKS"` for stable rendering.
- **Immersive Railroading** – Reads the rail bed fill material ID from the parent block's NBT. If the fixed block override is not enabled, database caching must be active to ensure reliable long‑distance display.
- **Database support** – Each dimension maintains its own SQLite database, stored in separate locations:
  - Singleplayer: `<save_folder>/belodcompat/<dimension>.db`
  - Multiplayer client: `.minecraft/belodcompat_servers/<server_ip>/<dimension>.db`
  - Dedicated server: `<world_root>/belodcompat/<dimension>.db`
  This per‑dimension isolation prevents data mixing and ensures correct LOD rendering across all dimensions.

## Requirements

- Minecraft: 1.20.1 (Forge) or 1.21.1 (NeoForge)
- Distant Horizons 3.0.2 or later
- LittleTiles (optional)
- Immersive Railroading (optional)

## Configuration

A config file is generated at `config/belodcompat.cfg`. The file is automatically reloaded when changed (except for `enableDatabase`, which requires a restart). The following options are available:

| Option | Description | Default |
|--------|-------------|---------|
| `debugLogging` | Enable debug logging (some debug messages may not be written to the log in certain situations) | `false` |
| `overrideIrRailBlock` | Force all IR rails to use a custom block instead of their bed fill material | `false` |
| `overrideIrRailBlockId` | Block ID used when override is enabled (falls back to `minecraft:soul_sand` if invalid) | `minecraft:soul_sand` |
| `getIrDataFromParentDirectly` | Retrieve bed fill color directly from parent rail (faster but may slow down slightly) | `false` |
| `replaceIrIfAir` | Replace the bed fill color with a fallback if the original is air | `false` |
| `enableDatabase` | Enable SQLite database caching (requires restart) | `true` |
| `useAsyncDbWrite` | Use asynchronous database writes (better performance but may lose data on crash) | `true` |

## Commands

The mod adds the `/belodcompat` command with the following subcommands:

| Command | Description |
|---------|-------------|
| `/belodcompat dumpIR [dimension]` | Dump all cached IR block states for the specified dimension (or current dimension if omitted) |
| `/belodcompat dumpLT [dimension]` | Dump all cached LT block states with colors |
| `/belodcompat showConfig` | Display current configuration values |
| `/belodcompat getLtColor <x> <y> <z> [dimension]` | Show the cached LT color at the given position |
| `/belodcompat getBlockStateOfLT <x> <y> <z> [dimension]` | Show the cached LT block state |
| `/belodcompat getBlockStateOfIR <x> <y> <z> [dimension]` | Show the cached IR block state |

## License

LGPL 3.0

## Project Links

GitHub: [https://github.com/Deepseasaltyfish/BE_LOD-compat](https://github.com/Deepseasaltyfish/BE_LOD-compat)