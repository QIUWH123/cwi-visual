# Contributing To CWI Visual

Use Java 21, the Gradle wrapper and the exact CWI dependency inputs below.
Keep gameplay and pollution in Core and title/loading presentation in Menu.
CWI Visual owns synchronized ash selection and client world presentation.

## Build And Verify

Use the included Gradle wrapper with Java 21. Obtain the exact authorized CWI
modpack dependency copies and place them in local `libs/` before building.
Dependency JARs are intentionally excluded from Git and release artifacts.

| Local filename | SHA-256 |
| --- | --- |
| `particlerain-4.0.3-1.21.1-neoforge.jar` | `1e33e0e74fc8fcdb35f28c6142e63f7df1aede044bcd1eaf933ba0c0bf0b5435` |
| `AsyncParticles-21.1.4.12-1.21.1.jar` | `6e35a69e7596667793e2b5c3f3564f79e8de5fb2cd18383ad7f27d14ec0f1402` |
| `sodium-neoforge-0.8.13+mc1.21.1.jar` | `575d187b15328d7bddd8a9b1bee64333c22c14735f5780ec49eac0cd5503c08d` |

```sh
./gradlew clean check build --no-daemon
```

Add `--offline` when Gradle inputs are already cached. The distribution is
`build/libs/cwi_visual-0.2.5-dev.jar`. The twelve headless verification tasks
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

## Licensing Contributions

Original CWI Visual code is licensed under GNU GPL version 3 only
(`GPL-3.0-only`). New original contributions use that license. Preserve the
Firelight MIT copyright, permission notice and source attribution in adapted
files and `src/main/resources/META-INF/LICENSE-FIRELIGHT.txt`. See `NOTICE`
for the licensing boundary. External dependency JARs remain excluded.

The root `LICENSE` contains the full GPL v3 text. Gradle packages `LICENSE`,
`NOTICE` and the separate Firelight notice into `META-INF/` and verifies the
license metadata. License/README maintenance does not authorize rewriting
existing tags or release assets. Publish future binary changes under a new
release version.
