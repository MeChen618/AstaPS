# CLI migration (Picocli)

This branch ports the command parser and all converted built-in commands from
`play/rino` onto the current upstream `main`, without bringing over the
unrelated quest, protocol, resource, combat or server changes.

## Command parsing

- Built-in commands declare their grammar using Picocli arguments, options and subcommands.
- The console uses JLine parsing and Picocli completion.
- `@username` selects a target by its exact account username (including numeric usernames), not a Picocli argument file. Command-local UID arguments are unaffected.
- Commands can opt out of implicit target parsing when `@UID` is positional data.
- New account names must not contain `@` or `.`; older stored accounts remain readable.
- The command registry rejects duplicate names or aliases.
- Console feedback continues to reach the web-console output capture.

## Account operations

Console-only commands include:

```text
account create <username> [<password>] [@UID]
account clone <source-account> <new-account> [@UID]
account delete <account>
account resetpass <account> <new-password>
```

Create accepts a username alone, a username and password, a username and @UID, or all
three (password before @UID). Without a password, the account stays passwordless until
one is set via resetpass.

Console-created and automatically registered accounts receive no implicit administrator
permissions. Both use the same configured `account.defaultPermissions` and explicit
grants. Legacy database documents missing a `permissions` field no longer acquire `*`
on load; existing explicitly stored grants remain unchanged.

`account resetpass <username> <new-password>` replaces the account password and
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

- `kill` (aliases `killCharacter`, `suicide`) kills the active character; `kill all <key> [sceneId]` kills monsters. The standalone `killall` and `kill character` routes are removed.
- `restore energy` replaces `er` / `e` / `energy`; `heal` and `heal all` remain because the latter covers off-team avatars.
- `setStats <stat> <value>`, `setStats lock <stat> [value]` and `setStats unlock <stat>` are canonical; redundant `set`, `freeze` and `unfreeze` subcommands are removed.
- `setSceneTag add <id>`, `remove <id>`, `reset` and `unlock all` are canonical; the old `set`, `del`, `restore` and `unlockall` subcommands are removed.
- `unlock all [@UID]` replaces the standalone `unlockall` command. The existing `player.unlockall` permissions, open-state blacklist and scene-tag exclusion remain unchanged. It is distinct from `tag unlock all`, which operates on scene tags.\n- `ban <@UID|accountName|IPv4> [endTime] [reason...]` selects the target from its first argument, with no `player` or `ip` subcommand. UID and account bans can expire; IPv4 bans are permanent and accept an optional reason but no end time. Account bans require `server.ban` and `server.ban.others` for someone else. IP bans require `server.banip` (no additional key). `unban ip <key> <ip>` remains unchanged.

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
After `@rino` (or `target @rino`), it displays the account name and UID,
e.g. `rino@10001> `. Entering `@` (or `target`) clears the selection and
restores `asta> `. The prompt uses the same target state as command execution;
per-command inline `@username` overrides do not change that remembered target.
The account name is cached when selecting the target, so rendering the prompt
never loads offline players or queries the database.

### Username-only command targets

`@rino`, `@20261010`, and inline `@username` selectors address a player by
their exact **account username** only. Digit-only usernames are valid.
The numeric string is never interpreted as a UID, and there is no fallback or
`name:`/`uid:` selector grammar. A valid account with no created player cannot
be selected as a player target. `@` clears the remembered target.
