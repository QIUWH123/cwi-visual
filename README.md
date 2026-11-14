# CWI Visual

CWI Visual adds ash storms, gentle firelight and weathered world colours to the
Create Wasteland Industry modpack.

## Features

- Ash particles, grey surface fog and rolling ground mist, with ash weather
  saved by the server and synchronized to players.
- Gentle flickering light around selected torches, lanterns, campfires and fire.
- Configurable grass, foliage, water and sky colours, applied to the Overworld
  by default.
- Client switches for ash effects, firelight and colours, plus adjustable
  firelight strength, source blocks and colour targets.

## Usage

Install the mod on clients for its visual effects and on the server to enable
ash weather commands. Operators with permission level 2 can use:

```text
/weather ashstorm
/weather clear
```

Ash takes no duration and continues until `/weather clear`. Clear ash before
using `/weather rain` or `/weather thunder`.

Open the mod's configuration through the Mods screen, or edit
`config/cwi_visual-client.toml`. Under `[firelight]`, `strength = 0` disables
flicker and `sources` lists the exact block IDs allowed to flicker. Add an ID
to include a block or remove it to exclude one; listed blocks must currently
emit light. Unlit and waterlogged blocks are excluded.

Default sources are torches and wall torches, soul torches and soul wall
torches, lanterns and soul lanterns, lit campfires and soul campfires, fire and
soul fire. Colour settings control each target RGB colour, blend strength and
the dimension list. Restart Minecraft after replacing the JAR.

## Requirements

- Minecraft **1.21.1**, NeoForge **21.1.264–21.1.x**, Java **21**.
- Clients: the **CWI customized Particle Rain 4.0.3** binary,
  **AsyncParticles 21.1.4.11** and **Sodium 0.8.13+mc1.21.1**.
- Dedicated servers do not need these client rendering dependencies.

This mod targets that CWI rendering stack. Active shader packs and standalone
Firelight disable CWI Visual's firelight. Full-pack graphical and performance
acceptance remains pending. Build and release instructions are in
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

CWI Visual's original code is licensed under the GNU General Public License,
**version 3 only** (`GPL-3.0-only`). See [LICENSE](LICENSE) for the full text.

Firelight-derived portions retain their upstream **MIT** license and attribution;
see the [Firelight notice](src/main/resources/META-INF/LICENSE-FIRELIGHT.txt)
and [NOTICE](NOTICE). External dependencies retain their own licenses and are
not bundled.
