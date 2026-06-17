# NoteScope

[日本語](README.md) | **English**

A Paper plugin that shows a note block's configured pitch on the action bar just by **looking at it** —
so you can tell what note a block is set to without having to play it.

```text
♪ F#3 (ファ#)   調律 0/24   楽器: ハープ
```

- **Note name** (`F#3`) … scientific pitch notation. Note blocks span F#3–F#5 (two octaves)
- **Solfège** (`ファ#`) … Japanese do-re-mi name
- **調律 0/24** … tuning step: `0` right after placing (F#3), `+1` per right-click, wraps at `24` (F#5)
- **楽器: ハープ** … the instrument, determined by the block underneath

---

## Background & Purpose

Every right-click changes a note block's pitch by one step, but **you can't tell which note it's set to until you play it**.
Counting "how many more clicks do I need" while composing, or checking already-placed blocks by ear, is tedious.

NoteScope ray-traces the player's line of sight and shows the pitch on the action bar only while they're looking at a note block.
Because it's a server-side plugin (not a client mod), **players need to install nothing** — it works on a vanilla client.

---

## Requirements

| Item | Version |
| --- | --- |
| Server | Paper **26.1.2** (verified on build 69) |
| Java | **25** (verified on 25.0.x) |
| Build | JDK 25 + Maven (`brew install openjdk@25 maven`) |
| Dependencies | **None** |
| Client | **Vanilla is fine** (no mod required) |

> This single jar is all you need. No extra libraries or plugins (`paper-api` is `provided` — supplied by the server at runtime).

---

## Usage

1. Drop the jar into the server's `plugins/` and restart (→ [Deploying to a Server](#deploying-to-a-server)).
2. In-game, just **aim your crosshair at a note block** — the pitch appears on the action bar.
3. Turn it off with `/notescope off`, back on with `/notescope on`.

### Reading the display

| Field | Meaning |
| --- | --- |
| `F#3` | Scientific pitch (letter + sharp + octave). Note blocks span **F#3–F#5** |
| `(ファ#)` | Solfège (`ド レ ミ ファ ソ ラ シ` = do re mi fa sol la si) + sharp |
| `調律 0/24` | Tuning step. **0 = just placed (F#3)**, +1 per right-click, **24 = F#5** |
| `楽器: ハープ` | Instrument, set by the block underneath (dirt/air → harp, stone → bass drum, …) |

> Only sharps are shown (vanilla note blocks are sharp-based). For example, `F#3` is the same pitch as `G♭3`.

---

## Commands

| Command | Description | Who | Permission |
| --- | --- | --- | --- |
| `/notescope` | Toggle the display on/off | Players only | `notescope.use` |
| `/notescope on` | Turn the display on | Players only | `notescope.use` |
| `/notescope off` | Turn the display off | Players only | `notescope.use` |
| `/notescope status` | Show the current on/off state | Players only | `notescope.use` |

- The on/off state is **per player** (it does not affect others).
- Default is **on** for everyone. State is kept only while the server is running and **resets to on after a restart**.
- Tab completion on `/notescope` suggests `on` / `off` / `status`.

---

## Permissions

| Permission node | Default | Description |
| --- | --- | --- |
| `notescope.use` | `true` (everyone) | Allows receiving the display and using `/notescope` |

By default every player sees the display (no LuckPerms setup needed).
Only if you want to **disable** it for a specific player/group, set the node to `false` for them.

```bash
# e.g. disable the display for a group
lp group default permission set notescope.use false
# e.g. disable the display for a user
lp user <name> permission set notescope.use false
```

---

## How It Works (technical notes)

- **Line-of-sight check**: every 4 ticks (~0.2 s) it loops over online players and casts `rayTraceBlocks(6.0, NEVER)` from the eye. If the first block hit is a note block (`BlockData instanceof NoteBlock`), it shows the info.
- **Performance**: the cost is roughly `interval × online players × one ray trace`. The interval is 5×/second, each ray is a short voxel walk of at most 6 blocks (`FluidCollisionMode.NEVER` also skips fluid checks), and the 6 blocks around a player are always loaded so no extra chunk loading happens. The only thing that scales linearly is the player count; at a few dozen players it's negligible against the 50 ms/tick budget (for hundreds of players, lengthen the interval). The ray trace reads world state, so it runs on the main thread (`runTaskTimer`).
- **Reading the pitch**: from `NoteBlock#getNote()` (`org.bukkit.Note`) it reads `getTone()` (letter), `isSharped()` (sharp) and `getId()` (0–24 tuning step). The octave number rolls over at C, so it's computed from `getId()` as `3 + (id + 6) / 12`.
- **Reading the instrument**: `NoteBlock#getInstrument()` is mapped to a Japanese name (unknown instruments fall back to a prettified enum name).
- **Display**: Adventure's `Player#sendActionBar(Component)` is used. It never spams chat and fades on its own once you look away.
- **It never modifies blocks** (read-only).

> The line-of-sight task runs on Bukkit's global scheduler (`runTaskTimer`), so the target is **Paper** (not Folia).

---

## Build

JDK 25 and Maven are required (`brew install openjdk@25 maven` if missing).
Build with the bundled `deploy.sh` (**no Docker needed**):

```bash
./deploy.sh
```

Output: `target/NoteScope-1.0.0.jar`

`deploy.sh` runs `mvn clean package` with JDK 25 internally.
Override with another JDK via `JAVA_HOME=/path/to/jdk25 ./deploy.sh`, or build directly:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

---

## Deploying to a Server

Put the jar in the server's `plugins/` and restart. Two ways to get the jar:

### A. Use a release build (no build needed, recommended)

Download the latest `NoteScope-<version>.jar` from [Releases](https://github.com/astail/minecraft-onpu/releases). No JDK or Maven required.

```bash
gh release download --repo astail/minecraft-onpu --pattern '*.jar'
```

### B. Build it yourself

Follow [Build](#build) to produce `target/NoteScope-1.0.0.jar`.

### Placement

```bash
# bind mount (copy to the host-side plugins dir)
cp target/NoteScope-1.0.0.jar /path/to/data/plugins/
docker restart <container>

# named volume etc. (copy directly into the container)
docker cp target/NoteScope-1.0.0.jar <container>:/data/plugins/
docker restart <container>
```

If you see this in the startup log, it worked:

```text
[NoteScope] NoteScope を有効化しました。音符ブロックを見るとアクションバーに音階が表示されます。
```

---

## Project Layout

```text
.
├── pom.xml
├── deploy.sh
├── README.md
└── src/main/
    ├── java/io/github/astail/notescope/
    │   ├── NoteScopePlugin.java    # entry point (command registration, task startup)
    │   ├── NoteLookTask.java       # line-of-sight check → action bar
    │   ├── NoteFormatter.java      # Note/Instrument → display string
    │   └── NoteScopeCommand.java   # /notescope (on/off toggle)
    └── resources/plugin.yml
```

> The package name (`io.github.astail.notescope`), `NoteScope`, and the command name can all be renamed (change pom.xml, each `package`, and `plugin.yml` together).

---

## Notes

- **Nothing showing?** Check that your crosshair is actually on the note block (within 6 blocks), that `/notescope status` is on, and that you have the `notescope.use` permission.
- **Other display surfaces** (boss bar, persistent HUD, etc.) are limited for a server-side plugin. The action bar is ideal for "show only while looking, then fade."
- **Octave numbering** uses F#3–F#5 (matching the official Minecraft Wiki). Some references write it as F#4–F#6.
- The `paper-api` build number can track server updates (e.g. `26.1.2.build.70-stable`).
