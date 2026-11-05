# SurvivalKit

**English** · [Türkçe](README.tr.md)

A client-side [Fabric](https://fabricmc.net/) mod for Minecraft survival: eating, totems, tools, weapons, bridging, tree and vein breaking, and world downloading, all from one in-game menu.

Installed on the client only; the server needs nothing. The interface is available in English and Turkish.

## Download

Get the jar from [Releases](https://github.com/ahmetelcin/SurvivalKit/releases).

| Minecraft | SurvivalKit | Fabric Loader | Java |
|---|---|---|---|
| 26.3 | [0.13.1](https://github.com/ahmetelcin/SurvivalKit/releases/tag/v0.13.1-mc26.3) | 0.19.5 or newer | 25 |
| 26.2 | [0.12.1](https://github.com/ahmetelcin/SurvivalKit/releases/tag/v0.12.1-mc26.2) | 0.19.3 or newer | 25 |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and the SurvivalKit jar in your `mods` folder.
3. Start the game and press **Right Shift** to open the menu. You can rebind this key under *Options → Controls → Key Binds*.

## Modules

| Module | What it does |
|---|---|
| **Auto Eat** | Eats when hunger drops below your threshold, then leaves the hotbar as it found it. Has food preferences, a blacklist and golden apple rules. |
| **Auto Totem** | Keeps a totem of undying in your off hand and replaces it when it is used. |
| **Auto Tool** | Takes out the best tool when you start breaking a block, and puts back what you held when you stop. |
| **Tool Picker** | Middle-click a block to take the best tool for it. |
| **Auto Weapon** | Picks the best weapon for your target: Smite for the undead, Bane of Arthropods for spiders, Sharpness for the rest. Can also attack automatically and prepare a weapon as mobs come near. |
| **Scaffold** | Places a block under your next step as you walk over a gap. Has Y lock, ramps, bridge width, safe walk and torches. Your view is never turned. |
| **Vein Breaker** | Breaks a whole tree or ore vein at once using the server's own commands. Does nothing without permission for `/setblock`. |
| **Build Mode** | Marks an area with a wand. Vein Breaker and Scaffold stay inside it. The area is saved per server and dimension. |
| **World Downloader** | Saves the chunks you visit on a server, including containers and entities, as a world you can open in singleplayer. Stops if the server forbids it through the old World Downloader permission channel. |
| **HUD** | On-screen boxes showing active modules and their status, positioned with a drag-and-drop editor. |

**Profiles and presets**

- **Profiles:** keep separate setups, and switch automatically per server using address rules.
- **Safe profile:** a built-in profile with every module off.
- **Presets:** each module has built-in presets, and you can save your own.
- **Settings:** every setting has a tooltip, and the menu search covers modules and settings.

The full list of settings and commands is in the [user guide](docs/USAGE.md). Version history is in the [changelog](CHANGELOG.md) (in Turkish).

## Using it on servers

**Check a server's rules before using this mod there. Many servers ban automation like Auto Weapon, Scaffold or world downloading, and using it can get you banned, whatever the mod does or doesn't do.**

SurvivalKit doesn't bypass anticheats. It sends no fake movement or fall packets and doesn't delay packets; modules only do what a normal player is allowed to do. That doesn't make it undetectable or permitted.

The *Safe* profile and per-server profile rules help you keep modules off where they aren't allowed. No server has a rule out of the box.

## Build from source

Requires JDK 25.

```sh
./gradlew build
```

The jar is written to `build/libs/`.

Decision logic lives in plain Java classes tested without the game, and a thin layer talks to Minecraft. That layer is tested with client gametests that use real clicks and key presses. The mod uses no mixins.

```sh
./gradlew test runClientGameTestParallel
```

## License

[MIT](LICENSE)
