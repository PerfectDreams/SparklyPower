package net.perfectdreams.dreamemotes.commands

import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamemotes.DreamEmotes

class DreamEmotesCommand(val m: DreamEmotes) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("dreamemotes")) {
        subcommand(listOf("reload")) {
            permission = "dreamemotes.setup"

            executor = ReloadExecutor(m)
        }
    }

    class ReloadExecutor(val m: DreamEmotes) : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            m.reload()
        }
    }
}