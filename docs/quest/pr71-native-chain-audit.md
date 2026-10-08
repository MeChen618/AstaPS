# PR #71 — native 7.1 quest-chain audit

## Scope and evidence

Product branch: `upstream/fix-quest-runtime`; test resources: **MeChen618/AstaPS-Resource/main** only.
Source of truth: `BinOutput/Quest/{351,352,353,354,355,361,363}.json`, `ExcelBinOutput/ChapterExcelConfigData.json`, `ExcelBinOutput/TriggerExcelConfigData.json`, `Scripts/Scene/3/scene3_group133003002.lua`. 
`play/rino` is a behavior regression reference, **not** a replacement for the native evidence.

| Transition | Native prerequisite/event | Observable expected behavior |
| --- | --- | --- |
| Born → 35104 | First quest bootstrap only | Opening cinematic; no chapter-start banner |
| 35104 → 35100 | `FINISH_PLOT(35104)` | Reach Paimon |
| 35100 → 35101 / 35107 | `FINISH_PLOT(35100)` or trigger `1053`; `35101` accepts state `35100=FINISHED` | Paimon continues; 35107 is hidden |
| 35106 | `UNLOCK_TRANS_POINT(scene=3, point=6)` | First actual waypoint unlock |
| 35105 / 35103 → 35102 | Alternative route conditions → `TRIGGER_FIRE(1017)` | Climb completed; 351 parent ends |
| 35200 → 35201 → 35202 | Area triggers `1021`, `1001/1095`; `COMPLETE_TALK(35216)` | Hilltop overlook and Paimon talk |
| 35202 → 36301 | `STATE_EQUAL(35202, FINISHED)` | **Chapter 1001 START banner**; no manual launch |
| 35203 → 35204 → 35205 | Plot `35203` plus trigger `1172`, trigger `1003`, `FINISH_PLOT(35205)` | Swim to statue; point `3/7` and area `3/1` unlock; Traveler gains Anemo |
| 35301 → 35302 | `COMPLETE_TALK(35328)` | Native action `GRANT_TRIAL_AVATAR(1)`, whose actual client team effect is **unverified** |
| 35302 | `REFRESH_GROUP_SUITE(scene=3, group=133003002, suite=2)` | Tutorial slime monster config `439`, monster `20011202` |
| 35302 → 35309 → 35303 → 35310 → 35304 → 35311 | Native skill and Lua-notify predicates | Elemental skill / hold / burst tutorial |
| 35311 → 361 → 355 → 354 | Plot and area transitions | Forest/Dvalin scene; Amber formally appears in 354 |

## Explicit anti-patch rules

1. Do not use timed sleeps, forced quest-completion, fake events, hardcoded UI suppression, or direct chapter-controller starts to mask a state error.
2. For each defect identify the **native quest predicate**, the **emitted server event**, and the **client-visible result** before behavior modification.
3. Quest visibility matters: hidden subquests can be active alongside a visible objective. Do not collapse them into a single linear ID order.
4. Source policy: `QuestExcelConfigData` first for populated execution lists, `BinOutput/Quest` for missing execution lists. The 351/363 native accept-condition repair is a *reviewed exception*, backed by regression tests. Do not generalize source precedence before comparing more native files.
5. A missing native action is a resource validation error, **not** a reason to embed a second copy of quest data. The stale `quest-7.1-intro-exec-baseline.json` fork-compatibility overlay is removed.
6. When changing a predicate index, remove stale cache keys and verify the actual emitted event starts the intended subquest.
7. CI build and tests are required before asking for a client run. CI cannot validate animation, map markers, or team behavior.

## Known issues; no speculative fixes

- First-birth persistent Return prompt: client-side activation predicate remains unverified. `HandlerQuestTransmitReq` logs requests but does not synthesize a teleport.
- Two map objective markers after first waypoint/hill: exact simultaneous subquest states and guide geometry have not been captured.
- `35302` slime visibility: begin-exec and suite call exist, but real entity spawn and client sync require a gameplay trace.
- `35301` grants trial avatar id 1 (Amber) despite formal Amber meeting occurring in `35402`. Confirm native client behavior before changing team actions.
- `ExecRefreshGroupSuite` still retries asynchronously on scene-script initialization, for up to 100 attempts. **Unverified lifecycle strategy**; do not increase the limit or add waits as a substitute for a proper initialization hook. Audit this separately before changing it.
- `TalkManager.isTalkNpc` currently allows null scene entities for client-local quest actors; authorization of that specific client path needs protocol/identity review.

## Verification matrix

The next fresh-account test must use upstream resources and check in order: no birth banner; Paimon after `35100`; waypoint `3/6`; climb and hilltop talk `35216`; chapter begin **at `36301`**, not `35100`; statue `3/7`; first slime `133003002/439`; formal Amber dialogue `35404` and team state. Preserve timestamps and quest-ID logs for each observed mismatch.
