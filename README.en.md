# Selective Rendering

English | [简体中文](README.md)

A Fabric client-side mod that makes any block translucent or hides it, based on "region + block list".

Ported from the selective rendering feature of [Lucidity](https://github.com/hotpad100c/lucidity2.0) (original author ryan100c, licensed CC-BY-NC-4.0),
rewritten for the new rendering architecture of MC 26.1 ~ 26.3, with compatibility for Sodium / Indigo.

---

## Features

- **Fade blocks**: modifies vertex alpha and moves blocks into the translucent render layer, keeping geometry and lighting.
- **Transparency**: 0% = fully opaque, 100% = completely hidden.
- **Modes**: inside/outside selection × listed/unlisted, 9 modes in total.
- **Invert**: show only the specified blocks instead.
- **Interact through**: the crosshair ignores faded blocks and targets what is **behind** them (outline, breaking cracks and interactions all behave like vanilla). Can be bound to a toggle key; unbound by default.
- **Selections**: works like a schematic-projection mod.
- **Change recorder**: records the areas where blocks / states changed.
- **Presets**: save the current "mode / transparency / blocks / selections" for quick switching.
- **Night vision**: renders blocks at full brightness and skips all lighting calculations.

---

## Usage

The default wand is a **breeze rod** — hold it in your hand to enable the operations below.

### Mouse controls

| Action                | Effect                                        |
| --------------------- | --------------------------------------------- |
| Left click            | Set corner 1 (red box)                        |
| Right click           | Set corner 2 (blue box)                       |
| Shift + left click    | Clear both corners                            |
| Middle click          | Select a corner or a region; air clears       |
| Selection key + left  | Add a selection or remove an existing one     |
| Selection key + right | Clip every stored selection with the current one |
| Operation key + left  | Add / remove a block or entity (to the selected region's local list if any) |
| Operation key + right | Block with states / item entity by item       |
| Ctrl + arrows / Ctrl + scroll (region selected) | Move the region's projection |
| Ctrl + Z / Ctrl + Y   | Undo / redo selection add, remove and property edits |
| Left Ctrl + scroll    | Cycle modes                                   |
| Left Alt + scroll     | Move the currently selected corner            |

### Hotkeys

| Key             | Default  | Effect                              |
| --------------- | -------- | ----------------------------------- |
| Open config     | `X + V`  | Open the config screen              |
| Record          | unbound  | Start / stop recording block changes |
| Interact through| unbound  | Toggle "interact through"           |

> The record key and the interact-through key are **both unbound by default**:
> the former keeps altering your selections while active, the latter changes how
> interaction feels — both are easy to trigger by accident.
> Bind them in the config screen if you want them.

### Recorder

1. Make a selection first;
2. Press the **record key** (unbound by default) to start recording;
3. Run the machine once;
4. Press it again to stop recording.

- **List mode**
  - **Include**: only record changes of blocks in the list.
  - **Exclude**: skip changes of blocks in the list.
- **Apply as**
  - **Add**: merge the recorded positions and add them to the selection list.
  - **Clip out**: clip the existing selections with the recorded positions.

### Config

- **Mode / invert / transparency / interact through**
- **Block list**
- **Selection list** (format `minX,minY,minZ:maxX,maxY,maxZ`)
- **Operation key / selection key / config key**
- **Recorded blocks / list mode / apply as / record key**
- **Wand** item
- **Night vision**
- **Presets**: new / apply / overwrite / remove

> "Interact through" is a **single row**: the switch on the left, and a bindable
> toggle key on the right — so you change the value and the key in one place
> instead of hunting across two rows.

---

### Mode overview

| Mode                                  | Blocks that get faded                     |
| ------------------------------------- | ----------------------------------------- |
| `OFF` Off                             | None                                      |
| `REGION_INSIDE` In selection          | Every block inside the selection          |
| `REGION_INSIDE_LISTED` In selection · listed   | Inside the selection **and** listed   |
| `REGION_INSIDE_UNLISTED` In selection · unlisted | Inside the selection **and** unlisted |
| `REGION_OUTSIDE` Outside selection    | Every block outside the selection         |
| `REGION_OUTSIDE_LISTED` Outside selection · listed   | Outside the selection **and** listed   |
| `REGION_OUTSIDE_UNLISTED` Outside selection · unlisted | Outside the selection **and** unlisted |
| `BLACKLIST` Listed                    | Listed blocks in the whole world          |
| `WHITELIST` Unlisted                  | Unlisted blocks in the whole world        |

With **invert** enabled, the faded blocks become the **complement** of the table above.

### Block rule syntax

```
minecraft:stone                      a specific block
stone                                namespace omitted
#minecraft:planks                    block tag
*                                    wildcard (* / % / ? all work)
[waterlogged=false]                  block state
*[moving=true]                       special pseudo-state, matches moving blocks
```

## License

CC-BY-NC-4.0.

---

## Developer documentation (in Chinese)

- [`docs/architecture.md`](docs/architecture.md) — code structure, core checks, render-pipeline injection map, cross-version conventions
- [`docs/development.md`](docs/development.md) — building, multi-version layout, steps to add a new MC version, porting checklist
- [`docs/known-issues.md`](docs/known-issues.md) — remaining known limitations
