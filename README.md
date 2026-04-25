# PiShock NeoForge Mod

![](https://img.shields.io/badge/Enviroment-Client-purple?style=for-the-badge) ![](https://img.shields.io/badge/Loader-NeoForge-a8320c?style=for-the-badge) ![Build and unit tests](https://img.shields.io/github/actions/workflow/status/RaithSphere/PiShock-NeoForge/build.yml?branch=main&label=Build%20%26%20Unit%20Tests&style=for-the-badge)

Not intended for users below the age of 18.

This project carries personal risk and was developed as a masochistic gameplay mod. If you are below the age of majority, please do not use it.

## Notable Features
- Works in multiplayer, including non-modded servers.
- Uses PiShock's current websocket broker API (`wss://broker.pishock.com/v2`).
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
  - `Check API`
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
- Optionally use **Check API** to validate API + websocket connectivity before live use.
- Save config, then test with low values first.

## Comparison table - Thank you to [ScoreUnder] for this list <3

| Feature                         | Raith's PiShock mod | [PiShock-Zap]        | [PiShockForMc]         | [Shockcraft]       | [Minecraft Shock Collar] | [The original Forge mod][original-forge-mod] | [pishock-mc]       |
|---------------------------------|-----------------------|--------------------|------------------------|--------------------|--------------------------|----------------------------------------------|--------------------|
| Minecraft versions              | 1.21.x                | 1.17.x - 26.1.x    | 1.19.x, 1.20.x, 1.21.x | 1.19.3, 1.19.4     | Wide range               | 1.18.2                                       | 1.21               |
| Author                          | [Raith]               | [ScoreUnder]       | [ojaha065]             | [yanchan09]        | [Hepno]                  | [DrasticLp]                                  | [PancakeTAS]       |
| Mod loader                      | NeoForge              | Fabric             | Forge                  | Fabric             | Bukkit                   | Forge                                        | Fabric             |
| Client-side                     | :white_check_mark:    | :white_check_mark: | :white_check_mark:     | :white_check_mark: | :x:                      | :white_check_mark:                           | :white_check_mark: |
| Singleplayer                    | :white_check_mark:    | :white_check_mark: | :white_check_mark:     | :white_check_mark: | :x:                      | :white_check_mark:                           | :white_check_mark: |
| Multiplayer                     | :white_check_mark:    | :white_check_mark: | :white_check_mark:     | :white_check_mark: | :white_check_mark:       | :white_check_mark:                           | :white_check_mark: |
| Works on vanilla servers        | :white_check_mark:    | :white_check_mark: | :white_check_mark:     | :white_check_mark: | :x:                      | :x:                                          | :x:                |
| Low-latency local serial API    | :x:                   | :white_check_mark: | :x:                    | :x:                | :x:                      | :x:                                          | :white_check_mark: |
| Multiple simultaneous shockers  | :x:                   | :white_check_mark: | :x:                    | :x:                | :x:                      | :x:                                          | :x:                |
| Vibration support               | :white_check_mark:    | :white_check_mark: | :white_check_mark:     | :x:                | :x:                      | :x:                                          | :x:                |
| Vibration/shock threshold       | :x: / caps only       | :white_check_mark: | :x:                    | :x:                | :x:                      | :x:                                          | :x:                |
| API connectivity checks         | Basic test button     | Sorta              | :white_check_mark:     | :x:                | :x:                      | :x:                                          | :white_check_mark: |
| Vibration test button           | :white_check_mark:    | :x:                | :white_check_mark:     | :x:                | :x:                      | :x:                                          | :x:                |
| In-game quick toggle            | Via hotkey            | Via hotkey         | :x:                    | Via command        | :x:                      | :x:                                          | :x:                |
| Damage curves                   | Linear scaling        | :white_check_mark: | :white_check_mark:     | :x:                | :x:                      | :white_check_mark:                           | :white_check_mark: |
| Queued/combined damage events   | :white_check_mark:    | :white_check_mark: | :white_check_mark:     | :x:                | :x:                      | :x:                                          | :x:                |
| Separate shock-on-death config  | :white_check_mark:    | :white_check_mark: | :white_check_mark:     | :x:                | :x:                      | :white_check_mark:                           | :white_check_mark: |
| Millisecond-precise duration    | :white_check_mark:    | :white_check_mark: | :x:                    | :x:                | :white_check_mark:       | :x:                                          | :white_check_mark: |
| Alternative/third-party devices | :x:                   | :white_check_mark: | :x:                    | :x:                | :x:                      | :x:                                          | :x:                |
| Usable by other mods            | :x:                   | :white_check_mark: | :x:                    | :x:                | :x:                      | :x:                                          | :x:                |
| Configuration method            | In-game settings      | In-game settings   | In-game settings       | Slash commands     | Configuration file       | In-game settings                             | In-game settings   |
| Configurability                 | Moderate              | Control-freak      | Simple                 | Basic              | Basic                    | Simple                                       | Simple             |
| Known performance issues        | :ok:                  | :ok:               | :ok:                   | :ok:               | :warning:                | :warning:                                    | :ok:               |
| Known limit-exceeding bugs      | :ok:                  | :ok:               | :ok:                   | :ok:               | :ok:                     | :warning: :bangbang:                         | :ok:               |
| Limit-respecting failsafes      | Multi-level           | Multi-level        | Some                   | N/A                | N/A                      | :x:                                          | :x:                |
| Source code available           | :white_check_mark:    | :white_check_mark: | :white_check_mark:     | :white_check_mark: | :white_check_mark:       | :x:                                          | :white_check_mark: |
| Unit tests                      | :white_check_mark:    | :white_check_mark: | :x:                    | :x:                | :x:                      | :question:                                   | :x:                |


## Credits
- Thanks to [ScoreUnder] for pointing out the limit-exceeding bug scenario, the config-screen reference/design inspiration used here.


[releases]:https://www.curseforge.com/minecraft/mc-mods/pishock
[Minecraft Shock Collar]: https://github.com/Hepno/MinecraftShockCollar
[original-forge-mod]: https://score.moe/a/ps/pishock-1.1.1.jar
[DrasticLp]: https://github.com/DrasticLp
[Shockcraft]: https://codeberg.org/yanchan09/shockcraft
[yanchan09]: https://codeberg.org/yanchan09
[PiShockForMc]: https://github.com/ojaha065/PiShockForMC
[ojaha065]: https://github.com/ojaha065
[Hepno]: https://github.com/Hepno
[ScoreUnder]: https://github.com/ScoreUnder
[Raith's PiShock mod]: https://www.curseforge.com/minecraft/mc-mods/pishock
[Raith]: https://github.com/RaithSphere
[pishock-mc]: https://github.com/PancakeTAS/pishock-mc
[PancakeTAS]: https://github.com/PancakeTAS
[PiShock-Zap]: https://github.com/ScoreUnder/pishock-zap-fabric