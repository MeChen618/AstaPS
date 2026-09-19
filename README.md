# AstaPS

English · [繁體中文](README_zh-TW.md)

A private server for Genshin Impact **7.0.0**, built on Grasscutter.

> This is a research and preservation project. It is not affiliated with, endorsed by, or connected to HoYoverse / miHoYo in any way, and it is not for commercial use.

## What it is

- **Up-to-date content.** Monster and gadget spawn data current with 7.0.0, Spiral Abyss rotations, domains, the artifact shop, battle pass, and the rest of the live-service surface.
- **Built to survive a bad day.** Database writes are split across four bounded pools that apply backpressure instead of dropping a player's progress; one world throwing during a tick no longer stops everybody else's; a watchdog holds the tick while MongoDB is unreachable rather than letting play continue against a database that cannot record it.
- **Visible when it is unwell.** A status readout logs CPU, memory, GC and every thread pool's queue depth on an interval, and `/api/status` serves the same figures over HTTP.
- **English throughout.** Source, comments, commit messages and command output.

## What is coming

Barring surprises: an update to **7.1**, then a change to the UID format, then the removal of the quests that are added by default.

## Requirements

| | |
|---|---|
| Java | 21 to build. The sources target 17, but virtual threads and other 21 APIs compile against the JDK's own classes. |
| MongoDB | Community Server. Must be running before the server starts. |
| Game client | Genshin Impact 7.0.0 |
| Resources | A 7.0.0 resource pack, extracted to `resources/` in the server directory. Not included here. |

## Building

```
./gradlew jar -PskipHandbook=1
```

`grasscutter.jar` lands in the project root. Drop `-PskipHandbook=1` to build the in-game handbook as well; that step needs NodeJS and fails without it.

On Windows use `.\gradlew.bat`, or run `gradlew-jar.bat`.

## Running

1. Start MongoDB.
2. Put a 7.0.0 resource pack in `resources/`.
3. Run the jar once. It writes a `config.json` and stops if anything essential is missing.
4. Start it again. The dispatch server listens on `8088` and the game server on `22101` by default.
5. Point the client at the dispatch server. A proxy such as Fiddler or mitmproxy will do it, as will a client patch.

### Accounts

There is no registration page. An account is created either way:

- **From the console.** `account create <username> [uid] [password]`
- **At sign-in.** Signing in with a name nobody holds registers it. With `account.useIntegrationPassword` on, put `name&&password` in the username box and leave the password box alone — useful where the launcher's password field is not usable. `account.autoCreate` turns this off for a closed server.

Passwords are BCrypt-hashed. The console needs `server.game.enableConsole` set to `true`.

## Commands

`help` lists them. A few worth knowing:

| | |
|---|---|
| `give` | Avatars, weapons, artifacts and materials. Level 100 by default. |
| `account` | Create and delete accounts, reset passwords. |
| `banip` / `unbanip` | Ban an address. Banning one also bans the account arriving from it. |
| `sysmail` | Send system mail to every player. |

## Licence

Released under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE).

`LICENSE-ClassGraph.txt` is not this project's licence. ClassGraph is an MIT-licensed dependency whose compiled classes ship inside `grasscutter.jar`, and MIT asks only that its notice travels with them, so the file stays.

## Credits

This server is based on **Grasscutter**. Reference projects: **LunaGC**, **HunkyMeow**.

The import commit at the root of this repository credits by name the authors whose work it carries.
