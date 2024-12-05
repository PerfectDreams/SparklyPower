package net.perfectdreams.dreamcore.utils.npc

import net.kyori.adventure.text.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.SparklyNamespacedBooleanKey
import net.perfectdreams.dreamcore.utils.get
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamcore.utils.set
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.entity.Husk
import org.bukkit.entity.Player
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scoreboard.Scoreboard
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.sqrt

// Based off https://github.com/PaperMC/Paper/issues/9487
class SparklyNPCManager(val m: DreamCore) {
    companion object {
        fun getTeamName(uuid: UUID) = "SparklyNPC-$uuid"
        private val chatColors = ChatColor.values()
    }

    val npcKey = SparklyNamespacedBooleanKey("npc")
    val npcEntities = ConcurrentHashMap<UUID, SparklyNPC>()
    internal var forceSpawn = false

    fun start() {
        // Register NPC related listeners
        m.registerEvents(SparklyNPCListener(this))

        // Create look close and remove outdated teams
        m.launchMainThread {
            // This is the same code as the one in the SparklyNPCListener EntitiesLoadEvent listener
            // Why it is here? Because entities may have been loaded BEFORE DreamCore has started
            for (world in Bukkit.getWorlds()) {
                for (entity in world.entities) {
                    // Is this an NPC?
                    if (entity.persistentDataContainer.get(npcKey)) {
                        // Is the entity's unique ID present in the NPC Entities list?
                        if (!npcEntities.containsKey(entity.uniqueId)) {
                            // If not, we are going to delete it!
                            m.logger.warning("Deleting entity ${entity.uniqueId} because their ID isn't present in the NPC list!")

                            // Bail out!
                            entity.remove()
                        }
                    }
                }
            }

            while (true) {
                // logger.info { "Checking look close NPCs..." }
                for ((id, sparklyNPCData) in npcEntities) {
                    if (sparklyNPCData.lookClose) {
                        // logger.info { "Checking ${sparklyNPCData.entity}..." }

                        val entity = sparklyNPCData.getEntity() ?: continue // May be null if the entity is not loaded
                        val nearbyEntities = entity.getNearbyEntities(16.0, 16.0, 16.0)
                        val nearestPlayer = nearbyEntities
                            .filterIsInstance<Player>()
                            .minByOrNull { it.location.distanceSquared(entity.location) }

                        // logger.info { "Nearest player is: ${nearestPlayer}" }
                        if (nearestPlayer != null) {
                            val directionX = nearestPlayer.x - entity.location.x
                            val directionY = nearestPlayer.y - entity.location.y
                            val directionZ = nearestPlayer.z - entity.location.z

                            // Calculate yaw (horizontal angle)
                            val yaw = Math.toDegrees(atan2(-directionX, directionZ))

                            // Calculate pitch (vertical angle)
                            val horizontalDistance = sqrt(directionX * directionX + directionZ * directionZ)
                            val pitch = Math.toDegrees(atan2(-directionY, horizontalDistance))

                            // logger.info { "Yaw: $yaw" }
                            // logger.info { "Pitch: $pitch" }

                            entity.setRotation(yaw.toFloat(), pitch.toFloat())
                        } else {
                            entity.setRotation(sparklyNPCData.initialLocation.yaw, sparklyNPCData.initialLocation.pitch)
                        }
                    }
                }

                delayTicks(10)
            }
        }
    }

    fun updateFakePlayerName(npc: SparklyNPC) {
        // Set the NPC name to the player's scoreboard
        for (player in Bukkit.getOnlinePlayers()) {
            val phoenixScoreboard = m.scoreboardManager.getScoreboard(player) ?: continue
            updateFakePlayerName(phoenixScoreboard.scoreboard, npc)
        }
    }

    fun updateFakePlayerName(scoreboard: Scoreboard, npc: SparklyNPC) {
        // Set the NPC name to the player's scoreboard
        npc.updateName(scoreboard)
    }

    fun deleteFakePlayerName(npc: SparklyNPC) {
        val teamName = getTeamName(npc.uniqueId)
        m.logger.info { "Removing team $teamName because the NPC was removed" }

        for (player in Bukkit.getOnlinePlayers()) {
            // Set the NPC name to the player's scoreboard
            val phoenixScoreboard = m.scoreboardManager.getScoreboard(player) ?: continue
            phoenixScoreboard.scoreboard.getTeam(teamName)?.unregister()
        }
    }

    fun spawnFakePlayer(
        owner: Plugin,
        location: Location,
        name: String,
        skinTextures: SkinTexture? = null,
        preSpawnSettings: (entity: Husk, npc: SparklyNPC) -> (Unit) = { _, _ -> }
    ): SparklyNPC {
        // If we create entities using this, we can get the UUID before it is added to the world
        val entity = location.world.createEntity(
            location,
            Husk::class.java
        )

        var fakePlayerName: String
        while (true) {
            val fakePlayerNameColors = (0 until 8).map { chatColors.random().toString() }
            fakePlayerName = fakePlayerNameColors.joinToString("")
            val isAlreadyBeingUsed = npcEntities.any { it.value.fakePlayerName == fakePlayerName }
            if (!isAlreadyBeingUsed)
                break
        }

        entity.customName(Component.text("SparklyNPC"))
        val npcData = SparklyNPC(
            this,
            owner,
            name,
            fakePlayerName,
            location,
            skinTextures,
            entity.uniqueId
        )

        npcData.updateEntityReference(entity)

        preSpawnSettings.invoke(entity, npcData)

        npcData.updateEntity(entity)

        updateFakePlayerName(npcData)

        // We need to store the NPC data BEFORE we spawn them, to avoid the packet interceptor not intercepting the packets due to the NPC data being missing
        m.logger.info { "Created NPC ${entity.uniqueId} with name \"$name\"" }
        npcEntities[entity.uniqueId] = npcData

        // println("NPC data has been set! ${fakePlayer.uuid}")

        entity.removeWhenFarAway = false
        entity.setAI(false)
        entity.isSilent = true
        entity.isInvulnerable = true

        entity.persistentDataContainer.set(
            npcKey,
            true
        )

        forceSpawn = true
        entity.spawnAt(location, CreatureSpawnEvent.SpawnReason.CUSTOM)
        forceSpawn = false

        m.logger.info { "NPC was added to the world, they have UUID ${entity.uniqueId} with name \"$name\"" }

        return npcData
    }
}