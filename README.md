# PiShock Forge Mod

![](https://img.shields.io/badge/Enviroment-Client-purple?style=for-the-badge) ![](https://img.shields.io/badge/Loader-Forge-a8320c?style=for-the-badge) ![Build and unit tests](https://img.shields.io/github/actions/workflow/status/RaithSphere/PiShock-NeoForge/build.yml?branch=main&label=Build%20%26%20Unit%20Tests&style=for-the-badge)

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
- Minecraft 1.18.2
- Forge 40.3.12 or newer for Minecraft 1.18.2
- Cloth Config (Forge build)

## Setup
- Launch Minecraft and open **PiShock Setup** from the main menu icon.
- Enter your PiShock username and API key.
- Use **Fetch IDs** to pull account/device routing values.
- Use **Transport** to choose `WebSocket` or `Serial`.
- For serial transport, open the **Serial** tab, connect a PiShock V3 hub over USB, set **Serial Port** or leave it blank for auto-detection, then use **Serial** to read firmware/device info and populate Hub/Shocker IDs when available.
- Optionally use **Check** to validate the selected transport before live use.
- Save config, then test with low values first.

## Comparison table 
Head over to [ScoreUnder]'s [[PiShock-Zap Comparison table]](https://github.com/ScoreUnder/pishock-zap-fabric#big-comparison-table)

## Credits
- Thanks to [ScoreUnder] for pointing out the limit-exceeding bug scenario, the config-screen reference/design inspiration used here.


[releases]:https://www.curseforge.com/minecraft/mc-mods/pishock
[ScoreUnder]: https://github.com/ScoreUnder
[Raith's PiShock mod]: https://www.curseforge.com/minecraft/mc-mods/pishock
[Raith]: https://github.com/RaithSphere
[PiShock-Zap]: https://github.com/ScoreUnder/pishock-zap-fabric
