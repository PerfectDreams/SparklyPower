package net.perfectdreams.dreamcustomitems.commands

import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.commands.options.buildSuggestionsBlockFromList
import net.perfectdreams.dreamcustomitems.utils.CustomItems
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.reflect.KType
import kotlin.reflect.full.createType

class CustomItemsGiveExecutor : SparklyCommandExecutor() {
    inner class Options : CommandOptions() {
        val itemName = word("item_name", buildSuggestionsBlockFromList {
            CustomItems::class.members.filter {
                it.returnType == ItemStack::class.createType()
            }.map { it.name }
        })
    }

    override val options = Options()

    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()

        val itemName = args[options.itemName]

        val name = itemName.toUpperCase()

        val invoke = CustomItems::class.members.first { it.name == name }.call(CustomItems) as ItemStack

        player.inventory.addItem(
            invoke.clone()
        )
        player.sendMessage("§aProntinho!")
    }
}