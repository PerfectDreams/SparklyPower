package net.perfectdreams.pantufa.listeners

import com.github.salomonbrys.kotson.jsonObject
import dev.minn.jda.ktx.messages.MessageCreate
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.sticker.Sticker
import net.dv8tion.jda.api.events.guild.GuildBanEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import net.perfectdreams.pantufa.PantufaBot
import net.perfectdreams.pantufa.api.commands.styled
import net.perfectdreams.pantufa.dao.User
import net.perfectdreams.pantufa.network.Databases
import net.perfectdreams.pantufa.serverresponses.AutomatedSupportResponse
import net.perfectdreams.pantufa.serverresponses.sparklypower.HowToBuyPesadelosResponse
import net.perfectdreams.pantufa.serverresponses.sparklypower.HowToResetMyPasswordResponse
import net.perfectdreams.pantufa.tables.Users
import net.perfectdreams.pantufa.utils.Emotes
import net.perfectdreams.pantufa.utils.Server
import net.perfectdreams.pantufa.utils.exposed.ilike
import net.perfectdreams.pantufa.utils.extensions.await
import net.perfectdreams.pantufa.utils.extensions.referenceIfPossible
import net.perfectdreams.pantufa.utils.extensions.toJDA
import net.perfectdreams.pantufa.utils.svm.*
import net.sparklypower.common.utils.adventure.TextComponent
import net.sparklypower.rpc.proxy.ProxyExecuteCommandRequest
import net.sparklypower.rpc.proxy.ProxyExecuteCommandResponse
import net.sparklypower.rpc.proxy.ProxySendAdminChatRequest
import net.sparklypower.rpc.proxy.ProxySendAdminChatResponse
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.image.BufferedImage
import java.util.*
import javax.imageio.ImageIO


class DiscordListener(val m: PantufaBot) : ListenerAdapter() {
	companion object {
		private val logger = KotlinLogging.logger {}
	}

	private val discordAccountQuestionSVM: SparklySVM

	private val accountMatchRegexes = listOf(
		// do/de/da NomeDaConta
		Regex("d[oae] ([A-z0-9_]{3,16})", RegexOption.IGNORE_CASE),
		// esse/essa NomeDaConta
		Regex("ess[ea] ([A-z0-9_]{3,16})", RegexOption.IGNORE_CASE),
		// quem é o/a NomeDaConta
		Regex("quem é [oa] ([A-z0-9_]{3,16})", RegexOption.IGNORE_CASE),
		// conhece NomeDaConta
		Regex("conhece [oa]? ?([A-z0-9_]{3,16})", RegexOption.IGNORE_CASE)
	)

	init {
		discordAccountQuestionSVM = loadSVM("svm-discord-account-question")
	}

	val supportResponses = listOf(
		HowToResetMyPasswordResponse(m, loadSVM("svm-how-to-reset-my-password")),
		HowToBuyPesadelosResponse(m, loadSVM("svm-how-to-buy-pesadelos"))
	)

	private fun loadSVM(name: String): SparklySVM {
		val trainedSVMData = Json.decodeFromString<TrainedSVMData>(
			PantufaBot::class.java.getResourceAsStream("/support_vector_machines_data/$name.json").readAllBytes().toString(Charsets.UTF_8)
		)

		return SparklySVM(
			SVM(
				trainedSVMData.weights,
				trainedSVMData.bias
			),
			trainedSVMData.vocabulary
		)
	}

	override fun onGuildBan(event: GuildBanEvent) {
		m.launch {
			if (event.guild.idLong != m.config.sparklyPower.guild.idLong)
				return@launch

			val bannedSparklyUser = m.getDiscordAccountFromUser(event.user) ?: return@launch
			if (!bannedSparklyUser.isConnected)
				return@launch

			val bannedSparklyUsername = transaction(Databases.sparklyPower) { User.findById(bannedSparklyUser.minecraftId)?.username } ?: return@launch

			val userBan = try {
				event.guild.retrieveBan(event.user)
					.await()
			} catch (e: Exception) { return@launch }

			m.proxyRPC.makeRPCRequest<ProxyExecuteCommandResponse>(
				ProxyExecuteCommandRequest(
					null,
					"ban $bannedSparklyUsername Banido no Discord do SparklyPower: ${userBan.reason}"
				)
			)
		}
	}

	override fun onMessageReceived(event: MessageReceivedEvent) {
		if (event.author.isBot)
			return

		if (event.message.type != MessageType.DEFAULT && event.message.type != MessageType.INLINE_REPLY)
			return

		m.launchMessageJob(event) {
			val sparklyPower = m.config.sparklyPower

			if (event.channel.idLong == sparklyPower.guild.staffChannelId) {
				val discordAccount = m.retrieveDiscordAccountFromUser(event.author)
				val sender = if (discordAccount == null || !discordAccount.isConnected) {
					ProxySendAdminChatRequest.AdminChatSender.UnknownUser(event.author.name)
				} else {
					ProxySendAdminChatRequest.AdminChatSender.SparklyUser(discordAccount.minecraftId)
				}

				val imagesToBeAppended = mutableListOf<BufferedImage>()
				val downloadedImages = mutableListOf<BufferedImage>()

				try {
					for (attachment in event.message.attachments) {
						if (attachment.isImage) {
							val originalImage = ImageIO.read(attachment.proxy.download().await())
							downloadedImages.add(originalImage)
						}
					}

					for (sticker in event.message.stickers) {
						if (sticker.formatType == Sticker.StickerFormat.GIF || sticker.formatType == Sticker.StickerFormat.PNG || sticker.formatType == Sticker.StickerFormat.APNG) {
							val originalImage = ImageIO.read(sticker.icon.download(256).await())
							downloadedImages.add(originalImage)
						}
					}
				} catch (e: Exception) {
					logger.warn(e) { "Something went wrong while trying to download images for the admin chat message!" }
				}

				for (image in downloadedImages) {
					// Desired dimensions for scaling
					val maxWidth = 96
					val maxHeight = 48

					fun calculateScaleFactor(originalWidth: Int, originalHeight: Int, targetWidth: Int, targetHeight: Int): Float {
						// Calculate scaling factors for width and height
						val widthScale = targetWidth.toFloat() / originalWidth
						val heightScale = targetHeight.toFloat() / originalHeight

						// Return the smaller scale factor to maintain aspect ratio
						return minOf(widthScale, heightScale)
					}

					// Calculate the scaling factor to preserve aspect ratio
					val scaleFactor = calculateScaleFactor(image.width, image.height, maxWidth, maxHeight)
					val scaledWidth = (image.width * scaleFactor).toInt()
					val scaledHeight = (image.height * scaleFactor).toInt()

					// Create a new BufferedImage for the scaled image
					val scaledImage = BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB)

					// Get the Graphics2D object
					val g2d = scaledImage.createGraphics()

					// We don't use any interpolation because it isn't that "wow"
					// Draw the scaled image
					g2d.drawImage(image, 0, 0, scaledWidth, scaledHeight, null)

					imagesToBeAppended.add(scaledImage)
				}

				val contentRaw = event.message.contentRaw
				val response = m.proxyRPC.makeRPCRequest<ProxySendAdminChatResponse>(
					ProxySendAdminChatRequest(
						sender,
						JSONComponentSerializer.json().serialize(
							TextComponent {
								append(
									TextComponent {
										if (contentRaw.isNotEmpty()) {
											content(contentRaw)
										} else {
											decorate(TextDecoration.ITALIC)
											content("*mensagem vazia*")
										}
									}
								)
							}
						),
						if (imagesToBeAppended.isNotEmpty()) {
							JSONComponentSerializer.json().serialize(
								TextComponent {
									for (image in imagesToBeAppended) {
										val imageHeightStep = (0 until image.height step 4)

										for (y in 0 until image.height step 4) {
											for (x in 0 until image.width) {
												append(
													TextComponent {
														font(Key.key("sparklypower:chat_pixel_drawing"))

														fun getRGBIfInBounds(x: Int, y: Int): Int? {
															return if (x in 0 until image.width && y in 0 until image.height)
																image.getRGB(x, y)
															else
																null
														}

														val y1 = getRGBIfInBounds(x, y)
														val y2 = getRGBIfInBounds(x, y + 1)
														val y3 = getRGBIfInBounds(x, y + 2)
														val y4 = getRGBIfInBounds(x, y + 3)

														if (y1 != null) {
															append(
																TextComponent {
																	content("a")
																	color(TextColor.color(y1))
																}
															)
														}

														if (y2 != null) {
															append(
																TextComponent {
																	content("zb")
																	color(TextColor.color(y2))
																}
															)
														}

														if (y3 != null) {
															append(
																TextComponent {
																	content("zc")
																	color(TextColor.color(y3))
																}
															)
														}

														if (y4 != null) {
															append(
																TextComponent {
																	content("zd")
																	color(TextColor.color(y4))
																}
															)
														}

														append(
															TextComponent {
																content("x")
															}
														)
													}
												)
											}

											if (y != imageHeightStep.last)
												appendNewline()
										}
									}
								}
							)
						} else null
					)
				)
			}

			if (event.channel.idLong == sparklyPower.guild.chitChatChannelId) {
				val unshortenedWordsContent = replaceShortenedWordsWithLongWords(event.message.contentRaw)
				val content = normalizeNaiveBayesInput(unshortenedWordsContent)
				val (predictedValue, rawValue) = discordAccountQuestionSVM.predictWithRawValue(content)

				logger.info { "Content \"$content\" is $predictedValue ($rawValue)" }

				if (predictedValue) {
					// We match against the original content (but with the short words replaced) to avoid normalization causing issues
					val matches = accountMatchRegexes.flatMap { it.findAll(unshortenedWordsContent) }
					for (match in matches) {
						// We try querying all possible matches
						val username = match.groupValues[1]
						val account = transaction(Databases.sparklyPower) {
							// First we get the exact match
							val fullMatchedUsername = User.find { Users.username eq username }.firstOrNull()
							if (fullMatchedUsername != null)
								return@transaction fullMatchedUsername

							// Then we try getting ignored case (what if there are two users with the same name but in different casing? probably impossible but who knows...)
							val ignoredCaseUsername = User.find { Users.username ilike username }.firstOrNull()
							if (ignoredCaseUsername != null)
								return@transaction ignoredCaseUsername

							return@transaction null
						}

						if (account != null) {
							val discordAccount = m.getDiscordAccountFromUniqueId(account.id.value)

							if (discordAccount != null) {
								event.channel.sendMessage(
									MessageCreate {
										styled(
											"A conta de `${account.username}` no Discord é <@${discordAccount.discordId}> (`${discordAccount.discordId}`)",
											Emotes.PantufaHi
										)

										styled(
											"**Dica da Pantufinha:** No futuro use o comando `/sparklyplayer playername` para ver a conta do Discord de outros players do SparklyPower!",
											Emotes.PantufaComfy
										)

										this.content += "\n-# • Resposta automática, espero que ela tenha te ajudado!"

										allowedMentionTypes = EnumSet.of(
											Message.MentionType.EMOJI,
											Message.MentionType.CHANNEL,
											Message.MentionType.SLASH_COMMAND,
										)
									}
								).referenceIfPossible(event.message).await()
								return@launchMessageJob
							}
						}
					}

					event.message.addReaction(Emotes.PantufaShrug.toJDA()).await()
				}
			}

			val currentChannel = event.channel

			if (currentChannel is ThreadChannel) {
				val parentChannel = currentChannel.parentChannel

				if (parentChannel.idLong == m.config.sparklyPower.guild.supportChannelId) {
					// We remove any lines starting with > (quote) because this sometimes causes responses to something inside a citation, and that looks kinda bad
					val cleanMessage = event.message.contentRaw.lines()
						.dropWhile { it.startsWith(">") }
						.joinToString("\n")

					for (response in supportResponses) {
						if (response.handleResponse(cleanMessage)) {
							logger.info { "Using support response \"${response::class.simpleName}\" for message \"${event.message.contentRaw}\" ${event.message.jumpUrl}" }

							val automatedSupportResponse = response.getSupportResponse(event.author, cleanMessage)

							if (automatedSupportResponse != null) {
								val messageCreateData = when (automatedSupportResponse) {
									is AutomatedSupportResponse.AutomatedSupportPantufaReplyResponse -> {
										MessageCreate {
											content = buildString {
												for (response in automatedSupportResponse.replies) {
													appendLine(response.build(event.author))
												}

												appendLine("-# • Resposta automática, se ela resolveu a sua dúvida, feche o ticket com `/closeticket`!")
											}
										}
									}
									is AutomatedSupportResponse.AutomatedSupportMessageResponse -> {
										MessageCreate {
											automatedSupportResponse.messageBuilder.invoke(this)

											if (content != null) {
												content = "\n-# • Resposta automática, se ela resolveu a sua dúvida, feche o ticket com `/closeticket`!"
											} else {
												content = "-# • Resposta automática, se ela resolveu a sua dúvida, feche o ticket com `/closeticket`!"
											}
										}
									}
								}

								event.channel.sendMessage(messageCreateData).setMessageReference(event.messageIdLong).failOnInvalidReply(false).await()
								break
							}
						}
					}
				}
			}

			if (checkCommandsAndDispatch(event))
				return@launchMessageJob
		}
	}

	suspend fun checkCommandsAndDispatch(event: MessageReceivedEvent): Boolean {
		// If Pantufa can't speak in the current channel, do *NOT* try to process a command! If we try to process, Pantufa will have issues that she wants to talk in a channel, but she doesn't have the "canTalk()" permission!
		if (event.channel is TextChannel && !event.channel.canTalk())
			return false

		val rawMessage = event.message.contentRaw
		val rawArguments = rawMessage
			.split(" ")
			.toMutableList()

		val firstLabel = rawArguments.first()
		val startsWithCommandPrefix = firstLabel.startsWith(PantufaBot.PREFIX)
		val startsWithPantufaMention = firstLabel == "<@${m.jda.selfUser.id}>" || firstLabel == "<@!${m.jda.selfUser.id}>"

		// If the message starts with the prefix, it could be a command!
		if (startsWithCommandPrefix || startsWithPantufaMention) {
			if (startsWithCommandPrefix) // If it is a command prefix, remove the prefix
				rawArguments[0] = rawArguments[0].removePrefix(PantufaBot.PREFIX)
			else if (startsWithPantufaMention) { // If it is a mention, remove the first argument (which is Pantufa's mention)
				rawArguments.removeAt(0)
				if (rawArguments.isEmpty()) // If it is empty, then it means that it was only Pantufa's mention, so just return false
					return false
			}

			// Executar comandos
			if (m.interactionsListener.manager.matches(event, rawArguments)) {
				return true
			}
		}

		return false
	}
}