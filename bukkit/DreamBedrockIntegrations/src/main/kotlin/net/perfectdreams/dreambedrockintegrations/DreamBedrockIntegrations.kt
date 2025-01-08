package net.perfectdreams.dreambedrockintegrations

import com.comphenix.protocol.ProtocolLibrary
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.perfectdreams.dreambedrockintegrations.fakegate.FakegateForm
import net.perfectdreams.dreambedrockintegrations.packetlisteners.BedrockPacketListener
import net.perfectdreams.dreambedrockintegrations.utils.isBedrockClient
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.DefaultFontInfo
import net.perfectdreams.dreamcore.utils.DreamMenu
import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.sparklypower.rpc.proxy.ProxyGeyserStatusRequest
import net.sparklypower.rpc.proxy.ProxyGeyserStatusResponse
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener
import org.geysermc.cumulus.component.ButtonComponent
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.impl.FormImpl
import org.geysermc.cumulus.form.impl.custom.CustomFormImpl
import org.geysermc.cumulus.form.impl.modal.ModalFormImpl
import org.geysermc.cumulus.form.impl.simple.SimpleFormImpl
import org.geysermc.cumulus.response.FormResponse
import org.geysermc.cumulus.response.SimpleFormResponse
import org.geysermc.cumulus.response.result.ClosedFormResponseResult
import org.geysermc.cumulus.response.result.FormResponseResult
import org.geysermc.cumulus.response.result.ResultType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

// Fakegate - Using Floodgate features without actually using Floodgate
// There are some Floodgate features that we want to use, but we don't want to use Floodgate's authentication, and
// Floodgate does not work on SparklyVelocity (due to the multiple listeners feature)
class DreamBedrockIntegrations : KotlinPlugin(), Listener, PluginMessageListener {
	private val ncpHook = Hook(this)
	val geyserUsers = Collections.synchronizedSet(
		mutableSetOf<UUID>()
	)
	val inventoryTitleTransformers = mutableListOf<InventoryTitleTransformer>()
	private val fakegatePlayerForms = ConcurrentHashMap<Player, FakegateForm>()

	override fun softEnable() {
		super.softEnable()

		// Disabled because we also want to block Bedrock cheats
		// NCPHookManager.addHook(CheckType.ALL, ncpHook)

		val protocolManager = ProtocolLibrary.getProtocolManager()
		protocolManager.addPacketListener(BedrockPacketListener(this))

		registerEvents(this)

		this.server.messenger.registerOutgoingPluginChannel(this, FakegateForm.getIdentifier())
		this.server.messenger.registerIncomingPluginChannel(this, FakegateForm.getIdentifier(), this)
	}

	override fun softDisable() {
		super.softDisable()

		// NCPHookManager.removeHook(ncpHook)
	}

	fun registerInventoryTitleTransformer(
		plugin: Plugin,
		matchInventory: (Component) -> (Boolean),
		newInventoryName: (Component) -> (Component)
	) {
		inventoryTitleTransformers.add(
			InventoryTitleTransformer(
				plugin,
				matchInventory,
				newInventoryName
			)
		)
	}

	fun sendSimpleForm(
		player: Player,
		form: SimpleForm
	) {
		if (!player.isBedrockClient)
			error("Player is not using Minecraft: Bedrock Edition!")

		val fakegateForm = fakegatePlayerForms.getOrPut(player) { FakegateForm(this) }
		fakegateForm.sendForm(
			player,
			form
		)
	}

	@EventHandler
	fun onDisable(event: PluginDisableEvent) {
		inventoryTitleTransformers.removeIf { it.plugin == event.plugin }
	}

	@EventHandler(priority = EventPriority.LOWEST)
	fun onAsyncPlayerPreLoginEvent(event: AsyncPlayerPreLoginEvent) {
		// Check if the connection is Geyser, attempt to bootstrap the connection
		runBlocking {
			try {
				withTimeout(5_000) {
					val response = DreamCore.INSTANCE.rpc.proxy.makeRPCRequest<ProxyGeyserStatusResponse>(
						ProxyGeyserStatusRequest(event.uniqueId)
					)
					logger.info("Geyser Connection Check for ${event.name} (${event.uniqueId}) is $response")

					when (response) {
						is ProxyGeyserStatusResponse.Success -> {
							if (response.isGeyser)
								geyserUsers.add(event.uniqueId)
							else
								geyserUsers.remove(event.uniqueId)
						}
						ProxyGeyserStatusResponse.UnknownPlayer -> {
							event.disallow(
								AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
								textComponent {
									color(NamedTextColor.RED)
									content("Algo deu errado ao tentar realizar o seu login!")
								}
							)
						}
					}
				}
			} catch (e: Exception) {
				logger.log(Level.WARNING, e) { "Something went wrong while trying to attempt connection bootstrap!" }
				event.disallow(
					AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
					textComponent {
						color(NamedTextColor.RED)
						content("Algo deu errado ao tentar realizar o seu login!")
					}
				)
			}
		}
	}

	// This is a fail-safe to avoid any forms causing issues to us
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	fun onTeleport(event: PlayerTeleportEvent) {
		val fakegateForm = fakegatePlayerForms[event.player]
		// Pretend that the player closed any pending forms when teleporting
		if (fakegateForm != null) {
			for ((id, form) in fakegateForm.storedForms) {
				logger.info("Cancelling pending form $form because ${event.player} was teleported")
				if (form is SimpleFormImpl) {
					form.callResultHandler(FormResponseResult.closed())
				} else if (form is CustomFormImpl) {
					form.callResultHandler(FormResponseResult.closed())
				} else if (form is ModalFormImpl) {
					form.callResultHandler(FormResponseResult.closed())
				}
			}
		}
		fakegateForm?.storedForms?.clear()
	}

	@EventHandler
	fun onDisable(event: PlayerQuitEvent) {
		geyserUsers.remove(event.player.uniqueId)
		val fakegateForm = fakegatePlayerForms[event.player]
		// Pretend that the player closed any pending forms when quitting the server
		if (fakegateForm != null) {
			for ((id, form) in fakegateForm.storedForms) {
				logger.info("Cancelling pending form $form because ${event.player} left the server")
				if (form is SimpleFormImpl) {
					form.callResultHandler(FormResponseResult.closed())
				} else if (form is CustomFormImpl) {
					form.callResultHandler(FormResponseResult.closed())
				} else if (form is ModalFormImpl) {
					form.callResultHandler(FormResponseResult.closed())
				}
			}
		}
		fakegateForm?.storedForms?.clear()
		fakegatePlayerForms.remove(event.player, fakegateForm)
	}

	data class InventoryTitleTransformer(
		val plugin: Plugin,
		val matchInventory: (Component) -> (Boolean),
		val newInventoryName: (Component) -> (Component)
	)

	override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
		if (channel == FakegateForm.getIdentifier()) {
			val fakegateForm = fakegatePlayerForms[player]
			if (fakegateForm != null) {
				fakegateForm.handleServerCall(message, player.uniqueId, player.name)
			} else {
				logger.warning("Received a floodgate:form plugin message but the player does not have a FakegateForm instance associated with it! Bug?")
			}
		}
	}
}