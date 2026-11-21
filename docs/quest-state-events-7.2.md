# Recipe, waypoint, variable, and vehicle quest events

This follow-up builds on main `76caccc5` and partially addresses #253. It adds no beta activity data or automatic quest acceptance. The remaining quest census and the incomplete main-coop system stay open.

## Cooking and recipe learning

Cooking previously paid for ingredients before validating the output item, then ignored inventory refusal when granting each dish. A missing output record or a full output stack could consume ingredients without delivering the advertised result. Cooking now validates the recipe, selected output, quantity, and eligible specialty configuration first. The existing inventory admission API checks the complete output batch before authorizing payment under the same inventory monitor. Refusal or insufficient ingredients returns failure without increasing proficiency.

Default-unlocked recipes remain usable before the login notification initializes their saved entries. Unknown and unlearned recipes are rejected. A specialty requires an owned assistant; malformed specialty data cannot spend ingredients. Quantities outside 1–99 are rejected instead of silently becoming one cook. The existing handling of unset QTE quality is retained.

This reuses the inventory admission mechanism. It is not a database transaction and does not roll back an exceptional persistence failure after payment begins. Offline tests exercise admission rejection, affordability failure, configuration validation, ordinary successful delivery, and proficiency updates using an inventory test double; the existing inventory tests cover the underlying admission implementation.

Learning a real recipe now wakes both recipe-state conditions. The state handlers read the current recipe collection and also run when a quest starts. Quest 40015 lists `3107,2105,2102` as alternative recipes: its success condition accepts one learned candidate, while its three individual not-learned failure conditions cover the opposite branch. This is consistent with the subsequent cooking choices and the objective to buy and learn a preferred recipe. An unrelated recipe event, malformed list, or unknown recipe cannot satisfy the condition.

## Waypoints and parent variables

`QUEST_CONTENT_UNLOCK_ANY_TRANS_POINT` uses the condition's scene and comma-separated candidate point IDs. It means one point from that list, not any waypoint in the world. Checks read persisted unlock state, run for an already-unlocked point when the quest starts, and receive events from successful `PlayerProgressManager.unlockTransPoint` transitions. An unchanged unlock does not fire the event again.

`QUEST_CONTENT_QUEST_VAR_NOT_EQUAL` now has a registered handler and an event source. All four local quest-variable comparisons reject malformed indices and use the current parent quest's values. Variable events include the parent ID, so another parent changing the same variable index cannot finish this parent's objective. Existing events that only contain index/value remain accepted for compatibility.

## Vehicle membership

Both vehicle-response construction paths now share the entity's membership transition. A repeated board does not duplicate the passenger or its enter event. Leaving matches UID even if the active avatar GUID changed, and sends `QUEST_CONTENT_LEAVE_VEHICLE` only after a real removal. Guest events go to the guest's quest manager. Target gadget ID zero is supported for the resource's any-vehicle objectives; an empty event is not a dismount.

Vehicle-response construction no longer overwrites configured/current HP with 1000 on every interaction. The fallback is applied once, during entity construction, only when no positive maximum HP exists. Configured health, Saurian health handling, and damage across repeated interactions are preserved.

## Evidence and coverage

The enum defaults were read from the supplied **7.2.0 CN Android ARM64** sample. Values 138/139, 167, 171, and 182 and their metadata offsets are recorded in [`reference/quest-state-events-7.2.json`](reference/quest-state-events-7.2.json). These defaults establish the enum IDs, not every parameter's gameplay semantics or a completed client playthrough.

The registration census uses the **same resource snapshot as the previous review**: 34,545 rows, QuestExcel SHA-256 `3fbc7cce3523fb3fd9dab2348f7c9962b4a3c0a96c15f3c09ee74e1487f462c8`. This is a fixture-specific comparison; it must not be advertised as coverage of every 7.2.0 resource distribution.

| Finish/fail condition entries | Before | After |
| --- | ---: | ---: |
| Registered handling | 44852 | 44884 |
| Still without registered handling | 1154 | 1122 |

The 32 newly registered occurrences are 20 leave-vehicle, 5 recipe, 4 listed-waypoint, and 3 variable-not-equal entries. Action coverage is unchanged: 2,946 entries remain without registered handling. Registration does not prove that all these quests work end to end.

## Validation

```sh
./gradlew test jar -PexcludeTags=integration -PskipHandbook=1
python tools/quest-coverage.py /path/to/ExcelBinOutput/QuestExcelConfigData.json
```

Tests include the actual recipe and waypoint event producers passing through the real quest queue, parent/scene isolation, default recipes, refused cooking delivery, changed-avatar dismounts, duplicate vehicle responses, and guest ownership. The real-server integration-tagged tests are excluded. No matching-client playthrough is claimed.

Main-coop save points, scene-level tags, remaining team/mirror-team actions, weather-area actions, daily-task variable parameter semantics, and the other entries in the census still require separate implementation and evidence. This change does not close #253 or claim that the unspecific quest report #250 is fully resolved.
