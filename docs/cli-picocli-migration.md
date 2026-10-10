# CLI migration (Picocli)

This branch ports the command parser and all converted built-in commands from
`play/rino` onto the current upstream `main`, without bringing over the
unrelated quest, protocol, resource, combat or server changes.

## Command parsing

- Built-in commands declare their grammar using Picocli arguments, options and subcommands.
- The console uses JLine parsing and Picocli completion.
- `@UID` remains an AstaPS target selector, not a Picocli argument file.
- Commands can opt out of implicit target parsing when `@UID` is positional data.
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
- `setSceneTag add <id>`, `remove <id>` and `reset` replace the duplicate `set`, `del` and `restore` subcommands.
- `ban ip <key> <ip> [reason...]` and `unban ip <key> <ip>` are the only IP-ban routes; standalone `banip` and `unbanip` were removed.

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

`achievement grantall` and `achievement revokeall` now call the achievement
manager's batched operations. Each operation preserves the existing used/parent
filters, status-transition counts, linked stages, reward-claim history and per-stage
completion events. It recomputes the completed count once, queues one achievement
document save, and groups unique changed achievements into update packets of at
most 128 entries. Single-ID grant, revoke and progress retain the same behavior.

`achievement revokeall` detects finished stages across each group, even if its
representative (the final stage, marked `isParent`) is unfinished. This fixes
partially completed multi-stage achievements that were skipped by the old check.
Already claimed rewards remain non-redeemable after reset.
