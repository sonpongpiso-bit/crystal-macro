# Crystal Macro

Fabric client mod for **Minecraft 26.1.2**. While you hold an End Crystal and hold right-click, it:

1. Places obsidian at the current crosshair target (500 ms cooldown).
2. Switches back to the End Crystal if a hotbar swap was needed.
3. Places an End Crystal on the obsidian you are looking at.
4. Breaks that crystal with a vanilla attack.
5. Repeats until you release right-click.

All actions go through normal client interaction methods (`MultiPlayerGameMode.useItemOn` / `attack`). The world is not edited locally, and targets are re-read from `Minecraft.hitResult` before every place/break.

## Requirements

- Minecraft **26.1.2**
- Fabric Loader **0.20.1+**
- Fabric API **0.155.3+26.1.2** (or another 26.1.2-compatible build)
- Java **25** (needed both to play this version and to compile the mod)

## In-game use

1. Put End Crystals in your main hand or offhand.
2. Put Obsidian in your offhand or hotbar.
3. Look at a block and **hold right-click**.
4. Release right-click to stop immediately.

Notes:

- Obsidian is only auto-placed once per attempt, with a **0.5s** cooldown. If obsidian is already at the destination, it will not place another block there.
- Crystal place/break only happens when your crosshair is on that obsidian (or on the crystal sitting on it). Looking away cancels that position.
- Vanilla right-click is suppressed while the macro is active so the game does not double-place.

## Build with Gradle (local)

Install JDK 25, then from this folder:

```bash
# Linux / macOS
chmod +x gradlew
./gradlew build

# Windows
gradlew.bat build
```

The playable jar is:

```
build/libs/crystal-macro-1.0.0.jar
```

Copy that file into `.minecraft/mods` together with Fabric API.

## Build with GitHub Actions

1. Create a new GitHub repository.
2. Upload this project (include `gradlew`, `gradlew.bat`, and `gradle/wrapper/`).
3. The workflow in `.github/workflows/build.yml` runs on every push and on `workflow_dispatch`.
4. Open the **Actions** tab → latest **Build** run → **Artifacts** → download `crystal-macro-jar`.

You can also download the jar from a release if you enable the optional release step yourself.

### Manual `workflow_dispatch`

On GitHub: **Actions → Build → Run workflow**.

## Project layout

```
src/client/java   client entrypoint and macro logic
src/main/java     shared mod id
src/main/resources/fabric.mod.json
```

No mixins. Targeting, cooldowns, and duplicate-placement guards live in `CrystalMacroHandler`.
