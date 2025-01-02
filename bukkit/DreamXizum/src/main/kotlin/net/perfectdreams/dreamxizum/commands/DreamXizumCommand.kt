package net.perfectdreams.dreamxizum.commands

import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.generateCommandInfo
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import net.perfectdreams.dreamxizum.utils.config.XizumPluginConfig
import org.bukkit.Bukkit

class DreamXizumCommand(val m: DreamXizum) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("dreamxizum")) {
        permission = "dreamxizum.setup"

        subcommand(listOf("arenas")) {
            executor = DreamXizumArenasCommandExecutor()
        }

        subcommand(listOf("createarena")) {
            executor = DreamXizumCreateArenaCommandExecutor()
        }

        subcommand(listOf("deletearena")) {
            executor = DreamXizumDeleteArenaCommandExecutor()
        }

        subcommand(listOf("setpos")) {
            executor = DreamXizumSetPosCommandExecutor()
        }

        subcommand(listOf("teleport")) {
            executor = DreamXizumTeleportCommandExecutor()
        }

        subcommand(listOf("setspectator")) {
            executor = DreamXizumSetSpectatorCommandExecutor()
        }

        subcommand(listOf("setarenamode")) {
            executor = DreamXizumSetArenaModeCommandExecutor()
        }

        subcommand(listOf("setpdc")) {
            executor = DreamXizumSetCombatPointsCommandExecutor()
        }

        // the root command will explain how to use the rest of the commands
        executor = DreamXizumCommandExecutor()
    }

    inner class DreamXizumCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val commands = listOf(
                "§6§l/dreamxizum arenas §7- §eVer as arenas disponíveis",
                "§6§l/dreamxizum createarena §7- §eCriar uma nova arena",
                "§6§l/dreamxizum deletearena <arenaName> §7- §eDeletar uma arena",
                "§6§l/dreamxizum setpos <arenaName> <forPlayer> §7- §eSetar a posição de um jogador/oponente",
                "§6§l/dreamxizum teleport <arenaName> §7- §eTeleportar para uma arena",
                "§6§l/dreamxizum setspectator §7- §eSetar a posição de espectador",
                "§6§l/dreamxizum setarenamode <arenaName> <mode> §7- §eSetar o modo de uma arena",
                "§6§l/dreamxizum setpdc <player> <points> §7- §eSetar os pontos de combate de um jogador"
            )

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                append("Comandos disponíveis:") {
                    color(NamedTextColor.GREEN)
                }

                commands.forEach {
                    append("\n §7- $it")
                }
            }
        }
    }

    inner class DreamXizumArenasCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                if (m.arenas.isEmpty()) {
                    color(NamedTextColor.RED)
                    append("Não existem arenas!")
                } else {
                    append("Arenas disponíveis:") {
                        color(NamedTextColor.GREEN)
                    }
                }

                m.arenas.forEach {
                    append("\n §7- §e§lArena: §a${it.data.arenaName}")
                    append("\n  §7- §e§lWorld: §a${it.data.worldName}")

                    append("\n  §7- §e§lPlayer Pos: ")
                    if (it.playerPos != null) {
                        append("§a(${Math.round(it.playerPos.x)}; ${Math.round(it.playerPos.y)}; ${Math.round(it.playerPos.z)})")

                    } else {
                        append("§cNenhuma, use ")
                        appendCommand("/dreamxizum setpos <arenaName> player")
                    }

                    append("\n  §7- §e§lOpponent Pos: ")

                    if (it.opponentPos != null) {
                        append("§a(${Math.round(it.opponentPos.x)}; ${Math.round(it.opponentPos.y)}; ${Math.round(it.opponentPos.z)})")
                    } else {
                        append("§cNenhuma, use ")
                        appendCommand("/dreamxizum setpos <arenaName> opponent")
                    }

                    append("\n  §7- §e§lModo: ")
                    if (it.data.mode != null) {
                        append("§a${XizumBattleMode.prettify(it.data.mode)}")
                    } else {
                        append("§cNenhum, use ")
                        appendCommand("/dreamxizum setarenamode <arenaName> <mode>")
                    }
                }
            }
        }
    }

    inner class DreamXizumCreateArenaCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val arenaName = word("arenaName")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val arenaName = args[options.arenaName]
            val player = context.requirePlayer()

            if (m.arenas.any { it.data.arenaName == arenaName }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Já existe uma arena com este nome!")
                }
                return
            }

            val arena = XizumPluginConfig.XizumArenaConfig(
                arenaName,
                player.world.name,
                null,
                null,
            )

            m.createArena(arena)

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Arena §b${arenaName} §acriada com sucesso!")
            }
        }
    }

    inner class DreamXizumDeleteArenaCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val arenaName = optionalWord("arenaName")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val arenaId = args[options.arenaName] ?: run {
                context.sendMessage {
                    append(generateCommandInfo("dreamxizum deletearena", mapOf("<arenaName>" to "ID da arena (indexado (0, 1, 2)")))
                }
                return
            }

            if (m.arenas.none { it.data.arenaName == arenaId }) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Não existe uma arena com o nome especificado!")
                }
                return
            }

            m.deleteArena(arenaId)

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Arena deletada com sucesso!")
            }
        }
    }

    inner class DreamXizumSetPosCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val arenaName = optionalWord("arenaName")

            val forPlayer = optionalWord("forPlayer") { context, builder ->
                builder.suggest("player")
                builder.suggest("opponent")
            }
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()
            val arenaId = args[options.arenaName] ?: run {
                context.sendMessage {
                    append(generateCommandInfo("dreamxizum setpos", mapOf("<arenaName>" to "ID da arena (indexado (0, 1, 2)", "<forPlayer>" to "player/opponent")))
                }
                return
            }
            val forPlayer = args[options.forPlayer] ?: run {
                context.sendMessage {
                    append(generateCommandInfo("dreamxizum setpos", mapOf("<arenaName>" to "ID da arena (indexado (0, 1, 2)", "<forPlayer>" to "player/opponent")))
                }
                return
            }

            val arena = m.arenas.firstOrNull { it.data.arenaName == arenaId } ?: run {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Não existe uma arena com o ID especificado!")
                }
                return
            }

            val playerPosition = player.location

            when (forPlayer) {
                "player" -> {
                    arena.data.playerPos = XizumPluginConfig.Location(
                        playerPosition.x,
                        playerPosition.y,
                        playerPosition.z,
                        playerPosition.yaw,
                        playerPosition.pitch
                    )
                }
                "opponent" -> {
                    arena.data.opponentPos = XizumPluginConfig.Location(
                        playerPosition.x,
                        playerPosition.y,
                        playerPosition.z,
                        playerPosition.yaw,
                        playerPosition.pitch
                    )
                }
            }

            m.updateArena(arenaId, arena.data)

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Posição de §b$forPlayer §aatualizada com sucesso!")
            }
        }
    }

    inner class DreamXizumTeleportCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val arenaId = optionalWord("arenaName") { context, builder ->
                m.arenas.forEach { payload ->
                    builder.suggest(payload.data.arenaName.toString())
                }
            }
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()
            val arenaId = args[options.arenaId] ?: run {
                context.sendMessage {
                    append(generateCommandInfo("dreamxizum teleport", mapOf("<arenaName>" to "ID da arena (indexado (0, 1, 2)")))
                }
                return
            }

            val arena = m.getArena(arenaId) ?: run {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Não existe uma arena com o ID especificado!")
                }
                return
            }

            val playerPosition = arena.playerPos ?: run {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("A arena não possui uma posição de jogador!")
                }
                return
            }

            if (!player.teleport(playerPosition)) {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Não foi possível te teletransportar para a arena!")
                }
                return
            }

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Teleportado para a arena §b${arenaId} §acom sucesso!")
            }
        }
    }

    inner class DreamXizumSetSpectatorCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            val playerPosition = player.location

            m.config.spectatorPos = XizumPluginConfig.Location(
                playerPosition.x,
                playerPosition.y,
                playerPosition.z,
                playerPosition.yaw,
                playerPosition.pitch
            )

            m.updateConfigFile()

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Posição de espectador atualizada com sucesso!")
            }
        }
    }

    inner class DreamXizumSetArenaModeCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val arenaId = optionalWord("arenaName") { context, builder ->
                m.arenas.forEach { payload ->
                    builder.suggest(payload.data.arenaName.toString())
                }
            }

            val mode = optionalWord("mode") { context, builder ->
                builder.suggest("standard")
                builder.suggest("pvp_with_soup")
                builder.suggest("pvp_with_potion")
                builder.suggest("competitive")
            }
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val arenaId = args[options.arenaId] ?: run {
                context.sendMessage {
                    append(generateCommandInfo("dreamxizum setarenamode", mapOf("<arenaName>" to "ID da arena (indexado (0, 1, 2))", "<mode>" to "standard/pvp_with_soup/pvp_with_potion/competitive/custom")))
                }
                return
            }
            val mode = args[options.mode] ?: run {
                context.sendMessage {
                    append(generateCommandInfo("dreamxizum setarenamode", mapOf("<arenaName>" to "ID da arena (indexado (0, 1, 2))", "<mode>" to "standard/pvp_with_soup/pvp_with_potion/competitive/custom")))
                }
                return
            }

            val arena = m.arenas.firstOrNull { it.data.arenaName == arenaId } ?: run {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Não existe uma arena com o ID especificado!")
                }
                return
            }

            arena.data.mode = when (mode) {
                "standard" -> XizumBattleMode.STANDARD
                "pvp_with_soup" -> XizumBattleMode.PVP_WITH_SOUP
                "pvp_with_potion" -> XizumBattleMode.PVP_WITH_POTION
                "competitive" -> XizumBattleMode.COMPETITIVE
                else -> {
                    context.sendMessage {
                        append(DreamXizum.prefix())
                        appendSpace()
                        color(NamedTextColor.RED)
                        append("Modo inválido! Modos válidos: §bstandard§c, §bpvp_with_soup§c, §bpvp_with_potion§c, §bcompetitive")
                    }
                    return
                }
            }

            m.updateArena(arenaId, arena.data)

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Modo da arena atualizado com sucesso!")
            }
        }
    }

    inner class DreamXizumSetCombatPointsCommandExecutor : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val player = optionalWord("player") { _, builder ->
                Bukkit.getOnlinePlayers().forEach {
                    builder.suggest(it.name)
                }
            }

            val points = optionalInteger("points")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = args[options.player]?.let { Bukkit.getOfflinePlayer(it) } ?: run {
                context.sendMessage {
                    append(generateCommandInfo("dreamxizum setpdc", mapOf("<player>" to "Nome do jogador", "<points>" to "Quantidade de pontos de combate")))
                }
                return
            }
            val points = args[options.points] ?: run {
                context.sendMessage {
                    append(generateCommandInfo("dreamxizum setpdc", mapOf("<player>" to "Nome do jogador", "<points>" to "Quantidade de pontos de combate")))
                }
                return
            }

            val result = m.setRatingForPlayer(player.uniqueId, points) ?: run {
                context.sendMessage {
                    append(DreamXizum.prefix())
                    appendSpace()
                    color(NamedTextColor.RED)
                    append("Não foi possível atualizar os pontos de combate de ${player.name}!")
                }

                return
            }

            context.sendMessage {
                append(DreamXizum.prefix())
                appendSpace()
                color(NamedTextColor.GREEN)
                append("Pontos de combate de ${player.name} atualizados com sucesso! Agora ele tem ${result.rating} pontos de combate!")
            }
        }
    }
}