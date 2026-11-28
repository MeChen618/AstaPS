# Fontaine dive stamina initialization

Free-exploration account initialization previously set PROP_DIVE_MAX_STAMINA to zero. Existing saved accounts also kept that zero cap. Login now repairs a nonpositive cap and fills current dive stamina before PlayerDataNotify. A positive saved cap is preserved. Dive-enabling commands use the same cap.

The default is 12000 (the resource constant 120 in hundredths). Current stamina is bounded by the maximum. This does not grant the ability to dive to accounts that have not unlocked it.

DiveStaminaInitializationTest covers questing modes, existing zero-cap saves, positive saved caps, initialization order and bounds. Client checks: relog an existing account, try underwater sprint in Fontaine and surface swimming, and verify positive current/max dive stamina.
