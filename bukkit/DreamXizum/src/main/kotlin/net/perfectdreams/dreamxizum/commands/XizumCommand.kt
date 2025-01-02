package net.perfectdreams.dreamxizum.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.extensions.centralizeHeader
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamcore.utils.set
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.structures.XizumBattle
import net.perfectdreams.dreamxizum.structures.XizumRequestHolder
import net.perfectdreams.dreamxizum.tables.dao.XizumProfile
import net.perfectdreams.dreamxizum.utils.XizumRank
import org.bukkit.Bukkit
import org.jetbrains.exposed.sql.transactions.transaction

class XizumCommand(val m: DreamXizum) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("xizum", "x1")) {
        executor = XizumCommandExecutor()

        subcommand(listOf("camarote", "spectate")) {
            executor = XizumSpectateCommandExecutor()
        }

        subcommand(listOf("desafiar", "challenge")) {
            executor = XizumChallengeCommandExecutor()
        }

        subcommand(listOf("aceitar", "accept")) {
            executor = XizumAcceptCommandExecutor()
        }

        subcommand(listOf("recusar", "decline")) {
            executor = XizumDeclineCommandExecutor()
        }

        subcommand(listOf("cancelar", "cancel")) {
            executor = XizumCancelCommandExecutor()
        }

        subcommand(listOf("ranking", "top")) {
            executor = XizumRankingCommandExecutor()
        }

        //subcommand(listOf("desafiar", "challenge")) {
        //            executor
        //        }
    }

    inner class XizumCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            if (m.activeBattles.any { it.player == player || it.opponent == player }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você já está em uma batalha!")
                }
                return
            }

            val hud = XizumRequestHolder.build(m, player)

            player.openInventory(hud)
        }
    }

    inner class XizumSpectateCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            val loc = m.config.spectatorPos?.toBukkitLocation(m.arenas.first().data.worldName)

            if (loc == null) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("A localização do camarote não foi definida, reporte para a staff!")
                }

                return
            }

            if (player.teleport(loc)) {
                player.persistentDataContainer.set(DreamXizum.IS_IN_CAMAROTE, true)

                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.GREEN)
                    append("Você foi teleportado para o camarote da arena!")
                }
            } else {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Não foi possível teleportar você para o camarote!")
                }
            }
        }
    }

    inner class XizumChallengeCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val target = player("target") { context, builder ->
                Bukkit.getOnlinePlayers().forEach {
                    builder.suggest(it.name)
                }
            }
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()
            val target = args[options.target]

            if (player == target) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não pode desafiar a si mesmo!")
                }
                return
            }

            if (m.queue.any { it.player == player || it.opponent == player }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você já está em uma fila de espera!")
                }
                return
            }

            if (m.queue.any { it.player == target || it.opponent == target }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("O jogador que você quer desafiar já está em uma fila de espera!")
                }

                return
            }

            if (m.activeBattles.any { it.player == player || it.opponent == player }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você já está em uma batalha!")
                }
                return
            }

            if (m.activeBattles.any { it.player == target || it.opponent == target}) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("O jogador que você quer desafiar já está em uma batalha!")
                }

                return
            }

            if (!target.isOnline) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("O jogador que você quer desafiar não está online!")
                }

                return
            }

            val hud = XizumRequestHolder.build(m, player, target, true)

            player.openInventory(hud)
        }
    }

    inner class XizumAcceptCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            if (!m.queue.any { it.opponent == player }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não tem nenhum convite para aceitar!")
                }

                return
            }

            val request = m.queue.first { it.opponent == player }
            val arena = m.arenas.filter { !it.inUse }.randomOrNull() ?: return m.notifyNoArena(request, request)
            val battle = XizumBattle(m, arena, request.mode, request.player, request.opponent!!)

            battle.start()

            m.activeBattles.add(battle)
            m.queue.remove(request)

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Você aceitou o convite de batalha!")
            }

            request.player.sendMessage(textComponent {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("O seu convite de batalha foi aceito!")
            })
        }
    }

    inner class XizumDeclineCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            if (!m.queue.any { it.opponent == player }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não tem nenhum convite para recusar!")
                }

                return
            }

            val request = m.queue.first { it.opponent == player }

            m.queue.remove(request)

            request.player.sendMessage(textComponent {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.RED)
                append("O seu convite de batalha para §b${player.displayName} §cfoi recusado!")
            })

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Você recusou o convite de batalha de §b${request.player.displayName}§c!")
            }
        }
    }

    inner class XizumCancelCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            if (!m.queue.any { it.player == player }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Você não está em nenhuma fila de espera!")
                }

                return
            }

            val request = m.queue.first { it.player == player }

            m.queue.remove(request)

            request.opponent!!.sendMessage(textComponent {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.RED)
                append("O convite de §b${request.player.displayName} §cpara você foi expirado!")
            })

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Você cancelou o convite de batalha para §b${request.opponent.displayName}§a!")
            }
        }
    }

    inner class XizumRankingCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            m.launchAsyncThread {
                val profiles = transaction(Databases.databaseNetwork) {
                    XizumProfile.all()
                        .limit(10)
                        .toList()
                }

                val sortedProfiles = profiles.sortedByDescending { it.rating }

                onMainThread {
                    context.sendMessage {
                        append(LegacyComponentSerializer.legacySection().deserialize("§8[ §bRanking de Combatentes §8- §6Competitivo §8]".centralizeHeader()))
                        appendNewline()
                        appendNewline()

                        for ((index, profile) in sortedProfiles.withIndex()) {
                            val innerPlayer = Bukkit.getOfflinePlayer(profile.id.value)

                            append("§7${index + 1}º §8- §b${innerPlayer.name} §8- §6${XizumRank.getTextByRating(profile.rating)} §8- §6${profile.rating} PdC")
                            appendNewline()
                        }

                        appendNewline()
                        appendNewline()
                        append(LegacyComponentSerializer.legacySection().deserialize("§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-§3§m-§b§m-"))
                    }
                }
            }
        }
    }
}