package net.perfectdreams.dreamreflections.modules.killaura

import com.mojang.authlib.GameProfile
import net.minecraft.network.protocol.game.*
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.perfectdreams.dreamcore.utils.extensions.sendPacket
import net.perfectdreams.dreamreflections.sessions.ReflectionSession
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.*

class KillAuraTester(val session: ReflectionSession) {
    val player = session.player
    val fakePlayersAlive = mutableListOf<KillAuraBait>()

    // YES, THIS CODE IS CORRECT
    // It FEELS like it doesn't work because the NPCs are a bit jumpy, but that only happens when KillAura is active! (because the camera rotates I think)
    private fun generateDirections(player: Player): List<Vector> {
        val clonedLocation = player.location.clone()
        // This is enough to trip the default LegitKillaura config to rotate
        clonedLocation.pitch = -17f

        val direction = clonedLocation.direction.normalize()

        return listOf(
            direction.clone().multiply(-3),
            direction.clone().rotateAroundY(Math.toRadians(58.31)).multiply(-3),
            direction.clone().rotateAroundY(Math.toRadians(-58.31)).multiply(-3)
        )
    }

    fun spawnFakePlayers() {
        val directions = generateDirections(player)

        for ((index, direction) in directions.withIndex()) {
            val targetLocation = player.location.add(direction)

            val id = Entity.nextEntityId()
            val playerUniqueId = UUID.randomUUID()
            val addPlayerPacket = ClientboundAddEntityPacket(
                id,
                playerUniqueId,
                targetLocation.x,
                targetLocation.y,
                targetLocation.z,
                player.location.yaw,
                player.location.pitch,
                EntityType.PLAYER,
                0,
                Vec3.ZERO,
                0.0
            )

            // 0x20 = invisible
            val metadataList = listOf(SynchedEntityData.DataValue(0, EntityDataSerializers.BYTE, 0x20))
            val metadataPacket = ClientboundSetEntityDataPacket(id, metadataList)

            val playerInfoUpdatePacket = ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER),
                listOf(
                    ClientboundPlayerInfoUpdatePacket.Entry(
                        playerUniqueId,
                        GameProfile(playerUniqueId, "Reflections"),
                        false,
                        0,
                        GameType.DEFAULT_MODE,
                        null,
                        false,
                        0,
                        null
                    )
                )
            )

            // The player info update packet MUST come before the add player packet!
            this.player.sendPacket(ClientboundBundlePacket(listOf(playerInfoUpdatePacket, addPlayerPacket, metadataPacket)))

            this.fakePlayersAlive.add(
                KillAuraBait(
                    index,
                    id,
                    playerUniqueId
                )
            )
        }
    }

    fun removeFakePlayerHitByPlayer(fakePlayer: KillAuraBait) {
        val removeEntityPacket = ClientboundRemoveEntitiesPacket(fakePlayer.networkId)
        val removePlayerListPacket = ClientboundPlayerInfoRemovePacket(listOf(fakePlayer.uniqueId))

        this.player.sendPacket(ClientboundBundlePacket(listOf(removeEntityPacket, removePlayerListPacket)))

        this.fakePlayersAlive.remove(fakePlayer)

        this.session.killAura.increaseViolationLevel(20)
    }

    fun removeFakePlayers() {
        for (bait in this.fakePlayersAlive) {
            val removeEntityPacket = ClientboundRemoveEntitiesPacket(bait.networkId)
            val removePlayerListPacket = ClientboundPlayerInfoRemovePacket(listOf(bait.uniqueId))

            this.player.sendPacket(ClientboundBundlePacket(listOf(removeEntityPacket, removePlayerListPacket)))
        }

        this.fakePlayersAlive.clear()
    }

    fun tick() {
        updateFakePlayersLocation()
    }

    fun updateFakePlayersLocation() {
        val directions = generateDirections(player).toMutableList()

        for (bait in this.fakePlayersAlive) {
            val direction = directions[bait.index]
            val targetLocation = player.location.clone().add(direction)

            val packet = ClientboundEntityPositionSyncPacket(bait.networkId, PositionMoveRotation(Vec3(targetLocation.x, targetLocation.y, targetLocation.z), Vec3.ZERO, 0f, 0f), false)

            this.player.sendPacket(packet)
        }
    }

    data class KillAuraBait(val index: Int, val networkId: Int, val uniqueId: UUID)
}