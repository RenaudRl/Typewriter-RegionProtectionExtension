# Protection QA Matrix

This matrix tracks the manual scenarios we use to validate the core Protection listeners.

## Building / Placement

| Scenario | Preparation | Steps | Expected result |
| --- | --- | --- | --- |
| Block placement denied | Create two nested regions, `spawn` (priority 5) and `market` (priority 10) with `block_place=false` on `spawn`. | Attempt to place a block in survival at the spawn centre. | Placement is cancelled, the player sees `Action denied: block_place.denied`, and the debug log prints `Flag block_place denied PlayerInteractEvent in region spawn`. |
| Block placement allowed | Same regions, but set `block_place=true` on `market`. | Repeat the placement inside `market`. | The block places successfully and the log shows `Flag block_place explicitly allowed PlayerInteractEvent in region market`. |

## Combat

| Scenario | Preparation | Steps | Expected result |
| --- | --- | --- | --- |
| PvP disabled | Region `sanctuary` (priority 8) with `pvp=false`. | Two players attack each other inside the region. | Damage is cancelled, the attacker receives `Action denied: sanctuary.pvp`, and the debug log shows `Denied EntityDamageByEntityEvent`. |
| Damage modifier | Add a custom `pvp` flag in `arena` with metadata `damage.multiplier=0.5`. | Two players fight inside `arena`. | Damage is halved, the attacker receives `Damage reduced by arena rules`, and the log records `modified EntityDamageByEntityEvent` with the `damage.multiplier` key. |

## Interaction

| Scenario | Preparation | Steps | Expected result |
| --- | --- | --- | --- |
| Interaction denied | Region `museum` with `interact=false`. | Right-click a lever. | The lever state does not change, the player receives `Action denied: museum.interact`, and the log prints `Flag interact denied PlayerInteractEvent`. |
| Custom message | Region `museum` with `interact=false` and metadata `message=Please contact a guide`. | Right-click a lever. | The custom text `Please contact a guide` appears and the log reports `Sent custom message to <player>`. |

## Entities

| Scenario | Preparation | Steps | Expected result |
| --- | --- | --- | --- |
| Pickup denied | Region `vault` with `item_pickup=false`. | Drop an item and attempt to collect it. | Item remains on the ground, the player receives `Action denied: item_pickup.denied`, and the log records `Flag item_pickup denied PlayerAttemptPickupItemEvent`. |
| Entry teleport | Region `lobby` with `teleport_on_entry` pointing to a warp. | Walk into the lobby. | Player is teleported to the configured location, the debug log includes `Teleporting <player>`, and no stray command execution logs appear. |

Re-run these scenarios after each major release to ensure parity between automated coverage and the in-game experience.
