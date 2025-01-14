package net.perfectdreams.dreamcore.utils.npc

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.GameType
import net.perfectdreams.dreamcore.event.PlayerScoreboardCreatedEvent
import net.perfectdreams.dreamcore.utils.extensions.sendPacket
import net.perfectdreams.dreamcore.utils.get
import net.perfectdreams.dreamcore.utils.packetevents.ClientboundPacketSendEvent
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Husk
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SparklyNPCListener(val m: SparklyNPCManager) : Listener {
    companion object {
        private const val PACKET_IDENTIFIER_ID = "sparklynpc"
    }

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        for (entity in event.entities) {
            // Is this an NPC?
            if (entity.persistentDataContainer.get(m.npcKey)) {
                // Is the entity's unique ID present in the NPC Entities list?
                val npcData = m.npcEntities[entity.uniqueId]
                if (npcData == null) {
                    // If not, we are going to delete it!
                    m.m.logger.warning("Deleting entity ${entity.uniqueId} because their ID isn't present in the NPC list!")

                    // Bail out!
                    entity.remove()
                } else {
                    val husk = entity as Husk
                    npcData.updateEntityReference(husk)
                    npcData.updateEntity(husk)
                }
            }
        }
    }

    @EventHandler
    fun onEntitiesLoad(event: EntityRemoveFromWorldEvent) {
        val entity = event.entity

        // Is this an NPC?
        if (entity.persistentDataContainer.get(m.npcKey)) {
            // Is the entity's unique ID present in the NPC Entities list?
            val npcData = m.npcEntities[entity.uniqueId]

            if (npcData != null) {
                // If yes, remove the NPC entity reference!
                npcData.updateEntityReference(null)
            }
        }
    }

    @EventHandler
    fun onDisable(event: PluginDisableEvent) {
        val entitiesToBeDeleted = mutableSetOf<UUID>()

        m.npcEntities.forEach { (id, data) ->
            if (data.owner == event.plugin) {
                // Delete all NPCs when the plugin is disabled
                data.remove()
            }
        }

        entitiesToBeDeleted.forEach {
            m.npcEntities.remove(it)
        }
    }

    @EventHandler
    fun onDeath(event: EntityDeathEvent) {
        val npcData = m.npcEntities[event.entity.uniqueId]
        if (npcData != null) {
            npcData.remove()
            m.npcEntities.remove(npcData.uniqueId)
        }
    }

    private val spawnedNPCEntitiesForPlayers = ConcurrentHashMap<Player, MutableMap<Int, UUID>>()

    private fun getOrCreateMap(player: Player): MutableMap<Int, UUID> {
        return spawnedNPCEntitiesForPlayers.getOrPut(player) { mutableMapOf() }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        spawnedNPCEntitiesForPlayers.remove(e.player)
    }

    @EventHandler
    fun onClientboundPacket(event: ClientboundPacketSendEvent) {
        val packet = event.packet

        if (packet is ClientboundSetEntityDataPacket) {
            val npcUniqueId = getOrCreateMap(event.player)[packet.id]

            // println("NPC data: ${npcData}")
            if (npcUniqueId != null) {
                val npcData = m.npcEntities[npcUniqueId]!!
                m.m.logger.info { "Intercepted packet for entity ${npcUniqueId}! Replacing set entity packets..." }

                event.packet = ClientboundSetEntityDataPacket(packet.id, npcData.createMetadata())
                return
            }
        }

        if (packet is ClientboundBundlePacket) {
            // println("Bundle Packet has...")
            // msg.subPackets().forEach {
            //     println("> ${it::class.java}")
            // }

            val newSubPackets = mutableListOf<Packet<in ClientGamePacketListener>>()
            val vanillaSubPackets = packet.subPackets()

            // ORIGINALLY I WAS GOING TO ADAPT THIS BUNDLE CODE TO BE MORE FLEXIBLE
            // BUT I GOT FED UP WITH DEBUGGING THESE ISSUES
            // SO FOR NOW IT IS WHAT IT IS

            // We need to check if this packet is related to our NPC or nah
            for (subPacket in vanillaSubPackets) {
                // It *seems* that vanilla entities are always sent in a bundle packet
                if (subPacket is ClientboundAddEntityPacket) {
                    if (subPacket.type == EntityType.HUSK) {
                        val npcData = m.npcEntities[subPacket.uuid]

                        // println("NPC data: ${npcData}")
                        if (npcData != null) {
                            // Because Minecraft 1.20.6 changed things (we cannot send invalid metadata data) we need to write the metadata ourselves
                            // So we are 100% rejecting the original packets and writing them ourselves
                            val gp = GameProfile(subPacket.uuid, npcData.fakePlayerName)
                            m.m.logger.info { "Intercepted packet for entity ${subPacket.uuid}! (Game Profile: ${gp.id}) Replacing packets with fake player packets..." }
                            val textures = npcData.textures
                            if (textures != null) {
                                gp.properties.put(
                                    "textures",
                                    Property(
                                        "textures",
                                        textures.value,
                                        textures.signature
                                    )
                                )
                            }

                            // Adds the player to the TAB list (sort of, since the "listed" option is set to false!)
                            newSubPackets.add(
                                ClientboundPlayerInfoUpdatePacket(
                                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER),
                                    ClientboundPlayerInfoUpdatePacket.Entry(
                                        gp.id,
                                        gp,
                                        false,
                                        0,
                                        GameType.DEFAULT_MODE,
                                        null,
                                        true,
                                        0,
                                        null
                                    )
                                )
                            )

                            // Change the spawn type to a player
                            subPacket.type = EntityType.PLAYER

                            // Creates the player itself
                            newSubPackets.add(subPacket)

                            // Sets the skin data (to make all skin layers show up)
                            newSubPackets.add(ClientboundSetEntityDataPacket(subPacket.id, npcData.createMetadata()))

                            // Set the yaw and pitch because, for some reason, player entities do not have the yaw correctly set when they are spawned
                            newSubPackets.add(
                                ClientboundRotateHeadPacket(
                                    subPacket.id,
                                    // This is a bit hacky, since we are accessing the entity's current location
                                    // Yes, the head yaw is controlled by the y rot... I don't know why
                                    subPacket.yRotRaw
                                )
                            )

                            newSubPackets.addAll(vanillaSubPackets.filterIsInstance<ClientboundSetEquipmentPacket>())

                            getOrCreateMap(event.player)[subPacket.id] = subPacket.uuid
                            break // Break because we have already set up all the necessary packets
                        } else {
                            // Bail out, this is just a normal entity...
                            // println("Bailing out, this is just a normal entity...")
                            return
                        }
                    } else {
                        newSubPackets.add(subPacket)
                    }
                } else {
                    newSubPackets.add(subPacket)
                }
            }

            // println("New Bundle...")
            // newSubPackets.forEach {
            //     println("> ${it::class.java}")
            // }

            event.packet = ClientboundBundlePacket(newSubPackets)
            return
        }

        if (packet is ClientboundRemoveEntitiesPacket) {
            for (entityId in packet.entityIds.iterator()) {
                val spawnedNPCEntities = getOrCreateMap(event.player)
                val uuid = spawnedNPCEntities[entityId]

                if (uuid != null) {
                    spawnedNPCEntities.remove(entityId)
                    event.player.sendPacket(ClientboundPlayerInfoRemovePacket(listOf(uuid)))
                }
            }
        }
        return
    }

    /* @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        /* (event.player as CraftPlayer).handle
            .connection
            .connection
            .channel
            .pipeline()
            .forEach {
                println(it.key + " - " + it.value)
            } */

        val nmsPlayer = (event.player as CraftPlayer).handle

        // This is per player! It is an entity ID to Entity UUID reference
        // We don't really *need* to store a SparklyNPC reference (it would be dead btw because we had removed the NPC), we just need this to send
        // a remove player packet and to match other packets
        val spawnedNPCEntities = mutableMapOf<Int, UUID>()

        nmsPlayer
            .connection
            .connection
            .channel
            .pipeline()
            .forEach {
                println(it.key + " - " + it.value)
            }

        // Create Netty pipeline to intercept packets
        nmsPlayer
            .connection
            .connection
            .channel
            .pipeline()
            .addBefore(
                "packet_handler",
                "sparklynpc-handler",
                object: ChannelDuplexHandler() {
                    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
                        // println(msg::class.java)

                        // We need to intercept this because Minecraft 1.20.6 changed how set entity data packets are handled and now throws an error on client-side if the metadata does not
                        // match the entity
                        if (msg is ClientboundSetEntityDataPacket) {
                            val npcUniqueId = spawnedNPCEntities[msg.id]

                            // println("NPC data: ${npcData}")
                            if (npcUniqueId != null) {
                                val npcData = m.npcEntities[npcUniqueId]!!
                                m.m.logger.info { "Intercepted packet for entity ${npcUniqueId}! Replacing set entity packets..." }

                                super.write(
                                    ctx,
                                    ClientboundSetEntityDataPacket(msg.id, npcData.createMetadata()),
                                    promise
                                )
                                return
                            }
                        }

                        if (msg is ClientboundBundlePacket) {
                            // println("Bundle Packet has...")
                            // msg.subPackets().forEach {
                            //     println("> ${it::class.java}")
                            // }

                            val newSubPackets = mutableListOf<Packet<in ClientGamePacketListener>>()

                            // We need to check if this packet is related to our NPC or nah
                            for (subPacket in msg.subPackets()) {
                                // It *seems* that vanilla entities are always sent in a bundle packet
                                if (subPacket is ClientboundAddEntityPacket) {
                                    if (subPacket.type == EntityType.HUSK) {
                                        val npcData = m.npcEntities[subPacket.uuid]

                                        // println("NPC data: ${npcData}")
                                        if (npcData != null) {
                                            // Because Minecraft 1.20.6 changed things (we cannot send invalid metadata data) we need to write the metadata ourselves
                                            // So we are 100% rejecting the original packets and writing them ourselves
                                            val gp = GameProfile(subPacket.uuid, npcData.fakePlayerName)
                                            m.m.logger.info { "Intercepted packet for entity ${subPacket.uuid}! (Game Profile: ${gp.id}) Replacing packets with fake player packets..." }
                                            val textures = npcData.textures
                                            if (textures != null) {
                                                gp.properties.put(
                                                    "textures",
                                                    Property(
                                                        "textures",
                                                        textures.value,
                                                        textures.signature
                                                    )
                                                )
                                            }

                                            // Adds the player to the TAB list (sort of, since the "listed" option is set to false!)
                                            newSubPackets.add(
                                                ClientboundPlayerInfoUpdatePacket(
                                                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER),
                                                    listOf(
                                                        ClientboundPlayerInfoUpdatePacket.Entry(
                                                            gp.id,
                                                            gp,
                                                            false,
                                                            0,
                                                            GameType.DEFAULT_MODE,
                                                            null,
                                                            0,
                                                            null
                                                        )
                                                    )
                                                )
                                            )

                                            // Change the spawn type to a player
                                            subPacket.type = EntityType.PLAYER

                                            // Creates the player itself
                                            newSubPackets.add(subPacket)

                                            // Sets the skin data (to make all skin layers show up)
                                            newSubPackets.add(ClientboundSetEntityDataPacket(subPacket.id, npcData.createMetadata()))

                                            // Set the yaw and pitch because, for some reason, player entities do not have the yaw correctly set when they are spawned
                                            newSubPackets.add(
                                                ClientboundRotateHeadPacket(
                                                    subPacket.id,
                                                    // This is a bit hacky, since we are accessing the entity's current location
                                                    // Yes, the head yaw is controlled by the y rot... I don't know why
                                                    subPacket.yRotRaw
                                                )
                                            )

                                            spawnedNPCEntities[subPacket.id] = subPacket.uuid
                                            break // Break because we have already set up all the necessary packets
                                        } else {
                                            // Bail out, this is just a normal entity...
                                            // println("Bailing out, this is just a normal entity...")
                                            super.write(
                                                ctx,
                                                msg,
                                                promise
                                            )
                                            return
                                        }
                                    } else {
                                        newSubPackets.add(subPacket)
                                    }
                                } else {
                                    newSubPackets.add(subPacket)
                                }
                            }

                            // println("New Bundle...")
                            // newSubPackets.forEach {
                            //     println("> ${it::class.java}")
                            // }

                            super.write(
                                ctx,
                                ClientboundBundlePacket(newSubPackets),
                                promise
                            )
                            return
                        }

                        if (msg is ClientboundRemoveEntitiesPacket) {
                            for (entityId in msg.entityIds.iterator()) {
                                val uuid = spawnedNPCEntities[entityId]

                                if (uuid != null) {
                                    spawnedNPCEntities.remove(entityId)
                                    nmsPlayer.connection.connection.send(ClientboundPlayerInfoRemovePacket(listOf(uuid)))
                                }
                            }
                        }

                        super.write(ctx, msg, promise)
                    }
                }
            )
    } */

    /* private val spawnedNPCEntitiesForPlayers = WeakHashMap<Player, MutableMap<Int, UUID>>()

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        spawnedNPCEntitiesForPlayers.remove(e.player)
    }

    @EventHandler
    fun onClientboundPacket(e: ClientboundPacketSendEvent) {
        // println(msg::class.java)
        val packet = e.packet

        // If it is a SparklyNPC packet, then ignore
        if (e.identifier == "sparklynpc")
            return

        val spawnedNPCEntities = spawnedNPCEntitiesForPlayers.getOrPut(e.player) { mutableMapOf() }
        // We need to intercept this because Minecraft 1.20.6 changed how set entity data packets are handled and now throws an error on client-side if the metadata does not
        // match the entity
        if (packet is ClientboundSetEntityDataPacket) {
            val npcUniqueId = spawnedNPCEntities[packet.id]

            // println("NPC data: ${npcData}")
            if (npcUniqueId != null) {
                val npcData = m.npcEntities[npcUniqueId]!!
                m.m.logger.info { "Intercepted packet for entity ${npcUniqueId}! Replacing set entity packets..." }

                e.isCancelled = true
                e.sendPacketWithIdentifier(PACKET_IDENTIFIER_ID, ClientboundSetEntityDataPacket(packet.id, npcData.createMetadata()))
                return
            }
        }

        if (packet is ClientboundBundlePacket) {
            // println("Bundle Packet has...")
            // msg.subPackets().forEach {
            //     println("> ${it::class.java}")
            // }

            val vanillaSubPackets = packet.subPackets()
            val rewrittenSubPackets = mutableListOf<Packet<in ClientGamePacketListener>>()

            // We WANT TO keep ALL (or most of them) packets of the bundle within the bundle
            // Because this may cause issues with equipment packets not being """sent""" when in reality it is because we were discarding them from the bundle packet
            // We need to check if this packet is related to our NPC or nah
            for (subPacket in vanillaSubPackets) {
                // It *seems* that vanilla entities are always sent in a bundle packet
                if (subPacket is ClientboundSetEntityDataPacket) {
                    // Because Minecraft 1.20.6 changed things (we cannot send invalid metadata data) we need to write the metadata ourselves
                    val npcUniqueId = spawnedNPCEntities[subPacket.id]
                    if (npcUniqueId != null) {
                        val npcData = m.npcEntities[npcUniqueId]

                        if (npcData != null) {
                            // Sets the skin data (to make all skin layers show up)
                            rewrittenSubPackets.add(
                                ClientboundSetEntityDataPacket(
                                    subPacket.id,
                                    npcData.createMetadata()
                                )
                            )
                            continue
                        }
                    }
                }

                if (subPacket is ClientboundAddEntityPacket) {
                    if (subPacket.type == EntityType.HUSK) {
                        val npcData = m.npcEntities[subPacket.uuid]

                        // println("NPC data: ${npcData}")
                        if (npcData != null) {
                            Bukkit.broadcastMessage("Spawning NPC ${subPacket.uuid}")
                            Bukkit.broadcastMessage("Vanilla sub packets: ${vanillaSubPackets.map { it::class.simpleName }}")
                            // Because Minecraft 1.20.6 changed things (we cannot send invalid metadata data) we need to write the metadata ourselves
                            // So we are 100% rejecting the original packets and writing them ourselves
                            val gp = GameProfile(subPacket.uuid, npcData.fakePlayerName)
                            m.m.logger.info { "Intercepted packet for entity ${subPacket.uuid}! (Game Profile: ${gp.id}) Replacing packets with fake player packets..." }
                            val textures = npcData.textures
                            if (textures != null) {
                                gp.properties.put(
                                    "textures",
                                    Property(
                                        "textures",
                                        textures.value,
                                        textures.signature
                                    )
                                )
                            }

                            // Adds the player to the TAB list (sort of, since the "listed" option is set to false!)
                            rewrittenSubPackets.add(
                                ClientboundPlayerInfoUpdatePacket(
                                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER),
                                    listOf(
                                        ClientboundPlayerInfoUpdatePacket.Entry(
                                            gp.id,
                                            gp,
                                            false,
                                            0,
                                            GameType.DEFAULT_MODE,
                                            null,
                                            0,
                                            null
                                        )
                                    )
                                )
                            )

                            // Change the spawn type to a player
                            subPacket.type = EntityType.PLAYER

                            // Creates the player itself
                            rewrittenSubPackets.add(subPacket)

                            // Set the yaw and pitch because, for some reason, player entities do not have the yaw correctly set when they are spawned
                            // TODO: This actually doesn't seem to be required..?! It is working fine without this
                            rewrittenSubPackets.add(
                                ClientboundRotateHeadPacket(
                                    subPacket.id,
                                    // This is a bit hacky, since we are accessing the entity's current location
                                    // Yes, the head yaw is controlled by the y rot... I don't know why
                                    subPacket.yRotRaw
                                )
                            )

                            spawnedNPCEntities[subPacket.id] = subPacket.uuid
                            break
                        }
                    }
                }

                // Add as is, this is just a normal entity...
                rewrittenSubPackets.add(subPacket)
            }

            if (!vanillaSubPackets.equals(rewrittenSubPackets)) {
                e.isCancelled = true
                e.sendPacketWithIdentifier(PACKET_IDENTIFIER_ID, ClientboundBundlePacket(rewrittenSubPackets))
            }
            return
        }

        if (packet is ClientboundRemoveEntitiesPacket) {
            for (entityId in packet.entityIds.iterator()) {
                val uuid = spawnedNPCEntities[entityId]

                if (uuid != null) {
                    spawnedNPCEntities.remove(entityId)
                    e.sendPacketWithIdentifier(PACKET_IDENTIFIER_ID, ClientboundPlayerInfoRemovePacket(listOf(uuid)))
                }
            }
        }
    } */

    @EventHandler
    fun onScoreboardCreation(event: PlayerScoreboardCreatedEvent) {
        // Update currently spawned NPC names
        for ((_, npc) in m.npcEntities) {
            m.updateFakePlayerName(event.phoenixScoreboard.scoreboard, npc)
        }
    }

    @EventHandler(ignoreCancelled = false)
    fun onClick(e: PlayerInteractEntityEvent) {
        m.m.logger.info { "PlayerInteractEntityEvent entity ${e.rightClicked.uniqueId}" }
        val npcData = m.npcEntities[e.rightClicked.uniqueId]
        m.m.logger.info { "Intercepted NPC Data: $npcData" }
        if (npcData != null) {
            e.isCancelled = true
            if (e.hand == EquipmentSlot.HAND) {
                npcData.onRightClickCallback?.invoke(e.player)
            }
        }
    }

    @EventHandler(ignoreCancelled = false)
    fun onClick(e: PrePlayerAttackEntityEvent) {
        m.m.logger.info { "PrePlayerAttackEntityEvent entity ${e.attacked.uniqueId}" }
        val npcData = m.npcEntities[e.attacked.uniqueId]
        m.m.logger.info { "Intercepted NPC Data: $npcData" }
        if (npcData != null) {
            e.isCancelled = true
            npcData.onLeftClickCallback?.invoke(e.player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onSpawn(e: CreatureSpawnEvent) {
        if (m.forceSpawn) {
            e.isCancelled = false
        }
    }
}