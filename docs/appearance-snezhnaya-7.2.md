# Snezhnaya 7.2 area UI and appearance selection

## Snezhnaya areas

Area detection previously accepted only 55–59. The 7.2 Excel rows for areas 60 and 61
(world rows 82000 and 83000) carry `DynamicAbility_IceCreature_V7_0_Team` too. Detection now
reads this resource group rather than assuming the last area is 59. Older tables without
the ability-group column retain a 55–61 fallback.

The installed outline file is older than the Excel table: it has areas 55–59 but no 60/61.
For areas without an outline, `EnterWorldAreaReq` now supplies the current area and refreshes
the team abilities. A scene entry clears that report so a teleport cannot reuse a previous
region. Existing outlines continue to drive position checks. The granted abilities include
Ice Creature placement, Kresnik/Frigid and the Snezhnaya notebook.

## Portraits and frames

The old list handler was still registered for 7.1 and emitted obsolete fields. The 7.2
request is 21907 and response is 27386 (`MCKPIBALIIE`):

| Meaning | Native field | Wire field |
| --- | --- | --- |
| Frame IDs | JNMPJJDOBOF, +0x18 | packed uint32, 5 |
| Picture IDs | HJBPILJAPFM, +0x20 | packed uint32, 14 |
| Retcode | +0x28 | int32, 12 |

Codec/reader RVAs: `0x925c5b0` / `0x925c310`. Dialog consumers `0x830b480` and `0x830b700`
identify the meanings independently of wire tags. Choices come from the portrait/frame
tables, including all 12 installed frames rather than the previous five hardcoded IDs.
Listing no longer forges completed quests or adds inventory items.

The Use-button callback at `0x830e7f0` submits picture request 22526 (`FKCONDDAJAE`, ID at
uint32 field 3) or frame request 29096 (`NBACHCMCPLE`, ID at uint32 field 2). Their writers
are `0x104b2d80` and `0xcfa4b50`. The server validates IDs against the same tables and uses
the existing persistent player setters. Legacy 7.1 handlers 6969/21517 are disabled.

The full appearance refresh 675 (`CDLELGKBDMM`) carries `ProfilePicture` on field 6 and
retcode on field 15. Both setters currently send that verified refresh with the request's
sequence. The native handlers for this refresh and 8692 (`JAENIDJLFMJ`, profile field 8,
retcode field 3) both apply the appearance and emit the same dialog-refresh event. The exact
request-to-response pairing has not been established; the common refresh behavior still
requires client testing. The separate obsolete 7.1 Beyond appearance notify is disabled.

`ProfilePicture` itself retains avatar field 1, costume field 2, picture field 3 and frame
field 4. Responses include the existing other selection so changing one preserves the other.

Evidence uses the fingerprint-checked CN Windows 7.2 client:

- Executable SHA-256: `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`
- Metadata SHA-256: `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`
- Startup SHA-256: `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`

## Validation

Regression tests cover both 7.2 areas, resource-based region detection, missing outlines,
scene-entry resets, native appearance wire fields, request sequences, saved selections and
rejection of unknown IDs. Resource tests check the full portrait/frame tables when present.

Live client rendering and immediate dialog refresh require client verification.

The isolated branch passed 1,698 nonintegration tests with the complete resource set and
the test JVM timezone set to UTC through a local Gradle init script. The initial run in
Asia/Shanghai before 04:00 hit eight pre-existing daily-task assertions: those fixtures
compare the host calendar date to production's 04:00 game-day boundary. The daily-task
production code and fixtures are unchanged from main in this patch.
