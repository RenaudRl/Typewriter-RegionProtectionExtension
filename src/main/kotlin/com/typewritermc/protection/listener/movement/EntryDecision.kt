package com.typewritermc.protection.listener.movement

import com.typewritermc.protection.flags.FlagEvaluation
import com.typewritermc.protection.flags.RegionFlagKey
import com.typewritermc.protection.service.storage.RegionModel

sealed interface EntryDecision {
    data object Allowed : EntryDecision
    data class Blocked(val region: RegionModel, val flag: RegionFlagKey, val evaluation: FlagEvaluation.Denied) : EntryDecision
}
