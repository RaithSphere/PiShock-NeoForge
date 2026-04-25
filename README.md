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
- Minecraft 1.21.1
- NeoForge
- Cloth Config (NeoForge build)

## Setup
- Launch Minecraft and open **PiShock Setup** from the main menu icon.
- Enter your PiShock username and API key.
- Use **Fetch IDs** to pull account/device routing values.
- Optionally use **Check API** to validate API + websocket connectivity before live use.
- Save config, then test with low values first.

## Credits
- Thanks to `ScoreUnder` (`pishock-zap-fabric`) for pointing out the limit-exceeding bug scenario.
- Thanks to `ScoreUnder` (`pishock-zap-fabric`) for the config-screen reference/design inspiration used here.
