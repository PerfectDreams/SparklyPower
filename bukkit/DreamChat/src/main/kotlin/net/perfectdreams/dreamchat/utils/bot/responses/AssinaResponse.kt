package net.perfectdreams.dreamchat.utils.bot.responses

import net.citizensnpcs.api.CitizensAPI
import net.perfectdreams.dreamchat.DreamChat
import net.perfectdreams.dreamchat.utils.ChatUtils
import net.perfectdreams.dreamcore.utils.BlockUtils
import net.perfectdreams.dreamcore.utils.LocationUtils
import net.perfectdreams.dreamcore.utils.extensions.canPlaceAt
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.regex.Pattern
import net.citizensnpcs.trait.SkinTrait;
import net.perfectdreams.dreamcore.utils.FaceUtils
import org.bukkit.Location
import org.bukkit.block.data.type.WallSign

abstract class AssinaResponse(
	val name: String,
	val color: ChatColor,
	val skinName: String,
	val texture: String,
	val signature: String,
	private val messageSendFunction: (Player, String) -> (Unit)
) : RegExResponse() {
	init {
		patterns.add(name.toPattern(Pattern.CASE_INSENSITIVE))
		patterns.add("assina".toPattern(Pattern.CASE_INSENSITIVE))
		patterns.add("minha|meu".toPattern(Pattern.CASE_INSENSITIVE))
		patterns.add("casa|prédio|apartamento|home|castelo".toPattern(Pattern.CASE_INSENSITIVE))
	}

	override fun getResponse(message: String, event: AsyncPlayerChatEvent): String? {
		val player = event.player

		val targetSignMaterial = hasSign(player)

		if (targetSignMaterial != null) {
			// Verificar se o player pode construir no target
			DreamChat.INSTANCE.launchMainThread {
				val lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null as Set<Material>?, 10)
				val airBlockBeforeTheTargetBlock = lastTwoTargetBlocks.getOrNull(0) ?: return@launchMainThread
				val targetBlock = lastTwoTargetBlocks.getOrNull(1) ?: return@launchMainThread

				val playerCurrentLocation = player.location

				var signBlock: Block? = null
				val npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name)
				DreamChat.INSTANCE.assinaturaCitizensNpcs.add(npc)
				val trait = npc.getOrAddTrait(SkinTrait::class.java)
				trait.setSkinPersistent(skinName, signature, texture)
				npc.spawn(playerCurrentLocation)

				fun removeNPC() {
					CitizensAPI.getNPCRegistry().deregister(npc)
					DreamChat.INSTANCE.assinaturaCitizensNpcs.remove(npc)
				}

				delayTicks(20L)

				if (targetBlock.type != Material.AIR) { // Olhando para um bloco sólido...
					if (targetBlock.getRelative(BlockFace.UP).type == Material.AIR)	{ // E o bloco acima é ar!
						signBlock = targetBlock.getRelative(BlockFace.UP)

						if (!event.player.canPlaceAt(signBlock.location, Material.OAK_SIGN)) {
							removeNPC()
							messageSendFunction.invoke(player, "§b${player.displayName}§a, eu não consegui colocar uma placa aonde você está...")
							return@launchMainThread
						}

						signBlock.type = targetSignMaterial
						val face = LocationUtils.yawToFace((playerCurrentLocation.yaw + 90) % 360, true).oppositeFace
						val blockData = signBlock.blockData as org.bukkit.block.data.type.Sign
						blockData.rotation = face
						signBlock.blockData = blockData
					} else { // Se o de cima não for ar, então o usuário quer assinar em uma parede!
						if (!event.player.canPlaceAt(airBlockBeforeTheTargetBlock.location, targetSignMaterial))  {
							removeNPC()
							messageSendFunction.invoke(player, "§b${player.displayName}§a, eu não consegui colocar uma placa aonde você está...")
							return@launchMainThread
						}

						val facePriority = LocationUtils.yawToFace((playerCurrentLocation.yaw + 90) % 360, false)
						signBlock = attachWallSignAt(player, airBlockBeforeTheTargetBlock.location, Material.valueOf(targetSignMaterial.name.replace("_SIGN", "_WALL_SIGN")), facePriority)
					}
				}

				if (signBlock == null || !event.player.canPlaceAt(signBlock.location, Material.OAK_SIGN)) {
					removeNPC()
					messageSendFunction.invoke(player, "§b${player.displayName}§a, eu não consegui colocar uma placa aonde você está...")
					return@launchMainThread
				}

				removeSignOfType(player, targetSignMaterial)

				delayTicks(40)

				val sign = signBlock.state as Sign
				for ((index, line) in getSignLines().withIndex()) {
					sign.setLine(index, line)
				}
				sign.update()
				messageSendFunction.invoke(player, getSuccessResponse(player))

				delayTicks(20)

				CitizensAPI.getNPCRegistry().deregister(npc)
				DreamChat.INSTANCE.assinaturaCitizensNpcs.remove(npc)
			}
			return null
		} else {
			return if (message.contains(name, true)) {
				"§b${player.displayName}§a, eu assino para você se você tiver uma placa no inventário!"
			} else {
				"§b${player.displayName}§a, infelizmente, a Staff sempre está ocupada, mas, se você quiser, eu posso assinar para você caso você tenha uma placa no inventário!"
			}
		}
	}

	open fun getSuccessResponse(player: Player) = "§b${player.displayName}§a, pronto! §dʕ•ᴥ•ʔ"

	open fun getSignLines() = listOf(
		"§3§m---------",
		"§6✪$color$name§6✪",
		"§4aprova! ʕ•ᴥ•ʔ",
		"§3§m---------"
	)

	private fun hasSign(player: Player): Material? {
		player.inventory.forEach {
			if (it != null && it.type.name.endsWith("_SIGN") && !it.type.name.contains("HANGING")) {
				return it.type
			}
		}
		return null
	}

	private fun removeSignOfType(player: Player, type: Material) {
		player.inventory.forEach {
			if (it != null && it.type == type) {
				it.amount -= 1
				return
			}
		}
	}

	private val TRANSPARENT = setOf(
		Material.AIR,
		Material.CAVE_AIR,
		Material.VOID_AIR
	)

	private val BLOCKED_ATTACHMENTS = setOf(
		Material.CAVE_AIR,
		Material.VOID_AIR,
		Material.AIR,
		Material.CAKE,
		Material.COBWEB,
		Material.WATER,
		Material.LAVA
	) + Material.values().filter { it.name.endsWith("_SIGN") }

	private fun attachWallSignAt(player: Player, l: Location, wallSignType: Material, facePriority: BlockFace): Block? {
		val block = l.block

		if (block.type !in TRANSPARENT) // Can't place here!
			return null

		// Check any block around the location
		val faces = mutableListOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)

		// We want to prioritize the facePriority face first
		faces.remove(facePriority)
		faces.add(0, facePriority)

		for (face in faces) {
			val relativeBlock = block.getRelative(face)
			if (relativeBlock.type !in BLOCKED_ATTACHMENTS) {
				// Can be attached here!
				block.type = wallSignType
				val signState = block.state as Sign
				signState.blockData = (signState.blockData as WallSign)
					.apply {
						// Now we sign the sign to be facing the opposite direction
						this.facing = face.oppositeFace
					}
				signState.update()
				return block
			}
		}

		return null
	}
}