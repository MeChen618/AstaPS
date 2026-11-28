# 7.2 Ice Creature wheel and Kresnik energy

The wheel was empty because `IceCreatureDataNotify` and `IceCreatureSetTypeGadgetReq` were
disabled (CmdId 0), and their schemas still described 7.1. The placement response and standing
creature list also used parked or obsolete fields. The local patch enables the 7.2 wheel data
and selection routes and corrects the build request, response, gadget info and restoration envelope.

All ten resource types with an `order` are offered, including their configured alternate looks.
Quest/activity-only rows stay outside the wheel. Valid selections and auto-replace preferences
are kept on the player; an invalid gadget, mismatched type or unknown enum cannot replace them.

`/kresnik [energy]` (`/kshnik` alias) queries or sets current Kresnik energy, from 0 to 500.
The command updates the server's balance and sends `SGV_SnezhnayaEnergy` to the team entity;
the following cold tick therefore uses the new balance. It uses the existing `player.setprop`
and `player.setprop.others` permissions. This balance is session state, like the existing cold meter.

## Native evidence

Input: Windows CN 7.2, statically inspected without launching or attaching to the client.

| File | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

| Schema | Native type | CmdId | Field evidence (RVA) |
| --- | --- | --- | --- |
| Wheel notify | `PIOFANAELFP` | 27355 | codec `0xd129ad0`, reader `0xd12a440` |
| Type/gadget selection | `HODBNMLKJKP` | dependency | writer `0x8ef4a00`, reader `0x8ef4d40` |
| Set selection request | `GBCHKPECGFB` | 6439 | writer `0xd43b560`, sender `0xce05510` |
| Build request | `GEBEKBFOCHK` | 5949 | codec `0xd675000`, writer `0xd675140` |
| Build response | `IEJNLENCAIO` | 28174 | reader `0x108c9210` |
| Gadget info | `GEHADJLKHBD` | dependency | writer `0xc9f4cb0`, reader `0xc9f6120` |
| Gadget detail | `EINIIJLJBCN` | dependency | writer `0xebffc80` |
| Standing creature notify | `HOAJPGFOHHP` | 24204 | codec `0x9b77700`, reader `0x9b77c10` |

The wheel's notify uses packed uint32 unlocks at field 8 (`LNGEPLAGDIG`) and selected pairs at
field 9 (`PDCPOEEFLCN`). `ACHNLDFGBIJ.DDGAKABICFJ` (`0x13dada90`) unions the unlocks into
its gadget set at `+0x18`, exposed by the wheel's `EFNCIPABKKL` (`0xc10d0c0`). It copies the
pairs into a dictionary keyed by type. The selection pair uses gadget 10, type 12, enum 7;
the request uses gadget 3, type 9, enum 5. The wheel sender at `0xc10d2bf` passes the type,
enum and the selected gadget from the type's configuration list to `0xce05510`.

Only these two required fields of the 15-field notify are implemented. Unidentified fields,
including its GUID lists and counters, are not populated using 7.1 assumptions. Standing gadget
details use the separate notify and build response. `IceCreatureGadgetDisappearNotify` remains
unbound; this patch does not claim to recover the complete Ice Creature protocol.

The build request uses detail 2, global values 4, packed fixed64 replacement GUIDs 11 and flag 12.
The response nests gadget info at field 8; restoration repeats it at field 10. Gadget info uses
position 1, nickname 2, detail 3, area 4, likes 5, map layer 6, fixed32 creation time 7, creator UID 8,
entity 9, the existing unnamed counter 10, profile 11, gadget 12, online ID 13, Xbox name 14,
uint64 GUID 15, scene 739 and flags 1637/1249. Field numbers/encodings are native evidence;
less central inherited aliases such as the platform-name strings remain structural identifications.
Nested detail uses opaque storage at 3 and build detail at 13. The existing beacon/decorate oneof
has tags 1/2 in `OCHOBGBKBPN` (`0x141e2a50`).

Local full instruction records and field traces are retained in `local/ice-map-analysis/`.
They are ignored development inputs, not part of the distributable server.

## Validation and client checks

Regression tests use independent native wire vectors for the wheel, selection and build fields;
exercise default/non-cube/invalid selections; check the placement/restoration envelopes; and
verify that the energy command sends the actual team global-value packet.

The isolated nonintegration test run passed: 1699 passed, zero failures/errors and four skipped. The two
existing daily-commission test fixtures now use the game's reset-day helper instead of the
calendar date, so their expected stored day is valid between midnight and 04:00 too. No daily
commission runtime code changed. The actual local resource audit covers ten wheel types with sixteen gadgets. The test JAR passes ZIP integrity.

After restarting with `ServerStart.cmd`, relog, open the Ice Creature selection page, select a
non-cube type and alternate look, place it, and verify the choice after another relog. Run
`/kresnik 0`, `/kresnik 250`, `/kresnik 500`, and `/kresnik` and inspect the energy bar.
Also repeat the Fontaine sprint checks in `dive-stamina.md`.
Automated checks do not establish the observed client result.
