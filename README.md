# Voxy World Gen V2

![Logo](src/main/resources/logo.png)

This is a rewrite of my old Voxy World Gen mod, this mod is NOT a fork of the passive chunk generator mod and instead is a entirely different mod.

## Features

- Generates chunks very fast in the background and auto-ingest them with voxy.
- Configurable generation speed and queue size.
- Tellus integration. https://github.com/Yucareux/Tellus
- Server-side support

## Dependencies

- **Minecraft**: 1.21.6 - 1.21.11 (Tested on 1.21.11, anything less is considered unstable and may not work)
- **Fabric Loader**: >= 0.16.0
- **Java**: 21 (Required)
- **Fabric API**
- **Cloth Config**: >= 15.0.127

## Building

This project requires Java 21.

```bash
# Clone the repo
git clone https://github.com/iSeeEthan/voxy_worldgen_v2.git

# Build
./gradlew build
```

Artifacts are output to `build/libs/`.

## Configuration

Config files are located in `config/voxyworldgenv2.json`. The throttle options are also on their own page of the Mod Menu config screen.

| Option | Default | Effect |
|---|---|---|
| `throttleEnabled` | `true` | Slow down or pause generation when memory runs low or the game lags. |
| `throttleTargetMspt` | `40` | While server ticks take longer than this (ms), the number of chunks generating at once is halved every second. |
| `throttleMinFps` | `20` | Singleplayer and LAN host with the window focused: the same while the frame rate is below this. `0` turns it off. |
| `throttleSlowHeapPercent` | `75` | Above this share of the maximum heap still in use after the last garbage collection, fewer chunks generate at once, down to one near the pause level. |
| `throttlePauseHeapPercent` | `85` | Generation pauses above this share and resumes below it. |

- Below 18 TPS generation is slowed the same way, below 10 TPS it pauses.
- Without any of these conditions the limit grows back towards `maxActiveTasks` by a tenth of it per second.
- The F3 overlay and `/vwg status` show the current limit and the reason.

## Commands

| Command | Effect |
|---|---|
| `/vwg on`, `/vwg off`, `/vwg toggle` | Turn generation on or off. Saved as `enabled` in the config, the same as "Mod Enabled" in the config screen. |
| `/vwg status`, `/vwg` | Whether generation runs, the throttle state, heap, tick time, TPS, FPS, and progress. |

The commands need operator rights, or the owner of a singleplayer or LAN world (also without cheats). The "Toggle Generation" key (unbound by default, under Options → Controls → Voxy World Gen V2) runs `/vwg toggle`.

## License

CUSTOM, refer to LICENSE file for more information.
