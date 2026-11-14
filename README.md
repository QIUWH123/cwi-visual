# CWI Visual

CWI Visual combines ash weather and persistent world appearance for the Create
Wasteland Industry modpack. Target: Minecraft 1.21.1, NeoForge 21.1.264, Java 21.
Mod ID: `cwi_visual`. Release: `0.2.3-dev`.

## Features

- Indefinite `/weather ashstorm`, stopped only by `/weather clear`; operator
  permission level 2. Rain/thunder require clear first while ash is active.
- Server-saved ash selection and client synchronization; client ash particles,
  surface fog and depth-aware rolling mist share one rendering owner.
- Gentle firelight from an editable exact block-ID list, with bounded tracking
  of the nearest 64 sources. Default sources are torches, soul torches, lanterns,
  soul lanterns, lit campfires, soul campfires, fire and soul fire, including
  torch wall variants. Listed blocks must currently emit light.
- Subtle configurable grass, foliage, water and sky colours in the Overworld.
- Client configuration: `config/cwi_visual-client.toml`, including master and
  individual feature switches, firelight strength/sources and colour targets.

This mod targets the CWI client rendering stack: customized Particle Rain 4.0.3,
AsyncParticles 21.1.4.11 and Sodium 0.8.13+mc1.21.1. These client dependencies
are required. Dedicated servers need none of them. No gameplay damage,
pollution state, title/loading screens, grime textures or new vegetation models
are added. Active shader packs are outside the verified firelight target.

## Build And Verify

Use the included Gradle wrapper with Java 21. Obtain the exact authorized CWI
modpack dependency copies and place them in local `libs/` before building.
Dependency JARs are intentionally excluded from Git and release artifacts.

| Local filename | SHA-256 |
| --- | --- |
| `particlerain-4.0.3-1.21.1-neoforge.jar` | `1e33e0e74fc8fcdb35f28c6142e63f7df1aede044bcd1eaf933ba0c0bf0b5435` |
| `AsyncParticles-21.1.4.11-1.21.1.jar` | `4bc63a92412ec57dcd7cf551f5e69d75f8549d2a5eec01fd21717b45f2ecf02a` |
| `sodium-neoforge-0.8.13+mc1.21.1.jar` | `575d187b15328d7bddd8a9b1bee64333c22c14735f5780ec49eac0cd5503c08d` |

```sh
./gradlew clean check build --no-daemon
```

Add `--offline` when Gradle inputs are already cached. The distribution is
`build/libs/cwi_visual-0.2.3-dev.jar`. The twelve headless verification tasks
cover weather commands/state/network, ash/fog/caches/budgets/lifecycle, colour,
firelight and exact installed integration contracts. Selected fixtures use
minimal in-memory Minecraft objects without a game, server, window or GPU.
They do not establish full loader transformation, multiplayer, visual acceptance
or full-pack FPS. JAR replacement requires a restart.

## Releases

Publish from the checked source commit using a GitHub Release tagged
`v<mod_version>`. Attach only the verified distribution JAR and its SHA-256
checksum; keep dependencies, private documentation/evidence, local configuration,
logs, runtime saves and backups out of the repository and release. Release notes
must identify Java/Minecraft/NeoForge and client dependency requirements and
separate headless verification from runtime acceptance. Development versions
are prereleases. Do not rewrite existing tags or historical artifacts.

## Licensing

Original CWI Visual code and assets are All Rights Reserved. Firelight-derived
portions remain under upstream MIT terms, with source attribution in adapted
files and the complete notice in
[src/main/resources/META-INF/LICENSE-FIRELIGHT.txt](src/main/resources/META-INF/LICENSE-FIRELIGHT.txt).
Upstream: [comedy01/Firelight](https://github.com/comedy01/Firelight).
Third-party dependencies are not bundled. Wasteland Vision was behavior-only
research; none of its code or assets is included.
