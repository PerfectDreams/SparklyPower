package net.sparklypower.rpc

import kotlinx.serialization.Serializable

@Serializable
class PrestartPantufaPrintShopCustomMapsRequest(
    val requestedById: String,
    val amountOfMapsToBeGenerated: Int,
    val copies: Int
)

@Serializable
sealed class PrestartPantufaPrintShopCustomMapsResponse {
    @Serializable
    data class Success(
        val costOfEachMap: Long,
        val totalCost: Long
    ) : PrestartPantufaPrintShopCustomMapsResponse()

    @Serializable
    data class NotEnoughPesadelos(
        val costOfEachMap: Long,
        val totalCost: Long
    ) : PrestartPantufaPrintShopCustomMapsResponse()

    @Serializable
    data object PluginUnavailable : PrestartPantufaPrintShopCustomMapsResponse()
}