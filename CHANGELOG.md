# Changelog

## 0.7.9

- The hologram editor now uses the vanilla Minecraft GUI style: panel, slots and buttons come from the game's own textures, so resource packs (including dark ones) theme it too.
- Label colors adapt to the panel brightness, staying readable on light and dark packs.
- Cleaner spacing and alignment throughout the editor; small buttons no longer lose their bottom border.

## 0.7.8

- New key L (rebindable) toggles link lines fully on/off, remembering the previous mode.
- Holograms are now linked automatically: when an output item of one hologram matches an input item of another, an animated line connects the two icons. Press Ctrl+H to cycle between lines for the looked-at hologram, all lines, or none. In the looked-at mode the lines follow the recipe you look at and stay visible for about 10 seconds after you look away.
- New config options `linkMode`, `linkColor` and `linksThroughWalls`.

## 0.7.7

- A hologram can now hold up to 8 recipes. Switch between them with the arrows above the slots in the editor, add or remove recipes with the + and - buttons.
- Each recipe can have its own name, shown above its row.
- Recipes are shown as separate rows in the world, with their arrows lined up.
- A hologram without a title no longer shows "Factory Holograms" on top; the panel is simply shorter.

## 0.7.6

- Aim at an icon in a world hologram to highlight it and see its tooltip next to the crosshair. Hold Shift for the full tooltip.
- While aiming at an icon, press NEI's recipe (R) or usage (U) key to open its recipes or uses.
- New `hoverTooltips` config option to turn this off.

## 0.7.5

- Icons can now be dragged or clicked from NEI's recently viewed (history) and craftables panels.
- Dropping any icon on the name field fills in that item's name.
- New labels are placed 0.50 lower by default; this height is shown as Y 0. Existing labels do not move.

## 0.7.4

- Added the MIT license to the repository and regular mod JAR.
- Made the main README English and kept a Ukrainian version in `README.uk.md`.
- Clarified installation, icon limits, targeting and manual label behavior; moved build instructions out of the player guide.
- Corrected build instructions to require JDK 25 for the current GTNH Gradle plugin.
- Recorded the maintainer's test pack: GTNH 2.9.0 RC-2. This does not claim separate Angelica or multiplayer NEI verification.
- No gameplay or label-format changes from 0.7.3.

## 0.7.3

- Added manual hologram rotation and tilt; disable auto-rotation in the config to edit angles.
- Retained personal client-only labels, inventory/optional NEI icons, position preview and local per-world storage.
