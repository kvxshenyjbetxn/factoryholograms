# Development

## Build

Use **JDK 25**. The current GTNH Gradle plugin requires Java 25 or newer; JDK 21 is not sufficient. The mod targets **Java 8 bytecode**; the build JDK is not the game's Java requirement.

```sh
./gradlew build
```

On Windows, use `gradlew.bat build` or `build.bat`. If `build.bat` selects a locally installed JDK you do not want, use `gradlew.bat` directly with your preferred `JAVA_HOME`.

The Gradle wrapper downloads the build tools and dependencies. Artifacts are written to `build/libs/`; install the regular JAR, not `dev` or `sources`. NEI and CodeChickenCore are compile-only dependencies and are not bundled. The regular JAR includes the MIT license in `META-INF/LICENSE`.

## Tests and game checks

`./gradlew test` runs the automated tests. The HTML report is at `build/reports/tests/test/index.html`. These tests cover label storage, layout, validation and client-only contracts, not in-game OpenGL rendering or complete modpack compatibility.

The maintainer reports testing on **GTNH 2.9.0 RC-2**. Separate Angelica and multiplayer NEI results have not yet been documented. Before reporting broader compatibility, manually check:

- Creating, editing and deleting labels, including Escape/Apply behavior.
- Copying inventory and NEI icons without moving items or sending inventory actions.
- Rendering with Angelica, opaque walls, F1, rotation and different GUI scales.
- Saving across restarts, reconnects, dimensions and single-player worlds.
- Connecting to a server without the mod installed and checking key-binding conflicts.
- Frame time with many labels in a factory.

Record the pack, Java and relevant mod versions with the results.

## Changes and bug reports

Keep [README.md](README.md) and [README.uk.md](README.uk.md) aligned when controls or user-facing behavior change. Include reproduction steps, expected/actual behavior and relevant logs in [Issues](https://github.com/kvxshenyjbetxn/factoryholograms/issues). Add regression tests where practical.

Factory Holograms is released under the [MIT License](LICENSE).
