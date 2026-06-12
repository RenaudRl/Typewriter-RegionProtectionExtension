package btcrenaud.protection.listener.movement

import btcrenaud.protection.flags.FlagEvaluation
import btcrenaud.protection.flags.RegionFlagKey
import btcrenaud.protection.service.storage.RegionModel

sealed interface EntryDecision {
    data object Allowed : EntryDecision
    data class Blocked(val region: RegionModel, val flag: RegionFlagKey, val evaluation: FlagEvaluation.Denied) : EntryDecision
}

