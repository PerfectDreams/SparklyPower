package net.perfectdreams.dreamholograms.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.serializable.SerializedWorldLocation
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamholograms.DreamHolograms
import net.perfectdreams.dreamholograms.data.HologramLine
import net.perfectdreams.dreamholograms.data.StoredHologram

class DreamHologramsTeleportExecutor(val m: DreamHolograms)  : SparklyCommandExecutor() {
    inner class Options : CommandOptions() {
        val hologramName = word("hologram_name", m.hologramNameAutocomplete)
    }

    override val options = Options()

    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()

        val hologramName = args[options.hologramName]
        val hologram = m.holograms[hologramName]
        if (hologram == null) {
            DreamHologramsCommand.sendHologramDoesNotExist(context, hologramName)
            return
        }

        player.teleport(hologram.data.location.toLocation())

        context.sendMessage {
            color(NamedTextColor.GREEN)
            content("Você se teletransportou para o holograma!")
        }
    }
}