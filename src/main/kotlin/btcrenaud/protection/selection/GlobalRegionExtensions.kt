package btcrenaud.protection.selection

import btcrenaud.protection.entry.artifact.GlobalRegionArtifactEntry

fun GlobalRegionArtifactEntry.toRegionShape(): GlobalRegionShape = GlobalRegionShape(
    worlds = resolvedWorlds(),
    minY = minY,
    maxY = maxY,
)

