package btc.renaud.protection.selection

import btc.renaud.protection.entry.artifact.GlobalRegionArtifactEntry

fun GlobalRegionArtifactEntry.toRegionShape(): GlobalRegionShape = GlobalRegionShape(
    worlds = resolvedWorlds(),
    minY = minY,
    maxY = maxY,
)

