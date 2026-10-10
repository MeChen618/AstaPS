# CLI migration (Picocli)

This branch ports the command parser and all converted built-in commands from
`play/rino` onto the current upstream `main`, without bringing over the
unrelated quest, protocol, resource, combat or server changes.

## Command parsing

- Built-in commands declare their grammar using Picocli arguments, options and subcommands.
- The console uses JLine parsing and Picocli completion.
- Player-targeting commands share the `playerSelector` syntax below. `@` clears the remembered target.
- Commands with positional player selectors (`coop`, `ban`, `unban`, `kick`, `mail send`) keep those arguments in place, while using the same centralized identity parser and resolver as ordinary command targets.
- New account names must not contain `@` or `.`; older stored accounts remain readable.
- The command registry rejects duplicate names or aliases.
- Console feedback continues to reach the web-console output capture.

## Account operations

Console-only commands include:

```text
account create <username> [password] [@UID]
account clone <sourceSelector> <newUsername> [@UID]
account delete <accountSelector>
account resetpassword <accountSelector> <new-password> # alias: account passwd
```

Create accepts a username alone, a username and password, a username and @UID, or all
three (password before @UID). Clone accepts an explicit source selector, a new username,
and an optional trailing @UID. Without a reserved UID, one is allocated automatically.
Without a password, a newly created account stays passwordless until one is set via
resetpassword (alias: passwd).

Console-created and automatically registered accounts receive no implicit administrator
permissions. Both use the same configured `account.defaultPermissions` and explicit
grants. Legacy database documents missing a `permissions` field no longer acquire `*`
on load; existing explicitly stored grants remain unchanged.

`account resetpassword <accountSelector> <new-password>` replaces the account password and
atomically removes the existing `token` and `sessionKey` from the MongoDB
account document. The write is synchronous and performed behind the database
write barrier, so success is reported only after the change is committed.
The currently connected player is then disconnected and must log in again.

The clone operation needs an offline source player and a new account name.
It copies player-owned gameplay collections and uses the database writer barrier
to avoid copying unfinished saves; friendships and shared beatmaps are excluded.
The clone inherits the source account password and permissions.

## Canonical command routes

The Picocli command tree keeps one route for each operation:

- `kill` (alias `suicide`) kills the active character; `kill all <key> [sceneId]` kills monsters. The standalone `killall` and `kill character` routes are removed.
- `restore energy` replaces `er` / `e` / `energy`; `heal` and `heal all` remain because the latter covers off-team avatars.
- `setStats <stat> <value>`, `setStats lock <stat> [value]` and `setStats unlock <stat>` are canonical; redundant `set`, `freeze` and `unfreeze` subcommands are removed.
- `setSceneTag add <id>`, `remove <id>`, `reset` and `unlock all` are canonical; the old `set`, `del`, `restore` and `unlockall` subcommands are removed.
- `unlock all [@UID]` replaces the standalone `unlockall` command. The existing `player.unlockall` permissions, open-state blacklist and scene-tag exclusion remain unchanged. It is distinct from `tag unlock all`, which operates on scene tags.
- `ban <playerSelector> [endTime] [reason...]` bans an account (`endTime` is a Unix timestamp); `ban <IPv4> [reason...]` bans an IP. `unban <playerSelector|IPv4>` uses the same parser without subcommands or a keystore key. Account ban/unban requires `server.ban` and, for another account, `server.ban.others`; IP ban/unban requires `server.banip`.
- `kick <playerSelector>` disconnects an online player. It requires `server.kick`, does not accept a keystore key, and no longer has the misleading `restart` alias.
- `announce send <content...>` is the only direct-send route; bare `announce <content...>` is removed. `announce template <templateId>` is the canonical template route; `announce tpl` remains an alias.
- `say <message...>` is the canonical message command. `sendMessage`, `sendservmsg`, `sendservermessage`, `b`, and `broadcast` are removed.
- `player list` replaces `list [uid]` and `players`; it always displays each online player’s nickname and UID. The `--uid` option is not supported.
- `account resetpassword <accountSelector> <new-password>` replaces `account resetpass`; `account passwd` is its sole shortcut. `account delete` and `account clone` also use an explicit selector for the existing account.
- `info` has no `troubleshoot` or `helpme` aliases.
- `coop [guestSelector] <hostSelector>` requires an online host. When the guest is omitted, it uses the current command target (the sender by default in-game); in the console, choose a guest with `target` or provide both selectors. Each explicit selector follows `playerSelector` syntax. Moving another player requires `server.coop.others`.

- `dungeon <dungeonId>` is the canonical dungeon command. The former `enter_dungeon` and `enterdungeon` names are removed; the `player.enterdungeon` permission key remains unchanged.
- Removed aliases: `pb`, `levelbreak`, `killCharacter`, `mattrack` and `unlockwp`; use `br`, `el`, `suicide`, `trackmaterial` and `wp` respectively.

## Help and invalid input

- `help` lists commands with their aliases and short descriptions; `help <command>` shows the full command usage. Nested command help is available as `help <command> <subcommand>`, for example `help teleport pos` or `help tp pos`.
- Picocli parse errors, including type-conversion errors, show both the error message and the corresponding command or subcommand syntax.

## Review and validation

- Compare the complete command registry and aliases against upstream before merge.
- Compile and run unit tests, including command parser/registry and database writer tests.
- Ensure game clients are not asked to test before CI has passed.
- Keep the upstream PR as a draft until CI succeeds and compatibility exceptions are documented.


### Achievement reward redemption

An achievement's persisted `rewardClaimed` flag is independent of its current progress
and completion state. Legacy `REWARD_TAKEN` statuses are migrated on load. Revoking a
completed achievement clears its progress but never refunds or resets a previously
claimed reward; completing it again restores `REWARD_TAKEN` rather than making it
claimable. Reward requests require the achievement to be finished and unclaimed, and
reject duplicate IDs. Already claimed achievement-goal rewards and duplicate goal IDs
are also rejected without awarding items.


### Bulk achievement commands

`achievement grant all` and `achievement revoke all` now call the achievement
manager's batched operations. Grant-all visits every used achievement ID directly,
sets each unfinished stage to its own required progress, and applies its individual
completion transition and event. It does not use the parent marker or synchronize
progress across stages. Revoke-all iterates each persisted, valid achievement ID and
resets completed states independently. Both preserve reward-claim history and
status-transition counts, recompute the completed count once, queue one achievement
document save, and group unique changed achievements into update packets of at
most 128 entries. Single-ID grant, revoke and progress retain linked-stage behavior.

`achievement revoke all` does not use stage-group membership: every completed
achievement ID is reset directly, even if the final stage marked `isParent` has
not been completed. It does not erase progress in achievements that are already
unfinished. Already claimed rewards remain non-redeemable after reset.

### Battle-pass purchases and paid state

`bp buy <levels> [@UID]` and the client's `BuyBattlePassLevelReq` now both
use `BattlePassManager.buyLevels`. The manager validates requested levels,
caps the purchase at the remaining levels up to the BP maximum, computes the
price from `BATTLE_PASS_LEVEL_PRICE`, and rejects insufficient funds or a
failed primogem debit without changing BP level. Successful purchases save
both the player's primogem balance and the battle-pass level, retain fractional
BP progress, and send both regular and beyond BP schedule updates.

`bp paid [true|false] [@UID]` now uses the persisted `paid` field for
both state changes and queries. The ordinary 7.1 schedule derives its unlock
status and platform flags from that field. The compatibility helper also
uses the manager setter rather than reflection. New players default to a
free battle pass; previously persisted paid grants remain unchanged.

### Inventory clear preview

`clear <all|weapons|artifacts|materials> [--level N] [--refinement N]
[--rarity N] [--dry-run] [@UID]` supports `--dry-run` to preview the
matching inventory entries and their total stack quantities. For example,
`clear all --dry-run @10001` reports how many stacks and individual items
would be deleted without changing the inventory. The preview applies the
same category, rarity, level, refinement, locked and equipped filters as
the destructive path; `all` also includes furniture, display and virtual
inventory items. Without `--dry-run`, deletion behavior is unchanged.

### Decoupled bulk command selectors

The standalone `achievement grantall` / `achievement revokeall` routes have been
replaced by `achievement grant all` / `achievement revoke all`. The same
`grant` / `revoke` route also accepts a numeric achievement ID; batch manager
operations and localized success keys are unchanged.

`tag unlock all` (also `setSceneTag unlock all`) replaces `tag unlockall`.
`unlock all` replaces the standalone `unlockall` command while retaining its
current permission keys and behavior. These commands deliberately do different
things: `unlock all` leaves scene tags unchanged, but `tag unlock all` enables
scene tags and can create incompatible quest terrain states. Legacy concatenated
commands are removed rather than kept as duplicate aliases. Other uses of
`all` (such as `restore all` and `clear all`) keep their existing meanings.

### Target-aware JLine console prompt

The JLine prompt is `asta> ` when the console has no remembered target.
After selecting `rino@` (or `target rino@`), it displays the account name and UID,
e.g. `rino@10001> `. Entering `@` (or `target`) clears the selection and
restores `asta> `. The prompt uses the same target state as command execution;
per-command inline target overrides do not change that remembered target.
The account name is cached when selecting the target, so rendering the prompt
never loads offline players or queries the database.

### Player selectors

**`playerSelector`:** `@UID` (player UID), `username@` (exact account username), or
`username@UID` (both must match).

Use `playerSelector` for general command targets, `coop` guests and hosts, `ban` / `unban` accounts,
`kick` targets, and `mail send` recipients. The `ban` and `unban` commands also accept IPv4 addresses;
it can resolve accounts with reserved UIDs before character creation. `mail send all`
broadcasts to all players. `@` clears the remembered target, and
`target 10001` selects UID 10001.

In `account create` and `account clone`, a separate trailing `@UID` argument reserves
an optional UID for the new account. Existing account selectors use `@UID`, `username@`, or `username@UID`,
including accounts with a reserved UID but no character save. Username/UID matching
is exact.

### GM cutscene playback safety

`cutscene list [search]` is a resource lookup and no longer requires an online
target. The results are ordered by numeric cutscene ID, with the existing 30
result cap. `cutscene <id>` still requires an online player; an ID absent from
`CutsceneExcelConfigData` is rejected unless `--force` is supplied. Invalid
non-positive IDs are always rejected. `--force` only bypasses the Excel lookup,
not online or gameplay-safety checks.

Manually initiated cutscenes are tracked per game session. Their finish
notifications still receive the normal client acknowledgement, but never
complete pending scene scripts or trigger the Spiral Abyss half-time team
swap. Manual playback of an ID already awaited by scene scripts or the Abyss
transition is refused. Pending manual IDs are cleared upon acknowledgement
or after a bounded timeout; disconnecting ends the session-scoped tracking.
This prevents manual GM playback from being mistaken for a quest or tower
completion, but does not claim to make unknown client-side IDs playable.

### Daily commission resource diagnostics

`dailytask support` (or `dt support`) reports commission definition and Lua
resource coverage by city. It only inspects loaded game resources and no longer
requires an online player or a daily-task database record; it works from the
server console. Other `dailytask` operations (`list`, `load`, `reset`, `city`,
`finish`, and `bonus`) still require an online player and reject a missing
or offline target before loading any daily-task state. The root command can
print help without a selected player.


### Avatar command consolidation

Character-specific commands now share one `avatar` command group:

```text
avatar list
avatar constellation set <0-6> [--avatar <avatarId> | --all]
avatar constellation reset [--avatar <avatarId> | --all]
avatar talent set <talentId> <level> [--avatar <avatarId>]
avatar talent normal <level> [--avatar <avatarId>] # alias: n
avatar talent skill <level> [--avatar <avatarId>]  # alias: e
avatar talent burst <level> [--avatar <avatarId>]  # alias: q
avatar talent all <level> [--avatar <avatarId>]
avatar talent list [--avatar <avatarId>]
avatar friendship set <level> [--avatar <avatarId>]
avatar extralevel [--avatar <avatarId>]
avatar max [--avatar <avatarId> | --all]
```

`--avatar` selects a character owned by the target player by avatar ID. Without it,
single-character operations use the currently active character. `--all` selects
every owned character, and is mutually exclusive with `--avatar`. The
`talent all` operation means all skills of one character, not every character.
The standalone `constellation`, `talent`, `extralevel` (`el`),
`setFetterLevel`, and `max` commands and their aliases were removed.

Permissions are enforced per operation. Constellation set/reset retain distinct
`player.setconstellation[.others]` and `player.resetconstellation[.others]`
permissions. Talents use `player.settalent[.others]`; friendship uses
`player.setfetterlevel[.others]`; extra-level upgrades use `player.give[.others]`;
and max uses `player.max[.others]`. The `avatar list` operation currently
requires `player.give[.others]`.

The target must be online. Specifying `--avatar` does not allow modification of
unowned characters. Extra-level upgrades still consume their required materials;
max retains its level-90 cap. `Avatar.forceConstellationLevel` saves each changed
avatar. The existing scene transfer when lowering constellation levels or
changing all characters remains in place until the client refresh behavior
is verified; this is not a resolved scene-transition issue.
