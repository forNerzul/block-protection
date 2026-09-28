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

## Next step
Feature complete pending the user's manual in-game verification (README
checklist) after deploying the built jar.
