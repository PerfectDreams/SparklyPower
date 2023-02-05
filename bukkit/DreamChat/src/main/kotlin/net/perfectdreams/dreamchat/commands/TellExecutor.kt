package net.perfectdreams.dreamchat.commands

import net.perfectdreams.dreamchat.DreamChat
import net.perfectdreams.dreamchat.utils.ChatUtils
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.extensions.artigo
import net.perfectdreams.dreamcore.utils.extensions.isStaff
import net.perfectdreams.dreamcore.utils.preferences.BroadcastType
import net.perfectdreams.dreamcore.utils.preferences.shouldSeeBroadcast
import net.perfectdreams.dreamvanish.DreamVanishAPI

class TellExecutor(val m: DreamChat) : SparklyCommandExecutor() {
    inner class Options : CommandOptions() {
            val receiver = player("receiver")
            val message = greedyString("message")
        }

        override val options = Options()

    override fun execute(context: CommandContext, args: CommandArguments) {
        val sender = context.requirePlayer()
        val receiver = args.getAndValidate(options.receiver)
        val message = args[options.message]

        if (!receiver.shouldSeeBroadcast(BroadcastType.PRIVATE_MESSAGE) && !sender.isStaff)
            context.fail("§c${receiver.name} desativou mensagens privadas nas preferências.")

        if (DreamVanishAPI.isQueroTrabalhar(receiver)) {
            receiver.sendMessage("§c${sender.displayName}§c tentou te enviar §e${message}§c!")
            context.fail(CommandArguments.PLAYER_NOT_FOUND.invoke())
        }

        if (sender == receiver)
            context.fail("§cVocê não pode enviar uma mensagem para você mesmo, bobinh${sender.artigo}!")

        // TODO: Fix this later
        if (message == null) {
            // If the message is null, then the user wants to lock a tell with someone!
            m.lockedTells[sender] = receiver.name
            sender.sendMessage("§aSeu chat foi travado com ${receiver.artigo} §b${receiver.displayName}§a! Agora você pode enviar mensagens no chat e elas irão ir para a caixa privada d${receiver.artigo} §b${receiver.displayName}§a!")
            sender.sendMessage("§7Para desativar, use §6/tell lock")
            return
        }

        ChatUtils.sendTell(sender, receiver, message)
    }
}
