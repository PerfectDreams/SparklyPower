package net.perfectdreams.dreamsinuca.sinuca

import kotlinx.serialization.Serializable

@Serializable
data class PoolTableData(
    val orientation: PoolTableOrientation
)