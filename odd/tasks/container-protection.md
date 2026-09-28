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
- [ ] T1 Scaffold Gradle project, `plugin.yml`, default `config.yml`, `.gitignore`, empty plugin main. Route: delegated (writer trigger, 2+ files).
- [ ] T2 Domain: `Protection` (owner, trusted), access policy, global trust, protectable-type config — with unit tests. Route: delegated.
- [ ] T3 Persistence adapters: chunk PDC protection repository, YAML global-trust repository. Route: delegated.
- [ ] T4 Listeners: place, interact, break, double chest, hoppers, explosions, pistons, burn. Route: delegated.
- [ ] T5 Commands + permissions + messages. Route: delegated.
- [ ] T6 README with build/install/test instructions; final `gradle build`. Route: delegated.

## Acceptance criteria
- A non-owner cannot open, break, or hopper-drain a protected chest.
- After `/protect trust <player>`, that player can open it.
- Protection survives a server restart.
- A player with `entityprotection.admin` can access everything.

## Checks
- `gradle test`, `gradle build` per task.
- Manual in-game verification (user) after deploy.

## Progress
(none yet)

## Next step
T1.
