# EntityProtection

A small Paper plugin for a family server: containers a player places are
automatically protected, so only the owner (and players they trust) can open,
use, or break them. Solves the "little brother empties the shared chests"
problem without a full land-claim plugin.

Out of scope: real entities (mobs, item frames, armor stands) and
region/land claims. Only blocks are protected.

## Features

- **Auto-protect on place.** Containers are protected the instant they are
  placed: chests, trapped chests, barrels, shulker boxes (any color, and the
  new 26.2 copper chest in every oxidation stage), furnaces/blast
  furnaces/smokers, dispensers, droppers, hoppers and brewing stands.
- **Doors, trapdoors and fence gates** (any wood type, iron, and the new 26.2
  copper variants) are protected too, but only if `protect-doors: true` in
  `config.yml` (default: `false`).
- **Owner-only access.** Opening, using or breaking a protected block requires
  being the owner, a player the owner trusted on that block, a player the
  owner globally trusts, or an admin. Denial messages name the owner.
- **Double chests** share one owner: placing a chest that would join an
  existing chest owned by someone else is blocked; the new half instead
  inherits the existing half's owner and trusted players so both halves behave
  identically. Breaking one half only removes that half's data.
- **Doors** are two blocks (top/bottom half) treated as a single protection:
  placing, breaking, trusting, untrusting or removing protection on either
  half applies to both.
- **Bypass prevention:**
  - Hoppers cannot drain a protected container into a destination its owner
    could not otherwise access; hopper minecarts can never drain one at all
    (see [Hopper rule](#hopper-rule) below for the exact behavior).
  - Explosions (creepers, TNT, beds, respawn anchors, ...) never destroy a
    protected block.
  - Pistons cannot push or pull a protected block.
  - Fire cannot burn down a protected block.
  - Breaking a protected block you own also removes its protection record
    (and the other half's, for a door).
- **Persistent storage**, survives a server restart:
  - Per-block protection (owner + per-block trusted players) is stored in the
    `PersistentDataContainer` of the chunk the block belongs to. This works
    even for blocks with no block entity of their own, such as doors.
  - Per-owner *global* trust (players an owner trusts on every one of their
    protections) is stored in `global-trust.yml` in the plugin's data folder.
- Players are identified by UUID everywhere (the server runs
  `online-mode: false`); name lookups only ever consult online players and
  Bukkit's local offline-player records, never the Mojang API.

### Hopper rule

Pulling items **out of** a protected container (via a hopper or a hopper
minecart) is only allowed when the destination is itself a protected block
whose owner already has access to the source — as the source's owner, a
player trusted on that specific source, or a player the source's owner
globally trusts. This is the exact same check ([`AccessPolicy`][access-policy])
used when a player opens the source directly, just applied to the
destination's owner instead. An unprotected destination is always denied,
which includes **every hopper minecart**: its inventory has no block
position, so it can never be protected in the first place, and can therefore
never drain a protected container.

Pushing items **into** a protected container from an unprotected source is
always left allowed, since it cannot leak anything from the protected side
and would otherwise break the common "feed a protected chest from an
unprotected hopper" setup.

[access-policy]: src/main/java/dev/nerzul/entityprotection/domain/policy/AccessPolicy.java

### Corrupt data

Protection data is text stored in each chunk's `PersistentDataContainer`. If
that text is ever corrupted (disk/region-file damage, a foreign plugin
writing over the same key, ...), a malformed line is **never erased**: it is
kept verbatim and re-written on every future save/remove in that chunk, and
the plugin fails closed instead of silently unprotecting anything:

- If the line's position (world/x/y/z) can still be read, only *that* block
  is affected: non-admins are denied there (as if it were owned by an unknown
  player) until it's fixed.
- If even the position can't be read, every protectable block in that chunk
  that has no valid record of its own is denied to non-admins, and the server
  log names the affected world and chunk coordinates.
- `entityprotection.admin` always bypasses this, exactly like any other
  protection.

**Recovery:** placing a fresh container/door at a position-specific corrupted
spot protects it again going forward (the old corrupt line becomes harmless
dead data, still preserved but no longer consulted). Recovering a chunk-wide
corruption, or clearing the leftover dead lines entirely, requires an
operator to edit the region file's chunk NBT offline (this plugin does not
ship an in-game repair command, to avoid an easy way to script away real data).

## Configuration (`config.yml`)

```yaml
protect-doors: false   # also auto-protect doors, trapdoors and fence gates

messages:
  # every player-facing message; supports & color codes and %owner%/%player%/%trusted%
  ...
```

See the shipped `config.yml` for the full list of message keys.

## Commands

All subcommands act on the block the player is looking at, up to 5 blocks
away (except `trustall`/`untrustall`, which manage the player's own global
trust list and need no target block).

| Command | Effect |
|---|---|
| `/protect info` | Shows the owner and trusted players of the looked-at block. |
| `/protect remove` | Removes protection from the looked-at block (owner/admin only). |
| `/protect trust <player>` | Lets `<player>` use/break the looked-at block (owner/admin only). |
| `/protect untrust <player>` | Revokes that per-block trust (owner/admin only). |
| `/protect trustall <player>` | Lets `<player>` use/break *every* block you own, now and in the future. |
| `/protect untrustall <player>` | Revokes that global trust. |

`<player>` must be online, or have played on this server before.

## Permissions

| Permission | Default | Effect |
|---|---|---|
| `entityprotection.use` | `true` | Use `/protect` and own protections on blocks you place. |
| `entityprotection.admin` | `op` | Bypass every protection: open/use/break any block, and manage trust/removal on any block regardless of owner. |

## Building

Requires Java 25 and Gradle (a wrapper is checked in). `build.gradle.kts`
declares a Java 25 toolchain for *compiling and running* the project, but
Gradle itself still needs a JVM to launch the build in the first place; it
auto-detects one (via `JAVA_HOME`, `PATH`, or its own toolchain
auto-provisioning) on most machines without any extra setup.

```bash
./gradlew build
```

The plugin jar is produced at `build/libs/entity-protection-1.0.0.jar`.

Run just the tests with `./gradlew test`.

If Gradle can't find a suitable JVM on your machine (for example: `Unable to
locate a Java Runtime` or a toolchain resolution error), point it at one —
without hardcoding a path in this repo, so the project stays portable across
machines and CI:

- Per shell session: `export JAVA_HOME=/path/to/a/jdk` before running `./gradlew`.
- Per user, for every project: add `org.gradle.java.home=/path/to/a/jdk` to
  `~/.gradle/gradle.properties` (outside this repo, so it never gets
  committed).

## Deploying

1. Build the jar (above).
2. Copy it into the server's plugin folder:
   ```bash
   cp build/libs/entity-protection-1.0.0.jar <server>/data/plugins/
   ```
3. Restart the server so Paper loads the new/updated jar:
   ```bash
   docker compose restart mc
   ```
   Do **not** use `/reload` — it does not safely reload plugin classes and can
   corrupt plugin state.

## Manual in-game test checklist

Use two accounts (or `entityprotection.admin` on one and a fresh alt for the
other) to verify the acceptance criteria:

- [ ] Player A places a chest. Player B (no trust, no admin) cannot open it;
      the denial message names player A.
- [ ] Player B cannot break player A's chest either.
- [ ] Player A runs `/protect trust B` on that chest. Player B can now open
      and use it.
- [ ] Player A runs `/protect untrust B`. Player B is denied again.
- [ ] Player A runs `/protect trustall B`, then places a *different* chest.
      Player B can open that new chest too, without being trusted on it
      individually.
- [ ] Player A places a second chest against the first, forming a double
      chest. Player B (still globally trusted) can open either half.
- [ ] Player A breaks one half of the double chest; the other half remains
      protected and usable by A.
- [ ] Player C (a third, untrusted player) tries to place a chest against a
      chest owned by player A: placement is blocked, no double chest forms.
- [ ] A hopper placed by player B (untrusted) under player A's chest does not
      drain it. A hopper placed by player A under their own chest, or by
      trusted player B under *their own* protected hopper, does drain it. A
      hopper minecart under the chest never drains it either.
- [ ] TNT/creeper explosion next to the chest leaves the chest standing.
- [ ] A piston cannot push or retract the chest.
- [ ] Fire spreading nearby does not burn the chest down (needs a flammable
      block adjacent, e.g. wood, to actually catch fire near it).
- [ ] With `protect-doors: true`, player A places a door; player B cannot open
      it until trusted; breaking/interacting with either half behaves as one
      unit.
- [ ] Stop the server (or `docker compose restart mc`) and start it again:
      all of the above protections are still in place.
- [ ] Player A (or admin) runs `/protect remove` on their chest; it is now
      open to anyone, matching an unprotected block.
- [ ] An admin (`entityprotection.admin`) can open, break, trust and remove
      protection on a block they do not own.
