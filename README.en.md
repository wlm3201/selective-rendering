# Selective Rendering

English | [简体中文](README.md)

A Fabric client mod that makes any block translucent, or hides it, based on a *region plus a block
list*.

Ported from the selective rendering feature of [Lucidity](https://github.com/hotpad100c/lucidity2.0)
(original author ryan100c, licensed CC-BY-NC-4.0), rewritten for the new 26.1 – 26.3 rendering
architecture and made compatible with Sodium / Indigo.

---

## Features

- **Fade blocks**: modify vertex alpha and move the block into the translucent render layer,
  keeping geometry and lighting.
- **Translucency**: 0% = opaque, 100% = fully hidden.
- **Modes**: inside/outside the selection × inside/outside the list — 9 modes in total.
- **Invert**: fade everything *except* the listed blocks.
- **Pass-through interaction**: the crosshair ignores faded blocks, so you can reach blocks and
  entities behind them.
- **Selections**: operated like a schematic-projection mod.
- **Change recorder**: records regions where blocks or block states changed.
- **Presets**: save the current mode / translucency / blocks / selection and switch between them.
- **Night vision**: full-bright blocks, skipping lighting calculations.

---

## Usage

The default wand is a **breeze rod** — hold it to enable the operations.

### Mouse

| Action | Effect |
| --- | --- |
| Left click | Set corner 1 (red box) |
| Right click | Set corner 2 (blue box) |
| Selection key + left click | Add a selection, or remove an existing one |
| Selection key + right click | Clip all stored selections with the current one |
| Block key + left click | Add or remove a block-ID rule |
| Block key + right click | Add or remove a rule for a block with state |
| Left Ctrl + scroll | Cycle modes |
| Left Alt + scroll | Move the currently selected corner |

### Keybinds

| Key | Default | Action |
| --- | --- | --- |
| Open config | `X + V` | Open the config screen |
| Record | Unbound | Start / stop recording block changes |
| Pass-through | Unbound | Toggle pass-through interaction |

### Recorder

1. Box out a selection first.
2. Press the **record key** (unbound by default) to start recording.
3. Run the machine once.
4. Press the record key again to stop.

- **List mode**
  - **Whitelist**: only record changes to blocks on the list.
  - **Blacklist**: exclude changes to blocks on the list.
- **Apply mode**
  - **Add**: merge the recorded positions into the selection list.
  - **Clip**: clip the existing selections with the recorded positions.

### Configuration

- **Mode / invert / translucency / pass-through**
- **Block list**
- **Selection list** (format `minX,minY,minZ:maxX,maxY,maxZ`)
- **Block key / selection key / config key**
- **Record blocks / list mode / apply mode / record key**
- **Wand** item
- **Night vision**
- **Presets**: create / switch / overwrite / delete

---

### Modes at a glance

| Mode | Blocks faded |
| --- | --- |
| `OFF` | none |
| `REGION_INSIDE` | everything inside the selection |
| `REGION_INSIDE_LISTED` | inside the selection **and** on the list |
| `REGION_INSIDE_UNLISTED` | inside the selection **and** off the list |
| `REGION_OUTSIDE` | everything outside the selection |
| `REGION_OUTSIDE_LISTED` | outside the selection **and** on the list |
| `REGION_OUTSIDE_UNLISTED` | outside the selection **and** off the list |
| `BLACKLIST` | blocks on the list, anywhere in the world |
| `WHITELIST` | blocks off the list, anywhere in the world |

With **invert** on, the faded set becomes the complement of the row above.

### Block rule syntax

```
minecraft:stone                      a specific block
stone                                namespace omitted
#minecraft:planks                    a block tag
*                                    wildcard (* / % / ? all work)
[waterlogged=false]                  block state
*[moving=true]                       special pseudo-state, matches moving blocks
```
