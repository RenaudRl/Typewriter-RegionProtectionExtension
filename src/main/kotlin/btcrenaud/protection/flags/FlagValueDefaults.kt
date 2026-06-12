package btcrenaud.protection.flags

internal val regionFlagDefinitions: Map<RegionFlagKey, RegionFlagDefinition> by lazy {
    RegionFlagRegistry.buildDefinitions()
}

internal fun RegionFlagKey.defaultFlagValue(): FlagValue {
    return regionFlagDefinitions[this]?.defaultFlagValue() ?: FlagValue.Text("")
}

internal fun RegionFlagDefinition.defaultFlagValue(): FlagValue {
    return when (valueKind) {
        FlagValueKind.BOOLEAN -> FlagValue.Boolean()
        FlagValueKind.INTEGER -> FlagValue.IntValue()
        FlagValueKind.DOUBLE -> FlagValue.DoubleValue()
        FlagValueKind.STRING -> FlagValue.Text(defaultValue ?: allowedValues.firstOrNull().orEmpty())
        FlagValueKind.COLOR -> FlagValue.ColorValue()
        FlagValueKind.ENUM -> FlagValue.Text(allowedValues.firstOrNull().orEmpty())
        FlagValueKind.LIST -> FlagValue.ListValue()
        FlagValueKind.ACTIONS -> FlagValue.Actions()
        FlagValueKind.LOCATION -> FlagValue.LocationValue()
        FlagValueKind.VECTOR -> FlagValue.VectorValue()
        FlagValueKind.SOUND -> FlagValue.SoundValue()
        FlagValueKind.POTION_LIST -> FlagValue.ListValue()
        FlagValueKind.NONE -> FlagValue.Text("")
    }
}

internal fun parseFlagValue(key: RegionFlagKey, raw: String): FlagValue {
    val definition = regionFlagDefinitions[key]
    return when (definition?.valueKind) {
        FlagValueKind.BOOLEAN -> FlagValue.Boolean(raw.toBooleanStrictOrNull() ?: raw.equals("true", ignoreCase = true))
        FlagValueKind.INTEGER -> FlagValue.IntValue(raw.toIntOrNull() ?: 0)
        FlagValueKind.DOUBLE -> FlagValue.DoubleValue(raw.toDoubleOrNull() ?: 0.0)
        FlagValueKind.LIST -> FlagValue.ListValue(raw.split(',').map { it.trim() }.filter { it.isNotEmpty() })
        FlagValueKind.STRING, FlagValueKind.ENUM, FlagValueKind.COLOR -> FlagValue.Text(raw)
        else -> definition?.defaultFlagValue() ?: FlagValue.Text(raw)
    }
}

