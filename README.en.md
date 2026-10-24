# Selective Rendering

English | [简体中文](README.md)

A Minecraft Fabric client mod: makes specific blocks / entities translucent or hidden, based on "region + list".

## Usage

Hold the configured item (a breeze rod by default) to enable the actions below.

| Action                | Effect                                  |
| --------------------- | --------------------------------------- |
| Left click            | Set corner 1                            |
| Right click           | Set corner 2                            |
| Shift + left click    | Clear corners                           |
| Middle click          | Select a corner or a selection          |
| Selection key + left  | Add / remove the aimed area             |
| Selection key + right | Clip existing selections with the current area |
| Operation key + left  | Add / remove blocks and entities        |
| Operation key + right | Blocks with states / entities by item ID |
| Ctrl + scroll         | Cycle modes                             |
| Alt + scroll          | Move the currently selected target      |
| Ctrl + Z / Y          | Undo / redo selection changes           |
| X + V                 | Open the config screen                  |

### Modes

| Rule                |
| ------------------- |
| Off                 |
| Inside selection    |
| Inside & listed     |
| Inside & unlisted   |
| Outside selection   |
| Outside & listed    |
| Outside & unlisted  |
| Listed              |
| Unlisted            |

### Config

| Global              | Description                             |
| ------------------- | --------------------------------------- |
| Render mode         | Fade blocks inside the rule             |
| Invert mode         | Fade blocks outside the rule            |
| Transparency        | Transparency of faded blocks            |
| Block list          | Global block list to fade               |
| Edit list           | Key for adding / removing blocks        |
| Edit selection      | Key for adding / removing selections    |
| Undo selection      | Undo the last selection change          |
| Redo selection      | Redo the last selection undo            |
| Open config         | Key to open the config screen           |
| Wand item           | Item that enables the actions           |
| Full bright         | Maximize block brightness               |
| Pass through blocks | Crosshair ignores faded blocks          |
| Pass through entities | Crosshair ignores faded entities      |
| Recorded blocks     | Areas where blocks changed are recorded |
| List mode           | Only record / don't record listed blocks |
| Apply mode          | Add / clip areas to selections          |
| Toggle recording    | Key to start / stop recording           |
| Selections          | Edit or remove selections               |
| Presets             | Save the config as a preset             |

| Selection    | Description                              |
| ------------ | ---------------------------------------- |
| Blocks       | Extra block list to fade in this selection |
| Entities     | Entities listed by this selection        |
| Particles    | Particles listed by this selection       |
| Transparency | Per-selection transparency override      |
| Projection   | Render the selection's blocks elsewhere  |
| Merge        | Merge areas into another selection       |
| Detach       | Split a sub-area into its own selection  |
| Remove       | Remove a sub-area                        |
| Corners      | Edit a sub-area's corners                |

## License

[CC-BY-NC-4.0](LICENSE)

Ported from the selective rendering module of [Lucidity](https://github.com/hotpad100c/lucidity2.0)
(by [Ryan100c](https://github.com/hotpad100c)), rewritten for the new 26.1–26.3 render architecture.
