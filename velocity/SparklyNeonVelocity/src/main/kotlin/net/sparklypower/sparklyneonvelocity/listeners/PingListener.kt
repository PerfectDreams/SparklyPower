package net.sparklypower.sparklyneonvelocity.listeners

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerPing
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.sparklypower.common.utils.TextUtils
import net.sparklypower.common.utils.adventure.TextComponent
import net.sparklypower.common.utils.toLegacySection
import net.sparklypower.sparklyneonvelocity.SparklyNeonVelocity
import net.sparklypower.sparklyneonvelocity.utils.DreamUtils
import java.time.DayOfWeek
import java.time.Instant
import java.util.*

class PingListener(private val m: SparklyNeonVelocity, private val server: ProxyServer) {
    companion object {
        private val COLOR_LOGO_RED = TextColor.color(237, 46, 22)
        private val COLOR_LOGO_AQUA = TextColor.color(1, 235, 247)
    }

    // A simple MOTD because Bedrock SUCKS because you don't have ANY SPACE for your MOTD
    // Heck, this MOTD does NOT HAVE ENOUGH SPACE
    val bedrockMOTD = TextComponent {
        // https://wiki.bedrock.dev/concepts/emojis.html
        append(
            TextComponent {
                color(NamedTextColor.DARK_RED)
                decorate(TextDecoration.BOLD)
                content("Sparkly")
            }
        )

        append(
            TextComponent {
                color(NamedTextColor.AQUA)
                decorate(TextDecoration.BOLD)
                content("Power")
            }
        )
    }

    @Subscribe
    fun onPing(event: ProxyPingEvent) {
        m.logger.info { "Pinged by ${event.connection.remoteAddress.hostString}, adding to the pinged by addresses list..." }
        m.pingedByAddresses.add(event.connection.remoteAddress.hostString)

        val now = Instant.now().atZone(DreamUtils.serverZoneId)
        val currentDayOfTheWeek = now.dayOfWeek
        val year = now.year

        // val version = ProtocolSupportAPI.getProtocolVersion(e.connection.address)
        val online = server.playerCount
        val max = year

        val builder = event.ping.asBuilder()
            .onlinePlayers(online)
            .maximumPlayers(max)

        if (m.isGeyser(event.connection)) {
            builder.description(bedrockMOTD)

            event.ping = builder.build()
            return
        }

        val top: String
        val bottom: String

        if (m.isMaintenance) {
            builder.favicon(m.favicons["pantufa_zz"])

            top = TextUtils.getCenteredMessage("§cSparklyPower está em manutenção!", 128)

            bottom = TextUtils.getCenteredMessage(
                "§cVolte mais tarde!",
                128
            )
        } else {
            builder.favicon(m.favicons["pantufa_point_allouette"])

            top = TextUtils.getCenteredMessage("§a\u266b §6(\uff89\u25d5\u30ee\u25d5)\uff89 §e* :\uff65\uff9f\u2727 ${COLOR_LOGO_RED.toLegacySection()}§lSparkly${COLOR_LOGO_AQUA.toLegacySection()}§lPower §e\u2727\uff9f\uff65: *§6\u30fd(\u25d5\u30ee\u25d5\u30fd) §a\u266b", 128)

            bottom = if (currentDayOfTheWeek == DayOfWeek.FRIDAY) {
                TextUtils.getCenteredMessage(
                    "§5§l»§d§l» §x§d§5§d§6§1§0HOJE É SEXTA CAMBADA! VAMOS ANIMAR!!! §d§l«§5§l«",
                    128
                )
            } else {
                // The colored part is "inesquecível"!
                TextUtils.getCenteredMessage(
                    "§5§l»§d§l» §fSurvival de um jeito §x§F§F§8§0§8§0i§x§F§F§B§F§8§0n§x§F§F§F§F§8§0e§x§B§F§F§F§8§0s§x§8§0§F§F§8§0q§x§8§0§F§F§B§Fu§x§8§0§F§F§F§Fe§x§8§0§B§F§F§Fc§x§8§0§8§0§F§Fí§x§B§F§8§0§F§Fv§x§F§F§8§0§F§Fe§x§F§F§8§0§B§Fl§f! §d§l«§5§l«",
                    128
                )
            }
        }

        // val bottom = TextUtils.getCenteredMessage("§5§l»§d§l» §fQuer coisas da §c§l1.16§f? Então entre! §c^-^ §d§l«§5§l«", 128)
        builder.description(LegacyComponentSerializer.legacySection().deserialize("$top\n$bottom"))

        builder.samplePlayers(
            createPlayerListMessage("§b✦§3§m                    §8[§4§lSparkly§b§lPower§8]§3§m                    §b✦"),
            createPlayerListMessage("§6✧ §fSurvival de um jeito §a§linesquecível§f! §6✧"),
            createPlayerListMessage(""),
            createPlayerListMessage("§b✦ §6$online§e Players Online! §b✦"),
            createPlayerListMessage("§b✦ §eUm §6Survival§e que você §6jamais§e viu antes! §b✦"),
            createPlayerListMessage("§b✦ §eServidor da §6Loritta Morenitta§e! §b✦"),
            createPlayerListMessage("§b✦ §eItens §6personalizados§e! §b✦"),
            createPlayerListMessage("§b✦ §eSem §6Lag§e! (as vezes né) §b✦"),
            createPlayerListMessage("§b✦ §eDesde §62014§e divertindo nossos jogadores! §b✦"),
            createPlayerListMessage("§b✦ §ee §6§lmuito§e mais! §b✦"),
            createPlayerListMessage(""),
            createPlayerListMessage("§5✸ §eentre agora... §5✸"),
            createPlayerListMessage("§d✸ §eporque só falta você! §c✌ §d✸"),
        )

        event.ping = builder.build()
    }

    private fun createPlayerListMessage(text: String) = ServerPing.SamplePlayer(
        TextUtils.getCenteredMessage(
            text,
            128
        ),
        UUID.randomUUID()
    )
}