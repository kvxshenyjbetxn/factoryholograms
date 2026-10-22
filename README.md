# Factory Holograms

[Українською](README.uk.md)

Personal holographic labels for machines and other blocks in **Minecraft 1.7.10 / Forge**, including **GTNH**. Add a name and input/output item icons to keep your factory readable.

**Client-only:** only you see your labels; no server installation is needed. These are manual annotations, not automatic recipe, inventory or machine-progress displays. Real items are never moved or consumed.

## Install

1. Download **`factoryholograms-0.7.4.jar`** from the [latest release](https://github.com/kvxshenyjbetxn/factoryholograms/releases/latest).
2. Put it in your Minecraft instance's **`mods/`** folder, removing any older Factory Holograms JAR.
3. Start the game.

GregTech and NEI are optional. Do not install files named `dev` or `sources`.

<details>
<summary>Upgrading from 0.4.0 or earlier</summary>

If an old server-side version is still installed, ask the administrator to back up its labels and remove it from the server. Old server labels are not migrated automatically.

</details>

## Create a label

1. Look at a nearby machine or block and press **Shift+H** (within 6 blocks, subject to your normal targeting reach).
2. Enter a name. Drag items from your inventory or **NEI** into the input/output slots, or click an item and then a slot. Only the icon is copied.
3. Adjust the position if needed, then click **Apply** to save.

You can add **16 input icons and 16 output icons**, with 4 per row. **Right-click** a slot to clear it.

To edit a label, target its **original block** and press Shift+H again. **Delete** removes the label, not the block. **Escape discards unsaved changes**; if you are holding a cursor copy, the first Escape only clears that copy.

## Controls and appearance

| Action | Key or setting |
| --- | --- |
| Create or edit a label | **Shift+H** |
| Show / hide all labels | **H** |
| Hide labels with the HUD | **F1** |
| Rebind H (Shift uses the same binding) | **Options → Controls → Factory Holograms** |
| Colors, opacity, distance, auto-rotation | **Mods → Factory Holograms → Config** |

- New labels are placed near the selected block's side; targeting the top or bottom chooses a side facing you.
- **X/Y/Z − / +** moves the label by **0.25 blocks**, or **1 block with Shift**. **Reset position** restores its initial placement.
- **Position preview** hides the large editor so you can see the result. **Back to editor** returns without losing your draft.
- Labels face the player by default. For manual angles, disable **Automatically face the player** in the config and open **Rotation / tilt** in the editor.

Position and angle changes are previewed immediately; **Apply** saves them.

## Backups and moving to another PC

Labels are stored locally, separately for each server, single-player world and dimension. Close the game and copy the **`factoryholograms/`** folder from your instance's game directory to back them up or transfer them. In Prism/MultiMC, use that instance's game directory, not a shared `.minecraft` folder.

**`/fholograms path`** shows the current label file's exact path. Subsequent saves keep the previous file as **`.bak`**. Changing the server address or single-player world folder may select a different label store.

## Troubleshooting and compatibility

- **Editor will not open:** move closer, target a block and check for conflicting H key bindings.
- **Labels are invisible:** use H to show them, turn off F1 mode, and check distance and opacity in the config. Labels do not show through opaque walls.
- **NEI is missing:** check that NEI is installed and its panel is enabled. You can also use your inventory without NEI.
- **Edits disappeared:** use **Apply** to save; Escape discards the draft.

Tested by the maintainer on **GTNH 2.9.0 RC-2**. Separate test results for Angelica rendering and NEI on multiplayer servers are not yet documented. Check your server's rules on client mods.

Report problems in [Issues](https://github.com/kvxshenyjbetxn/factoryholograms/issues), including your pack/mod versions and reproduction steps.

## License and development

[MIT License](LICENSE). Build and contribution instructions: [CONTRIBUTING.md](CONTRIBUTING.md). Release history: [CHANGELOG.md](CHANGELOG.md).
