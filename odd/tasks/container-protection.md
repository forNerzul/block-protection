# Feature: container-protection

## Objective
Paper plugin that automatically protects containers a player places, so only the owner
(and players they trust) can open, use, or break them.

## Problem / why
Family server: a child player takes items from chests filled by the adults.

## Scope
- Auto-protect on place. Default protected set: chests, trapped chests, barrels, shulker boxes,
  furnaces (all variants), dispensers, droppers, hoppers.
- Doors, trapdoors, fence gates: OFF by default, enabled via `config.yml`.
- Owner-only access (open, use, break); denial message names the owner.
- Double chests: block joining with a foreign chest; both halves share one owner.
- Bypass prevention: hoppers / hopper minecarts pulling from protected blocks, explosions,
  pistons, fire/burn.
- Commands: `/protect trust|untrust|info|remove <player>`, `/protect trustall|untrustall <player>`.
- Permissions: `entityprotection.use` (default true), `entityprotection.admin` (bypass, default op).
- Persistent storage; players identified by UUID.

## Out of scope
Real entities (mobs, item frames, armor stands), region/land claims.

## Constraints
- Paper `26.2.build.129-stable`, `api-version: '26.2'`, Java 25, Gradle Kotlin DSL
  (source: https://github.com/forNerzul/sv-01/blob/main/docs/plugin-development.md).
- `online-mode: false` → offline UUIDs; renaming a player changes their UUID.
- Domain logic independent from Paper (hexagonal): domain + ports, Paper adapters.
- Artifacts (code, comments, messages, docs) in English.

## TDD
- Mode: strict, ON. Source: user global config (`Strict TDD Mode: enabled`).
- Runner: `gradle test` (JUnit 5).

## Delivery
- Branch `feat/container-protection`. No git remote → no PRs; work-unit commits only.
- Forecast: ~900 authored lines (exceeds ~400 heuristic; chain strategy moot without a remote).

## Tasks
- [x] T1 Scaffold Gradle project, `plugin.yml`, default `config.yml`, `.gitignore`, empty plugin main. Route: delegated (writer trigger, 2+ files).
- [x] T2 Domain: `Protection` (owner, trusted), access policy, global trust, protectable-type config — with unit tests. Route: delegated.
- [x] T3 Persistence adapters: chunk PDC protection repository, YAML global-trust repository. Route: delegated.
- [x] T4 Listeners: place, interact, break, double chest, hoppers, explosions, pistons, burn. Route: delegated.
- [x] T5 Commands + permissions + messages. Route: delegated.
- [x] T6 README with build/install/test instructions; final `gradle build`. Route: delegated.
- [x] T7 fix(persistence): robustness (R3-corrupt-data-fail-open, R4-fail-open-on-corrupt-data, R4-global-trust-file-wipe, R4-hopper-hot-path-decode). Codec resilience to malformed/truncated lines and separator-bearing world names; fail-closed on unexpected lookup errors; atomic YAML save with backup-and-refuse on corruption and rollback on failed save; per-chunk decode cache invalidated on write/unload. Route: delegated (writer trigger).
- [x] T8 fix(listeners): destruction bypasses (R1-door-support-break, R1-wither-block-destruction). Deny breaking a block that supports an inaccessible protected door; clean up orphaned door protection data when physics destroys a door; cancel `EntityChangeBlockEvent` against a protected block. Route: delegated.
- [x] T9 fix(commands): double-chest half divergence (R3-double-chest-half-divergence). Mirror `/protect trust|untrust|remove` onto the other half of a double chest (same material + same owner guard as doors); re-verify the T7(prior)/R1 chest-join-fallback path against the current code. Route: delegated.
- [x] T10 fix(listeners): hopper rule (R3-hopper-rule-blocks-protected-sources, R2-minecart-doc-misleading). Pulling from a protected source is allowed only when the destination's owner already has access to the source, via the domain `AccessPolicy`, in a pure/testable decision; add `BREWING_STAND` to default containers if real in 26.2; fix misleading Javadoc/README about hopper minecarts. Route: delegated.
- [x] T11 chore(build): drop the hardcoded `org.gradle.java.home` (R2-gradle-java-home, R3-hardcoded-java-home). Rely on the Java 25 toolchain; document `JAVA_HOME` in the README instead. Route: delegated.

## Acceptance criteria
- A non-owner cannot open, break, or hopper-drain a protected chest.
- After `/protect trust <player>`, that player can open it.
- Protection survives a server restart.
- A player with `entityprotection.admin` can access everything.

## Checks
- `gradle test`, `gradle build` per task.
- Manual in-game verification (user) after deploy.

## Progress

All 6 tasks implemented on `feat/container-protection` (repo had no prior
commits; no remote, so work-unit commits only, no PR).

- **T1** `8a95495 chore: scaffold paper plugin project`. Gradle Kotlin DSL,
  Java 25 toolchain, `paper-api:26.2.build.129-stable` (compileOnly) from
  `https://repo.papermc.io/repository/maven-public/`, `api-version: '26.2'`,
  `plugin.yml`, default `config.yml` (`protect-doors: false` + messages),
  `.gitignore` (`.atl/`, `build/`, `.gradle/`, keeps `gradle-wrapper.jar`),
  empty `EntityProtectionPlugin`. Wrapper generated with `gradle wrapper
  --gradle-version 9.7.1`. Checks: `./gradlew build` → BUILD SUCCESSFUL.
- **T2** `4e35374 feat(domain): ...`. Pure domain, no Paper imports:
  `BlockPosition`, `Protection` (owner + per-block trust), `AccessPolicy`
  (owner/trusted/globally-trusted-by-owner/admin bypass), `ProtectableCategory`
  + `AutoProtectPolicy` (protect-doors opt-in for DOOR/TRAPDOOR/FENCE_GATE,
  CONTAINER always true), `ProtectableTypePolicy` port, `ProtectionRepository`
  + `GlobalTrustRepository` ports, `ProtectionService` (owns the "only owner or
  admin may modify" rule). TDD: wrote 5 test files first, ran `./gradlew test`
  → RED (compile errors, classes did not exist), implemented, ran again → GREEN
  (33 tests). Route: delegated (writer trigger, wrote domain + tests together).
- **T3** `4dd502c feat(persistence): ...`. `ChunkProtectionCodec` (pure string
  (de)serialization, no Bukkit imports) + `ChunkProtectionRepository`
  (`ProtectionRepository` backed by each chunk's `PersistentDataContainer`,
  one `STRING` entry holding every protection in that chunk — works for
  non-TileState blocks like doors, per the environment note) and
  `YamlGlobalTrustRepository` (`YamlConfiguration` file in the plugin data
  folder). TDD: wrote codec + YAML repo tests first → RED (missing classes,
  then `NoClassDefFoundError` because `paper-api` was `compileOnly`-only) →
  added `testImplementation("io.papermc.paper:paper-api:...")` so
  `YamlConfiguration`/`PersistentDataType` are on the test runtime classpath →
  GREEN (9 new tests, 42 total, via `./gradlew test`). `ChunkProtectionRepository`
  itself needs a live `Chunk`/`World`, so per the "Paper adapters without a
  test harness" rule it is verified with `./gradlew build` only (compiles,
  jar assembles).
- **T4** `a3a0517 feat(listeners): ...`. Before writing this task, verified the
  real Material/Tag surface of the resolved 26.2 jar with `javap`/`unzip -l`
  (see Deviations/decisions below) instead of guessing. Added
  `PaperProtectableTypePolicy` (classifies via `Tag.SHULKER_BOXES` /
  `Tag.COPPER_CHESTS` / `Tag.DOORS` / `Tag.TRAPDOORS` / `Tag.FENCE_GATES` plus
  a small hardcoded list for chest/barrel/furnace-family/dispenser/dropper/
  hopper), `BlockPositions`/`PlayerNames` utils, and 7 listeners:
  `ProtectionPlaceListener` (auto-protect + double-chest join guard/mirroring +
  door pairing), `ProtectionAccessListener` (open/use), `ProtectionBreakListener`
  (break + cleanup), `ProtectionHopperListener` (bypass rule, see Decisions),
  `ProtectionExplosionListener`, `ProtectionPistonListener`,
  `ProtectionBurnListener`. Wired in `EntityProtectionPlugin#onEnable`. No new
  unit tests (Paper adapters need a live server); checks: `./gradlew build` →
  BUILD SUCCESSFUL, all 42 existing tests still green.
- **T5** `e340b87 feat(commands): ...` + `cba69d8 fix(commands): ...`.
  `ProtectCommand` (`info`/`remove`/`trust`/`untrust`/`trustall`/`untrustall`,
  `getTargetBlockExact(5)`, owner/admin-only modification, door-pair mirroring)
  and `ProtectTabCompleter`, wired via `getCommand("protect")`. Follow-up fix
  wired the previously-dead `not-protectable` message. Checks: `./gradlew
  build` → BUILD SUCCESSFUL, 42 tests green.
- **T6** `bedd98c docs: ...`. README (English): features, `protect-doors`
  config, commands/permissions table, hopper-rule decision, build (`./gradlew
  build`, jar at `build/libs/entity-protection-1.0.0.jar`), deploy (copy jar to
  `data/plugins/`, `docker compose restart mc`, never `/reload`), manual
  in-game checklist matching every acceptance criterion. No deploy/remote
  operations performed.

### Final verification (this session, `./gradlew clean build`)

- `./gradlew test` → **BUILD SUCCESSFUL**, 42 tests, 0 failures (7 suites:
  `BlockPositionTest` 5, `ProtectionTest` 7, `AccessPolicyTest` 6,
  `AutoProtectPolicyTest` 3, `ProtectionServiceTest` 12,
  `ChunkProtectionCodecTest` 4, `YamlGlobalTrustRepositoryTest` 5).
- `./gradlew build` → **BUILD SUCCESSFUL**; jar at
  `build/libs/entity-protection-1.0.0.jar`; `plugin.yml` inside it shows
  `version: '1.0.0'` (templating confirmed).
- `git log --oneline` → 7 commits, `8a95495`..`bedd98c` (listed above), all on
  `feat/container-protection`, no remote/PR.
- Manual in-game verification: **not performed** (requires a running server;
  explicitly the user's job per the Checks section — see the README checklist).

### Deviations / decisions made without stopping to ask

- **Hopper bypass rule is one-directional, not symmetric**: pulling FROM a
  protected container requires the destination (hopper/hopper minecart) to be
  protected by the same owner; pushing INTO a protected container is always
  allowed. This matches the task prompt's explicit "may stay allowed" for the
  push direction, so it was implemented rather than asked about. Documented in
  README under "Hopper rule".
- **Trust grants break, not just open/use**: `AccessPolicy`/`ProtectionService.canAccess`
  is used uniformly for open, use and break. Read literally, "Owner-only access
  (open, use, break)" in the spec could mean trust never extends to breaking,
  but the acceptance criteria only test "open" after trust, and every
  comparable protection plugin treats per-block trust as full access including
  break; the alternative (owner can break, trusted cannot) has no command to
  express it and would contradict "both halves share one owner" symmetry.
  Treated as the natural reading rather than a blocking product question.
- **Command modification also applies to the door's other half**: `/protect
  trust|untrust|remove` on one half of a door mirrors the same operation onto
  the other half, so both stay "one protection" for commands too, not only for
  placement/breaking.
- **Material defaults built from the real 26.2 jar, not guessed**: verified via
  `javap`/`unzip -l` on the resolved `paper-api-26.2.build.129-stable.jar`
  before writing `PaperProtectableTypePolicy`. Confirmed 26.2 additions:
  `SMOKER` (not in the task's explicit furnace list, added since "furnaces
  (all variants)" implies it), `COPPER_CHEST` + oxidation/waxed stages (covered
  by `Tag.COPPER_CHESTS`), `COPPER_DOOR`/`COPPER_TRAPDOOR` + stages (covered by
  `Tag.DOORS`/`Tag.TRAPDOORS`). Deliberately excluded `ENDER_CHEST` from the
  container list (per-player inventory, not a block to own) and all
  `LEGACY_*` materials (pre-flattening, `Tag`s already exclude them).
- **paper-api added as `testImplementation`** in addition to `compileOnly`
  (T3): needed so `YamlConfiguration` (a real, concrete class shipped in the
  API jar, not just an interface) is present on the test runtime classpath;
  `compileOnly` alone is compile-time only for tests too, per Gradle's `java`
  plugin defaults.
- Nothing required stopping to ask the user; no blocked/partial items.

### Correction: native review findings R1, R3, R4

Native review found 3 candidate-caused CRITICAL findings after T1-T6; applied
one bounded correction (~119 authored changed lines):

- **R1 (double-chest join bypass)**: `findJoinedNeighbor` no longer gates on
  the neighbor's own `Chest.Type` (unreliable: can still read `SINGLE` at
  `BlockPlaceEvent` time), only on the two directions perpendicular to our own
  facing. Admin bypass now mirrors instead of blindly assigning ownership.
  `ProtectionAccessListener` now requires access on BOTH halves of a
  `DoubleChest` (via `Container`/`DoubleChest.getLeftSide/RightSide`).
- **R3 (trapdoor treated as bisected door)**: `DoorHalves.otherHalfOrNull` now
  matches only `org.bukkit.block.data.type.Door`, not any `Bisected` (which
  also matches single-block `TrapDoor`/`Stairs`).
- **R4 (neighbor wipe)**: `ProtectionBreakListener` and `ProtectCommand#handleRemove`
  now only force-remove the "other half" if it has the same material and its
  protection `sharesOwnerWith` the primary block's protection (new pure
  `Protection.sharesOwnerWith`, TDD: RED — `ProtectionTest` referenced the
  missing method, compile failure — then GREEN, 43 tests total).

Checks: `./gradlew test` → BUILD SUCCESSFUL, 43 tests, 0 failures.
`./gradlew build` → BUILD SUCCESSFUL. `git show --stat HEAD` on the correction
commit: 7 files changed, 89 insertions(+), 30 deletions(-).

### T7 fix(persistence): robustness

Non-blocking WARNING advisories from the same native review, authorized by the
user. Route: delegated (writer trigger, 15 files).

- **Codec resilience**: `ChunkProtectionCodec` now length-prefixes the world
  name (`<charCount>:<world>|x|y|...`) so it round-trips even if the world
  name contains the field separator, and decodes each line independently
  inside a try/catch, logging and skipping a malformed/truncated line instead
  of losing every protection in the chunk. Not backward compatible with the
  old unprefixed format — documented in the class Javadoc why that's fine:
  this plugin has never shipped a release, so no server has ever persisted
  data in the old format. TDD: added 3 tests (malformed line skipped without
  losing the others, truncated world-length prefix skipped, world name
  containing `|` round-trips) → RED (`ArrayIndexOutOfBoundsException`/
  `NumberFormatException`/assertion failure) → GREEN.
- **Fail closed**: `ProtectionService.canAccess`/`isProtected` now catch any
  `RuntimeException` from the repositories and return the safe default for
  their own semantics — `false` (deny) for `canAccess`, `true` (assume
  protected) for `isProtected`, since the same "empty on error" answer would
  be fail-open for one caller and fail-closed for the other. The remaining
  direct `find()` call sites that gate a security decision
  (`ProtectionBreakListener`, `ProtectionHopperListener`) now wrap that call
  and cancel the event on error instead of letting the exception silently
  leave the event un-cancelled (allowed). `ProtectCommand#onCommand` now
  wraps its whole dispatch and reports a new `storage-error` message instead
  of crashing silently.
- **YamlGlobalTrustRepository**: invalid UUID entries are skipped with a
  warning (`parseUuids`); a file that fails to parse is detected ourselves
  (read + `loadFromString`, not the lenient `loadConfiguration(File)` which
  swallows parse errors) and is never overwritten — a timestamped `.corrupt-*`
  backup is written next to it and every `trust`/`untrust` call returns
  `false` until it's fixed; saves are atomic (temp file in the same
  directory + `ATOMIC_MOVE`); a failed save rolls the in-memory change back
  to its previous value. `GlobalTrustRepository.trust/untrust` now return
  `boolean`; `ModificationResult` gained `STORAGE_ERROR`, wired through
  `ProtectCommand`'s `trustAll`/`untrustAll` and the shared `respond()`. TDD:
  added 4 tests (invalid UUID skipped, corrupt file backed up and never
  overwritten, failed save rolls back and reports failure, trust returns
  true) → RED (compile errors: `trust`/`untrust` still returned `void`) →
  GREEN.
- **Hot path**: `ChunkProtectionRepository` now caches each chunk's decoded
  `Map<BlockPosition, Protection>` keyed by `(world, chunkX, chunkZ)`; a write
  replaces the cache entry (no re-decode), a `ChunkUnloadEvent` (the
  repository self-registers as a `Listener`) evicts it, and a chunk with no
  `protections` PDC key at all is never decoded (an empty map is used
  directly). No dedicated test: needs a live `Chunk`/`PersistentDataContainer`,
  same "Paper adapter without a test harness" rule as T3/T4; verified with
  `./gradlew build`.

Checks: `./gradlew test` → BUILD SUCCESSFUL, 50 tests, 0 failures (7 new: 3 in
`ChunkProtectionCodecTest`, 4 in `YamlGlobalTrustRepositoryTest`).
`./gradlew build` → BUILD SUCCESSFUL.

### T8 fix(listeners): destruction bypasses

- **R1-door-support-break**: `ProtectionBreakListener` now also checks the
  block directly above the one being broken; if it's a door's BOTTOM half
  (`org.bukkit.block.data.type.Door`, `Bisected.Half.BOTTOM`) and the breaker
  cannot access it, the break is cancelled — otherwise breaking the block a
  protected door rests on would pop the door off for free, bypassing its own
  protection entirely. Reuses `ProtectionService.canAccess` (already
  fail-closed from T7), so no new exception handling was needed here.
- **Orphan cleanup**: new `ProtectionPhysicsCleanupListener` listens for
  Paper's `com.destroystokyo.paper.event.block.BlockDestroyEvent` (found via
  `javap` on the resolved 26.2 jar — it is the event Paper fires for a block
  destroyed by physics/natural causes rather than a player) and, when the
  destroyed block is a door, force-removes both halves' protection data. It
  does not cancel: there is no player to check access against, so fighting
  physics here would be meaningless; the goal is only to stop stale data
  from lingering once the physical door is already gone.
- **R1-wither-block-destruction**: `ProtectionExplosionListener` gained an
  `EntityChangeBlockEvent` handler that cancels the event outright when
  `event.getBlock()` is protected — covers a wither/ravager breaking blocks,
  an enderman picking one up, a zombie breaking a door in hard mode, etc.
  (verified the event exists and carries `getBlock()`/`isCancelled` via
  `javap`; class Javadoc updated to describe both explosion and direct
  entity destruction).

No new unit tests: all three touch points need a live `Block`/`Player`/event
instance (Door blockdata, `BlockDestroyEvent`, `EntityChangeBlockEvent`), so
per the established "Paper adapters without a test harness" rule they are
verified with `./gradlew build` only; the 50 existing tests stayed green.

Checks: `./gradlew test` → BUILD SUCCESSFUL, 50 tests, 0 failures.
`./gradlew build` → BUILD SUCCESSFUL.

### T9 fix(commands): double-chest half divergence

- **R3-double-chest-half-divergence**: new `DoubleChestHalves.otherHalfOrNull`
  (extracted from `ProtectionAccessListener`'s existing double-chest logic,
  which now reuses it too instead of duplicating it) resolves a chest's
  double-chest partner via `Container`/`DoubleChest.getLeftSide/RightSide`.
  `ProtectCommand` gained a unified `otherHalfOrNull(Block)` (door half, else
  double-chest half) used by `handleTrust`/`handleUntrust` (mirrors through
  the normal, permission-checked `protectionService.trust/untrust` — safe by
  construction, same as the existing door path) and `handleRemove` (mirrors
  through `forceRemoveProtection`, guarded by the same "same material + same
  owner" check already used for doors, via `Protection.sharesOwnerWith`).
  Before this, running `/protect trust` on only one half of a double chest
  left the other half's trust list diverged, even though both halves share
  one combined inventory.
- **R1-chest-join-fallback-fail-open re-check (against `d85fa43` and later)**:
  confirmed the bug. `findJoinedNeighbor` scanned the two directions
  perpendicular to facing and used whichever one happened to hold a matching
  chest — not deterministic, and its "no match found" branch still fell back
  to *protecting the block as an unverified single chest and allowing the
  placement*, i.e. fail-open exactly like the original R1. Fixed by making
  the partner position exact: vanilla connects a chest to the neighbor
  reached by rotating its own facing clockwise for a RIGHT half or
  counter-clockwise for a LEFT half (clockwise cycle NORTH→EAST→SOUTH→WEST→
  NORTH) — verified against an independent, citable description of Java
  Edition's double-chest left/right layout (Minecraft Wiki-sourced summary;
  paper-api itself exposes no rotation helper on `BlockFace`, confirmed via
  `javap`) rather than assumed from memory. `findJoinedNeighbor` now computes
  that one direction directly; the "no match" branch now cancels the
  placement and logs a severe warning instead of silently allowing it, since
  `Chest.Type` reporting a join with no matching partner should not happen
  and must fail closed.

No new unit tests: both changes are Block/Chest-blockdata-shaped Paper logic
needing a live server; verified with `./gradlew build` (50 existing tests
stayed green).

Checks: `./gradlew test` → BUILD SUCCESSFUL, 50 tests, 0 failures.
`./gradlew build` → BUILD SUCCESSFUL.

### T10 fix(listeners): hopper rule

- **R3-hopper-rule-blocks-protected-sources**: the old "same owner exactly"
  rule was too strict (a trusted player's own hopper still couldn't drain a
  container they were legitimately trusted on). New pure
  `domain.policy.HopperTransferPolicy.isTransferAllowed(source, destination,
  globallyTrustedBySourceOwner)`: unprotected source → always allowed;
  protected source + unprotected destination → denied; protected source +
  protected destination → allowed exactly when the destination's *owner*
  would themselves pass `AccessPolicy.canAccess` against the source (owner,
  per-block trusted, or globally trusted by the source's owner). TDD: 6 tests
  written first → RED (`HopperTransferPolicy` didn't exist) → GREEN.
  `ProtectionService.canTransfer(source, destination)` composes the two
  repository lookups (fails closed to `false` on an unexpected error, same
  `safely()` wrapper as T7) and calls the policy; `ProtectionHopperListener`
  shrank to resolving both positions and calling it.
- **BREWING_STAND**: confirmed via `javap` on the 26.2 jar that
  `Material.BREWING_STAND` is real (non-legacy) and its block state
  (`org.bukkit.block.BrewingStand`) implements `Container`; added to
  `PaperProtectableTypePolicy`'s hardcoded container set so an owner's own
  hopper can feed it.
- **R2-minecart-doc-misleading**: `BlockPositions.ofLocationOrNull` and
  `ProtectionHopperListener`'s Javadoc now say plainly that a hopper minecart
  has no block position and therefore can never be protected, so it can never
  drain a protected container — not "must be protected by the same owner"
  (the old, now-inaccurate wording). README's "Hopper rule" section and its
  manual-test-checklist line rewritten to match the real behavior, and the
  container feature list now mentions brewing stands.

Checks: `./gradlew test` → BUILD SUCCESSFUL, 56 tests, 0 failures (6 new:
`HopperTransferPolicyTest`). `./gradlew build` → BUILD SUCCESSFUL.

### T11 chore(build): drop the hardcoded `org.gradle.java.home`

- **R2-gradle-java-home / R3-hardcoded-java-home**: deleted `gradle.properties`
  (its only content was `org.gradle.java.home=/Library/Java/VirtualMachines/
  temurin-25.jdk/...`, a path specific to this machine, which would break the
  build for any other contributor or CI runner). `build.gradle.kts`'s
  `java.toolchain.languageVersion = 25` already handles compiling/running the
  project; Gradle itself still needs some JVM to launch, but auto-detects one
  via `JAVA_HOME`/`PATH`/its own toolchain provisioning without that file.
  Verified locally: stopped the running Gradle daemons (`./gradlew --stop`,
  so the next run couldn't reuse one started under the old pinned path),
  then `./gradlew clean build` → BUILD SUCCESSFUL, and `./gradlew -v`
  confirms it fell back to "current Java home" (still Temurin 25 on this
  machine, resolved from `PATH`/`java_home`, not from a committed setting).
  README's "Building" section now documents the two supported ways to point
  Gradle at a JDK without hardcoding a path in the repo: `export JAVA_HOME=...`
  per shell session, or `~/.gradle/gradle.properties` (user-level, outside
  the repo — not edited by this change, only documented as an option).

Checks: `./gradlew clean build` → BUILD SUCCESSFUL, 56 tests, 0 failures
(same as T10 — no code changed, only `gradle.properties` removed and the
README updated).

### Correction: R3-chest-partner-direction-inverted (post-T9)
Fixed T9's inverted LEFT/RIGHT↔clockwise chest-partner mapping (was backwards
vs. vanilla `ChestBlock#getConnectedDirection`) in new, TDD-tested
`paper.util.ChestJoinDirection` (9 cases, red→green); 65 tests, build green.

## Next step
All authorized work (T7-T11) is implemented. Feature complete pending the
user's manual in-game verification (README checklist) after deploying the
built jar.

### Review outcome (T7–T11 + a934516)

- Review lineage `review-44cd79cb66f96489` (base `d85fa43`) found R3-chest-partner-direction-inverted; fixed in `a934516`. The correction exceeded its declared budget (100 vs 80 lines), which produced `scope_changed`. With user authorization, the lineage was abandoned (`operator_disposition`).
- A fresh review of `d85fa43..a934516` (reliability lens) came back **approved**; the acknowledgement was made and the authority burned. The reviewed boundary is now `a934516`.
- Open advisories (WARNING, non-blocking): R3-codec-skip-erases-and-fails-open, R3-physics-cleanup-before-final-outcome, R3-posix-permission-test-nondeterministic, R3-service-fail-closed-untested.
- Pending: manual in-game verification by the user (README checklist).
