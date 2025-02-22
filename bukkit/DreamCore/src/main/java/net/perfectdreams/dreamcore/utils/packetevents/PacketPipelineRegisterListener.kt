package net.perfectdreams.dreamcore.utils.packetevents

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import net.minecraft.network.protocol.Packet
import net.perfectdreams.dreamcore.DreamCore
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.logging.Level

class PacketPipelineRegisterListener(val m: DreamCore) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val nmsPlayer = (player as CraftPlayer).handle

        val packetWithIdentifiers = mutableMapOf<Packet<*>, String>()

        // Create Netty pipeline to intercept packets
        nmsPlayer
            .connection
            .connection
            .channel
            .pipeline()
            .addBefore(
                "packet_handler",
                "dreamcore-packet-events",
                object: ChannelDuplexHandler() {
                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                        val packetSendEvent = ServerboundPacketReceiveEvent(player, msg)

                        try {
                            val isNotCancelled = packetSendEvent.callEvent()

                            if (!isNotCancelled)
                                return

                            super.channelRead(ctx, packetSendEvent.packet)
                        } catch (e: Exception) {
                            m.logger.log(Level.WARNING, e) { "Something went wrong while processing events for $msg" }
                            throw e
                        }
                    }

                    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
                        val packetSendEvent = ClientboundPacketSendEvent(player, msg, packetWithIdentifiers[msg], packetWithIdentifiers)

                        try {
                            val isNotCancelled = packetSendEvent.callEvent()

                            if (!isNotCancelled)
                                return

                            super.write(ctx, packetSendEvent.packet, promise)
                        } catch (e: Exception) {
                            m.logger.log(Level.WARNING, e) { "Something went wrong while processing events for $msg" }
                            throw e
                        } finally {
                            // Remove identifier
                            packetWithIdentifiers.remove(msg)
                        }
                    }
                }
            )
    }
}