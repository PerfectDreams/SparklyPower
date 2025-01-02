package net.perfectdreams.dreammini.commands

import com.destroystokyo.paper.profile.ProfileProperty
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.commands.annotation.Subcommand
import net.perfectdreams.commands.bukkit.SparklyCommand
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclaration
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.generateCommandInfo
import net.perfectdreams.dreammini.DreamMini
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.*

class SkullCommand(val m: DreamMini) : SparklyCommandDeclarationWrapper {
	override fun declaration() = sparklyCommand(listOf("skull", "cabeça", "head")) {
		permission = "dreammini.skull"

		executor = HeadExecutor(m)
	}

	class HeadExecutor(val m: DreamMini) : SparklyCommandExecutor() {
		inner class Options : CommandOptions() {
			val name = word("name")
			val autoUpdate = boolean("auto_update")
		}

		override val options = Options()

		override fun execute(context: CommandContext, args: CommandArguments) {
			val sender = context.requirePlayer()
			val item = sender.inventory.itemInMainHand
			val type = item.type
			val owner = args[options.name]
			val autoUpdate = args[options.autoUpdate]

			if (type == Material.PLAYER_HEAD) {
				m.launchAsyncThread {
					sender.sendMessage(
						textComponent {
							color(NamedTextColor.YELLOW)
							content("Pegando skin de $owner...")
						}
					)

					// É necessário "clonar" o item, se não for "clonado", não será possível usar "meta.owner" caso a skull já tenha
					// um owner anterior
					val accountInfo = DreamCore.INSTANCE.skinUtils.retrieveMojangAccountInfo(owner)

					if (accountInfo == null) {
						sender.sendMessage(
							textComponent {
								color(NamedTextColor.RED)
								content("Conta de $owner não encontrada!")
							}
						)
						return@launchAsyncThread
					}

					val profileProperty = ProfileProperty(
						"textures",
						accountInfo.textures.raw.value,
						accountInfo.textures.raw.signature,
					)

					val meta = item.itemMeta as SkullMeta
					val profile = if (autoUpdate) {
						Bukkit.createProfile(UUID.fromString(accountInfo.uuid), accountInfo.username)
					} else {
						Bukkit.createProfile(UUID(0L, 0L), "")
					}

					profile.setProperty(profileProperty)

					meta.playerProfile = profile
					item.itemMeta = meta

					sender.sendMessage("§aAgora a skin da cabeça é a skin do §b${owner}§a na Mojang!")
				}
			} else {
				sender.sendMessage("§cSegure uma cabeça de um player na sua mão antes de usar!")
			}
		}
	}
}