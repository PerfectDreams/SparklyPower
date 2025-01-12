package net.perfectdreams.dreamsocial.commands.announce

import com.github.benmanes.caffeine.cache.Caffeine
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.perfectdreams.dreamchat.DreamChat
import net.perfectdreams.dreamcore.utils.TimeUtils
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.extensions.*
import net.perfectdreams.dreamcore.utils.preferences.BroadcastType
import net.perfectdreams.dreamcore.utils.preferences.shouldSeeBroadcast
import net.perfectdreams.dreamcore.utils.stripColorCode
import net.perfectdreams.dreamcore.utils.translateColorCodes
import net.perfectdreams.dreamsocial.DreamSocial
import net.perfectdreams.dreamsocial.commands.announce.helper.PrefixGenerator
import net.perfectdreams.dreamsocial.dao.AnnouncementsEntity
import net.perfectdreams.dreamsocial.gui.announcement.renderAnnouncementsMenu
import net.perfectdreams.dreamvanish.DreamVanishAPI
import org.bukkit.entity.Player
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class AnnounceExecutor(private val plugin: DreamSocial, private val dreamChat: DreamChat) : SparklyCommandExecutor() {
    inner class Options : CommandOptions() {
        val message = optionalGreedyString("message")
    }

    override val options = Options()

    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()
        val currentTimestamp = System.currentTimeMillis()

        if (DreamChat.mutedUsers.contains(player.name)) {
            context.fail("Você está mutado e, portanto, não pode anunciar nada!")
        }

        cooldowns[player]?.let {
            val humanizedTime = TimeUtils.convertEpochMillisToAbbreviatedTime(it - currentTimestamp).ifEmpty { "um pouquinho" }
            context.fail(HAS_RECENTLY_ANNOUNCED.format(humanizedTime).asComponent.color(RED_COLOR))
        }

        if (currentTimestamp - lastAnnouncement < 10_000) context.fail(TOO_SOON.asComponent.color(RED_COLOR))

        val canPlayerSaveAnnouncements = player.isVIPPlus || player.isVIPPlusPlus

        val message = args[options.message] ?: return run {
            if (canPlayerSaveAnnouncements)
                plugin.schedule(SynchronizationContext.ASYNC) {
                    val savedAnnouncements = AnnouncementsEntity.fetch(player.uniqueId)

                    switchContext(SynchronizationContext.SYNC)

                    if (savedAnnouncements.isEmpty) return@schedule player.sendMessage(NO_SAVED_ANNOUNCEMENTS.asComponent.color(RED_COLOR))

                    renderAnnouncementsMenu(this@AnnounceExecutor.plugin, player, savedAnnouncements).sendTo(player)
                }
            else {
                val maleArticleOrNothing = player.artigo.let { if (it == "o") it else "" }
                context.fail(EMPTY_MESSAGE.format(maleArticleOrNothing).asComponent.color(RED_COLOR))
            }
        }

        cooldowns[player] = currentTimestamp + 60 * 3_000
        lastAnnouncement = currentTimestamp

        val coloredMessage = if (player.hasAnyVIP) message.translateColorCodes() else message.stripColorCode()

        val prefixAndColors = PrefixGenerator.getPrefixAndColors()

        val prefix = prefixAndColors.coloredPrefix
        val nicknameColor = prefixAndColors.nicknameColor
        val messageColor = prefixAndColors.messageColor

        val broadcast = "\n $prefix $nicknameColor${player.name}: $messageColor$coloredMessage\n §r"
        val ignoreList = dreamChat.userData.getStringList("ignore.${player.uniqueId}")
        var reachedPlayers = 0

        plugin.server.onlinePlayers.forEach {
            if (it == player) return@forEach
            if (it.uniqueId.toString() in ignoreList && !it.isStaff) return@forEach
            if (!it.shouldSeeBroadcast(BroadcastType.PLAYER_ANNOUNCEMENT)) return@forEach

            if (!DreamVanishAPI.isQueroTrabalhar(it)) reachedPlayers++
            it.sendMessage(broadcast)
        }

        player.sendMessage(broadcast)

        if (canPlayerSaveAnnouncements) SaveAnnouncementExecutor.lastAnnouncements[player] = message

        if (reachedPlayers == 0)
            player.sendMessage(REACHED_NONE.asComponent.color(NamedTextColor.YELLOW))
        else
            player.sendMessage(REACHED_SOME.format(reachedPlayers.pluralize("jogador" to "jogadores")).asComponent.color(NamedTextColor.YELLOW))
    }

    companion object {
        private const val HAS_RECENTLY_ANNOUNCED = "Alto lá, almirante! Você anunciou recentemente, e, como não gostamos de spam, você terá que esperar mais %s antes de poder usar o comando novamente."
        private const val TOO_SOON = "Ops, parece que alguém anunciou recentemente. Por favor, espere um pouco antes de usar este comando para não ofuscar o brilho do anúncio anterior."
        private const val NO_SAVED_ANNOUNCEMENTS = "Você não tem nenhum anúncio salvo. Que tal fazer um novo? Digite /anunciar <mensagem>. Em seguida, você terá a oportunidade salvá-lo."
        private const val EMPTY_MESSAGE = "Hmm, mas e a mensagem, campeã%s? A síntaxe correta é /anunciar <mensagem>."
        private const val REACHED_NONE = "Ah, mas que pena. Seu anúncio não alcançou nenhum jogador desta vez :("
        private const val REACHED_SOME = "Yay, seu anúncio alcançou um total de %s!"

        private val RED_COLOR = TextColor.color(0xFF5555)

        private var lastAnnouncement = 0L

        private val cooldowns = Caffeine.newBuilder()
            .expireAfterWrite(3.minutes.toJavaDuration())
            .build<Player, Long>()
            .asMap()
    }
}
