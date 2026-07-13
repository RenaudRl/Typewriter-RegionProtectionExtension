package btc.renaud.protection.listener.movement

import btc.renaud.protection.flags.FlagEvaluation
import btc.renaud.protection.flags.RegionFlagKey
import btc.renaud.protection.service.storage.RegionModel

sealed interface EntryDecision {
    data object Allowed : EntryDecision
    data class Blocked(val region: RegionModel, val flag: RegionFlagKey, val evaluation: FlagEvaluation.Denied) : EntryDecision
}

