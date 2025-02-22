package net.perfectdreams.dreamcore.utils.extensions

import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.ClaimPermission
import net.minecraft.network.protocol.Packet
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.MeninaAPI
import net.perfectdreams.dreamcore.utils.PlayerUtils
import net.perfectdreams.dreamcore.utils.collections.mutablePlayerMapOf
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.*

fun Player.canBreakAt(location: Location, material: Material) = PlayerUtils.canBreakAt(location, this, material)
fun Player.canPlaceAt(location: Location, material: Material) = PlayerUtils.canPlaceAt(location, this, material)
fun Player.healAndFeed() = PlayerUtils.healAndFeed(this)

var Player.girl: Boolean
    get() = MeninaAPI.isGirl(this.uniqueId)
    set(value) {
        MeninaAPI.setGirlStatus(this.uniqueId, value)
        return
    }

val Player.pronome: String
    get() = MeninaAPI.getPronome(this)

val Player.artigo: String
    get() = MeninaAPI.getArtigo(this)

val EMPTY_ITEM = Material.AIR.toItemStack()
val playerInventories = mutablePlayerMapOf<Array<ItemStack>> { player, content ->
    player.inventory.contents = content
}

/**
 * Stores [Player]'s inventory.
 */
fun Player.storeInventory() {
    playerInventories[this] = inventory.contents.map { it ?: EMPTY_ITEM }.toTypedArray()
    inventory.clear()
}

/**
 * Retrieves [Player]'s stored inventory.
 */
fun Player.restoreInventory() = playerInventories[this]?.let { inventory.contents = it  }

/**
 * Plays [sound] and sends [message] to [Player].
 */
fun Player.playSoundAndSendMessage(sound: Sound, message: String) {
    playSound(location, sound, 10F, 1F)
    sendMessage(message)
}

/**
 * @return Whether [Player] has [permission] at [claim] or not.
 */
private fun Player.hasPermissionAtClaim(permission: ClaimPermission, claim: Claim, staffBypass: Boolean) =
    claim.hasExplicitPermission(this, permission) || (staffBypass && this.isStaff)

/**
 * @return Whether the owner of the claim has trusted [Player] to build at said claim.
 */
fun Player.canBuildAtClaim(claim: Claim, staffBypass: Boolean) =
    this.hasPermissionAtClaim(ClaimPermission.Build, claim, staffBypass)

/**
 * @return Whether the owner of the claim has trusted [Player] to manage said claim.
 */
fun Player.canManageClaim(claim: Claim, staffBypass: Boolean) =
    this.hasPermissionAtClaim(ClaimPermission.Manage, claim, staffBypass)

/**
 * Since we are only interested in whether [Player] has either [ClaimPermission.Build] or [ClaimPermission.Manage]
 * permissions, there is no need to check for the other types.
 */
fun Player.hasAnyPermissionAtClaim(claim: Claim, staffBypass: Boolean) =
    this.canBuildAtClaim(claim, staffBypass) || this.canManageClaim(claim, staffBypass)

/**
 * Sends a NMS (`net.minecraft.server`) packet to the [Player]
 *
 * @param packet the NMS packet that will be sent
 */
fun Player.sendPacket(packet: Packet<*>) {
    (this as CraftPlayer).handle.connection.send(packet)
}


/**
 * Hides a player from this player without removing them from the player list
 *
 * @param plugin Plugin that wants to hide the player
 * @param player Player to hide
 */
fun Player.hidePlayerWithoutRemovingFromPlayerList(plugin: Plugin, otherPlayer: Player) {
    DreamCore.INSTANCE.getOrCreatePlayerVisibilityManager(this).hidePlayer(plugin, otherPlayer)
}


/**
 * Allows this player to see a player that was previously hidden. If
 * another plugin had hidden the player too, then the player will
 * remain hidden until the other plugin calls this method too.
 *
 * @param plugin Plugin that wants to show the player
 * @param player Player to show
 */
fun Player.showPlayerWithoutRemovingFromPlayerList(plugin: Plugin, otherPlayer: Player) {
    DreamCore.INSTANCE.getOrCreatePlayerVisibilityManager(this).showPlayer(plugin, otherPlayer)
}