package net.perfectdreams.dreamloja.listeners

import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import kotlinx.coroutines.InternalCoroutinesApi
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.sendTextComponent
import net.perfectdreams.dreamcore.utils.extensions.loadAsync
import net.perfectdreams.dreamcore.utils.extensions.rightClick
import net.perfectdreams.dreamcore.utils.scheduler.onAsyncThread
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamloja.DreamLoja
import net.perfectdreams.dreamloja.dao.UserShopVote
import net.perfectdreams.dreamloja.dao.VoteSign
import net.perfectdreams.dreamloja.tables.UserShopVotes
import net.perfectdreams.dreamloja.tables.VoteSigns
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Statistic
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class SignListener(val m: DreamLoja) : Listener {
    private suspend fun updateVoteSigns(owner: UUID) {
        onAsyncThread {
            val voteSigns = transaction(Databases.databaseNetwork) {
                VoteSign.find {
                    VoteSigns.owner eq owner
                }.toMutableList()
            }

            val voteCount = transaction(Databases.databaseNetwork) {
                UserShopVotes.select {
                    UserShopVotes.receivedBy eq owner
                }.count()
            }

            onMainThread {
                voteSigns.forEach {
                    val location = it.getLocation()
                    // To avoid loading multiple chunks on the main thread at the same time, we will load them async to avoid lag
                    // If the chunk doesn't exist, we will skip the sign
                    location.chunk.loadAsync(false) ?: return@forEach

                    // We don't use the chunk reference, we don't *really* need it
                    val sign = location.block.state

                    if (sign is Sign) {
                        sign.setLine(3, "§bVotos: §3$voteCount")
                        sign.update()
                    } else {
                        onAsyncThread {
                            transaction(Databases.databaseNetwork) {
                                it.delete()
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun onSignEditGoToShop(e: SignChangeEvent) {
        if (!e.lines[0].equals("[Ir para Loja]", true))
            return

        if (e.lines[1].isBlank()) {
            e.player.sendMessage("§cVocê precisa especificar o nome do player na segunda linha!")
            return
        }

        e.setLine(0, "§8[§a§lIr para Loja§8]")
        e.player.sendMessage("§aPlaca de teletransporte para a loja marcada com sucesso! Você pode colocar qual loja do player você quer que o teletransporte leve na segunda linha.")
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onClickGoToShop(e: PlayerInteractEvent) {
        if (!e.rightClick)
            return

        if (e.clickedBlock?.type?.name?.endsWith("_SIGN") == false)
            return

        val block = e.clickedBlock ?: return

        val sign = block.state as Sign

        if (sign.lines[0] != "§8[§a§lIr para Loja§8]")
            return

        val line1 = sign.lines[1]
        val line2 = sign.lines[2]

        var cmd = "loja "
        if (line1.isNotBlank()) {
            cmd += "$line1 "

            if (line2.isNotBlank())
                cmd += line2

            Bukkit.dispatchCommand(e.player, cmd.trim())
        }
    }

    @InternalCoroutinesApi
    @EventHandler
    fun onSignEdit(e: SignChangeEvent) {
        if (!e.lines[0].equals("[Votar]", true))
            return

        e.setLine(0, "§8[§a§lVotar§8]")
        e.setLine(1, "§cClique para votar")
        e.setLine(2, "§b${e.player.name}")

        m.launchAsyncThread {
            transaction(Databases.databaseNetwork) {
                VoteSign.new {
                    this.owner = e.player.uniqueId
                    this.setLocation(e.block.location)
                }
            }

            updateVoteSigns(e.player.uniqueId)

            onMainThread {
                e.player.sendTextComponent {
                    color(NamedTextColor.GREEN)
                    append(DreamLoja.PREFIX)
                    append(" ")
                    append("Placa de votação criada com sucesso!")
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) {
        if (!e.block.type.name.endsWith("_SIGN"))
            return

        scheduler().schedule(m, SynchronizationContext.ASYNC) {
            val hasVoteSign = transaction(Databases.databaseNetwork) {
                val voteSign = VoteSign.find {
                    (VoteSigns.x eq e.block.location.x) and
                            (VoteSigns.y eq e.block.location.y) and
                            (VoteSigns.z eq e.block.location.z) and
                            (VoteSigns.worldName eq e.block.world.name)
                }.firstOrNull()

                voteSign?.delete()

                return@transaction voteSign != null
            }

            switchContext(SynchronizationContext.SYNC)

            if (hasVoteSign)
                e.player.sendTextComponent {
                    color(NamedTextColor.RED)
                    append(DreamLoja.PREFIX)
                    append(" ")
                    append("A placa de votação foi quebrada! Mas não se preocupe, os votos ainda estão firmes e fortes!")
                }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onClick(e: PlayerInteractEvent) {
        if (!e.rightClick)
            return

        if (e.clickedBlock?.type?.name?.endsWith("_SIGN") == false)
            return

        val block = e.clickedBlock ?: return

        val sign = block.state as Sign

        if (sign.lines[0] != "§8[§a§lVotar§8]")
            return

        e.isCancelled = true

        m.launchAsyncThread {
            val voteSign = transaction(Databases.databaseNetwork) {
                VoteSign.find {
                    (VoteSigns.x eq block.location.x) and
                            (VoteSigns.y eq block.location.y) and
                            (VoteSigns.z eq block.location.z) and
                            (VoteSigns.worldName eq block.world.name)
                }.firstOrNull()
            } ?: return@launchAsyncThread // Não é uma placa de votação

            if (e.player.uniqueId == voteSign.owner) {
                e.player.sendTextComponent {
                    color(NamedTextColor.RED)
                    append(DreamLoja.PREFIX)
                    append(" ")
                    append("Você vê a sua placa de votação atentamente, e pensa... \"Um dia, eu serei um empreendedor incrível!\" e, como você respeita as regras dos comerciantes da LorittaLand, você sabe que não pode votar na sua própria loja.")
                }
                return@launchAsyncThread
            }

            val shopVote = transaction(Databases.databaseNetwork) {
                UserShopVote.find { UserShopVotes.givenBy eq e.player.uniqueId and (UserShopVotes.receivedBy eq voteSign.owner) }.firstOrNull()
            }

            if (shopVote != null) {
                val diff = System.currentTimeMillis() - shopVote.receivedAt

                if (300_000 > diff) {
                    e.player.sendTextComponent {
                        color(NamedTextColor.RED)
                        append(DreamLoja.PREFIX)
                        append(" ")
                        append("Você tem que esperar ${DateUtils.formatDateDiff(shopVote.receivedAt + 300_000L)} antes de tirar o seu voto! Que tal conversar com o proprietário da loja para ver se vocês resolvem as suas diferenças?")
                    }
                    return@launchAsyncThread
                }

                // YES, THIS IS TICKS PLAYED
                val ticksPlayed = e.player.getStatistic(Statistic.PLAY_ONE_MINUTE)
                if (144_000 > ticksPlayed) {
                    e.player.sendTextComponent {
                        color(NamedTextColor.RED)
                        append(DreamLoja.PREFIX)
                        append(" ")
                        append("Você só pode votar na loja de um player após ter jogado no SparklyPower por mais de 2 horas!")
                    }
                    return@launchAsyncThread
                }

                transaction(Databases.databaseNetwork) {
                    shopVote.delete()
                }

                updateVoteSigns(voteSign.owner)

                onMainThread {
                    e.player.sendTextComponent {
                        color(NamedTextColor.GREEN)
                        append(DreamLoja.PREFIX)
                        append(" ")
                        append("Você removeu o voto da loja d${MeninaAPI.getArtigo(voteSign.owner)} ")
                        append(Bukkit.getOfflinePlayer(voteSign.owner).name ?: "???") {
                            color(NamedTextColor.AQUA)
                        }
                        append("...")
                    }

                    e.player.sendTextComponent {
                        color(NamedTextColor.GRAY)
                        append(DreamLoja.PREFIX)
                        append(" ")
                        append("Lembre-se, votos ajudam donos das lojas, não seja vacilão para votar e depois tirar só para ser v1d4 l0ka.")
                    }
                }
                return@launchAsyncThread
            }

            transaction(Databases.databaseNetwork) {
                UserShopVote.new {
                    this.givenBy = e.player.uniqueId
                    this.receivedAt = System.currentTimeMillis()
                    this.receivedBy = voteSign.owner
                }
            }

            updateVoteSigns(voteSign.owner)

            onMainThread {
                e.player.sendTextComponent {
                    color(NamedTextColor.GREEN)
                    append(DreamLoja.PREFIX)
                    append(" ")
                    append("Você votou na loja d${MeninaAPI.getArtigo(voteSign.owner)} ")
                    append(Bukkit.getOfflinePlayer(voteSign.owner).name ?: "???") {
                        color(NamedTextColor.AQUA)
                    }
                    append("!")
                }

                InstantFirework.spawn(
                    block.location.add(0.0, 0.5, 0.0), FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL)
                        .withColor(Color.AQUA)
                        .flicker(true)
                        .build()
                )
            }
        }
    }
}