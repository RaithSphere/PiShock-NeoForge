# PiShock NeoForge Mod

![](https://img.shields.io/badge/Enviroment-Client-purple?style=for-the-badge) ![](https://img.shields.io/badge/Loader-NeoForge-a8320c?style=for-the-badge) ![Build and unit tests](https://img.shields.io/github/actions/workflow/status/RaithSphere/PiShock-NeoForge/build.yml?branch=main&label=Build%20%26%20Unit%20Tests&style=for-the-badge)

Not intended for users below the age of 18.

This project carries personal risk and was developed as a masochistic gameplay mod. If you are below the age of majority, please do not use it.

## Notable Features
- Works in multiplayer, including non-modded servers.
- Uses PiShock's current websocket broker API (`wss://broker.pishock.com/v2`).
- Supports local serial communication with PiShock V3 hubs over USB.
- Supports Shock, Vibrate, and Beep modes.
- Millisecond-precise duration settings (`100ms` to `15000ms`).
- Queues and combines damage events that occur in quick succession.
- Limit-respecting failsafes at dispatch time.
- Broker error reporting in chat with anti-spam throttling.
- Optional success confirmation in chat, including intensity and duration.
- Includes death-trigger support.
- Auto-discovery for `UserId`, `HubId`, and `ShockerId` (`/Account` + `/Shockers`).
- In-game setup UI with:
  - `Fetch IDs`
  - `Check`
  - `Test`
  - `Serial`
- Quick enable/disable hotkey (default `F12`).
- Client debug command:
  - `/pishock debug`
  - `/pishock debug true`
  - `/pishock debug false`
- Config entry points:
  - Main menu icon button
  - Pause menu icon button
  - Mod config screen integration

## Before You Use
- If you need to stop immediately, close the game (`Alt+F4` on Windows).
- Test your emergency stop path before real use.
- Set up and test the quick toggle hotkey (`F12` by default).
- Start with conservative intensity and duration values and verify behavior in a safe test world first.
- Confirm your PiShock API key was generated on or after `2024-10-15` (required by websocket API login).

## Requirements
- A supported Minecraft 1.21.x version
- NeoForge
- Cloth Config (NeoForge build)

## Setup
- Launch Minecraft and open **PiShock Setup** from the main menu icon.
- Enter your PiShock username and API key.
- Use **Fetch IDs** to pull account/device routing values.
- Use **Transport** to choose `WebSocket` or `Serial`.
- For serial transport, open the **Serial** tab, connect a PiShock V3 hub over USB, set **Serial Port** or leave it blank for auto-detection, then use **Serial** to read firmware/device info and populate Hub/Shocker IDs when available.
- Optionally use **Check** to validate the selected transport before live use.
- Save config, then test with low values first.

## Comparison table - Thank you to [ScoreUnder] for this list <3 - Removed all abandoned versions

| Feature                         | [Raith's PiShock mod] | [PiShock-Zap]      |
|---------------------------------|-----------------------|--------------------|
| Minecraft versions              | 1.21.x                | 1.17.x - 26.1.x    |
| Author                          | [Raith]               | [ScoreUnder]       |
| Mod loader                      | NeoForge              | Fabric             |
| Client-side                     | :white_check_mark:    | :white_check_mark: |
| Singleplayer                    | :white_check_mark:    | :white_check_mark: |
| Multiplayer                     | :white_check_mark:    | :white_check_mark: |
| Works on vanilla servers        | :white_check_mark:    | :white_check_mark: |
| Low-latency local serial API    | :white_check_mark:    | :white_check_mark: |
| Multiple simultaneous shockers  | :x:                   | :white_check_mark: |
| Vibration support               | :white_check_mark:    | :white_check_mark: |
| Vibration/shock threshold       | :x: / caps only       | :white_check_mark: |
| API connectivity checks         | Basic test button     | Sorta              |
| Vibration test button           | :white_check_mark:    | :x:                |
| In-game quick toggle            | Via hotkey            | Via hotkey         |
| Damage curves                   | Linear scaling        | :white_check_mark: |
| Queued/combined damage events   | :white_check_mark:    | :white_check_mark: |
| Separate shock-on-death config  | :white_check_mark:    | :white_check_mark: |
| Millisecond-precise duration    | :white_check_mark:    | :white_check_mark: |
| Alternative/third-party devices | :x:                   | :white_check_mark: |
| Usable by other mods            | :x:                   | :white_check_mark: |
| Configuration method            | In-game settings      | In-game settings   |
| Configurability                 | Moderate              | Control-freak      |
| Known performance issues        | :ok:                  | :ok:               |
| Known limit-exceeding bugs      | :ok:                  | :ok:               |
| Limit-respecting failsafes      | Multi-level           | Multi-level        |
| Source code available           | :white_check_mark:    | :white_check_mark: |
| Unit tests                      | :white_check_mark:    | :white_check_mark: |

## Credits
- Thanks to [ScoreUnder] for pointing out the limit-exceeding bug scenario, the config-screen reference/design inspiration used here.


[releases]:https://www.curseforge.com/minecraft/mc-mods/pishock
[ScoreUnder]: https://github.com/ScoreUnder
[Raith's PiShock mod]: https://www.curseforge.com/minecraft/mc-mods/pishock
[Raith]: https://github.com/RaithSphere
[PiShock-Zap]: https://github.com/ScoreUnder/pishock-zap-fabric
