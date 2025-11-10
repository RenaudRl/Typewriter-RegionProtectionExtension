package com.typewritermc.protection.selection

import com.typewritermc.protection.entry.artifact.GlobalRegionArtifactEntry

fun GlobalRegionArtifactEntry.toRegionShape(): GlobalRegionShape = GlobalRegionShape(
    worlds = resolvedWorlds(),
    minY = minY,
    maxY = maxY,
)
