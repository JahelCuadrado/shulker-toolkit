# Shulker Toolkit

Everything you need to live out of shulker boxes: see inside them, move items in and out without
placing them, craft, build and refill straight from them.

Fabric mod for Minecraft 26.3.

## Features
- **See inside from the tooltip:** hover a shulker box to see its 27 slots in a chest-style window
  tinted with the box's colour, with the name of the selected item. Your ender chest shows its
  contents over an End starfield, and bundles can show a leather panel. Choose when contents show:
  always, never, while holding a key or with a toggle key.
- **Move items without placing the box:** right-click to put items in or take them out (or split
  it like vanilla bundles: left click puts in, right click takes out), drag across slots to fill or
  empty several at once, hold `Ctrl`/`Cmd` to move one item at a time and use the mouse wheel to
  pick the slot you want. Works with your ender chest too, and with bundles if you turn them on.
- **Look inside boxes inside boxes:** select a box or bundle in a tooltip and press Enter or Space
  to peek inside it.
- **Know at a glance:** box icons show a fill bar, and a green **+** when the box already holds the
  item you carry and has room for more.
- **Middle-click windows:** middle-click a shulker box, a bundle or your ender chest to open its
  window next to your inventory, a crafting table, a chest or a furnace, and use it like a chest.
  Open up to 6 at once, beside the screen or above and below the recipe book, each with its own
  opening sound. An eye marks the boxes that are open. In creative, hold `Ctrl` while
  middle-clicking.
- **Craft from your boxes:** the recipe book counts the materials inside your shulker boxes and
  takes them out for you. Four depth levels: the boxes in your inventory, then your ender chest,
  the boxes inside it and the bundles inside those boxes.
- **Pick block from your boxes:** middle-click a block you only have inside a shulker box and you
  get it in your hand: one block or a full stack, your choice.
- **Auto refill:** when the stack in your hand runs out (blocks, food, potions), it refills from
  your shulker boxes.
- **Pick up into boxes (optional, off by default):** send items into your boxes when your inventory
  is full, or straight into the box that already holds them.
- **Build schematics from boxes:** Litematica Easy Place, manual placement and the Litematica
  Printer take the blocks they need out of your shulker boxes.
- **Simple settings screen** with no extra libraries (Mod Menu supported), plus keys to switch
  the main options on the fly.
- **Lightweight:** no noticeable impact on performance.
- **Languages:** English, Español (España), Español (México), Português (Brasil), Português (Portugal),
  Français, Deutsch, Italiano, Nederlands, Polski, Türkçe, Русский, Українська, 简体中文, 繁體中文,
  日本語 and 한국어.

## Servers

Install it on both client and server for the full feature set (singleplayer works out of the box).
On servers without the mod you still see the contents of shulker boxes and bundles in tooltips and
the icon markers. Server owners can turn features off for other players in
`config/shulkertoolkit-server.json` (the singleplayer/LAN host is never limited):

```json
{
  "schemaVersion": 1,
  "allowTransfer": true,
  "allowPick": true,
  "allowRefill": true,
  "allowEnderPreview": true,
  "allowMouseDragging": true,
  "allowSingleItemMovement": true,
  "allowPickupIntoBoxes": true,
  "allowCraftFromBoxes": true,
  "allowPinnedWindows": true
}
```

## Requirements

Minecraft 26.3 · Fabric Loader 0.19.5+ · Fabric API · Java 25. Mod Menu and Litematica optional.

## Development

Requires JDK 25. `./gradlew runClient` · `./gradlew build` (compile, format check, JUnit, GameTests)
· `./gradlew spotlessApply`.

## License

MIT.
