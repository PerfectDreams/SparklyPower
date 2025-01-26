package net.perfectdreams.dreamvipstuff.commands

import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.ClaimPermission
import me.ryanhamshire.GriefPrevention.GriefPrevention
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamterrainadditions.DreamTerrainAdditions
import net.perfectdreams.dreamvipstuff.DreamVIPStuff
import java.util.UUID

class BackCommand(val m: DreamVIPStuff) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("back")) {
        executor = BackCommandExecutor()
        permission = "dreamvipstuff.back"
    }

    inner class BackCommandExecutor : SparklyCommandExecutor() {
        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()
            val griefPrevention = GriefPrevention.instance
            val additions = DreamTerrainAdditions.INSTANCE

            val prohibitedWorlds = DreamCore.dreamConfig.blacklistedWorldsTeleport
            val oldLocation = m.storedLocations[player.uniqueId] ?: run {
                return context.sendMessage {
                    append("Você não se teleportou recentemente!") {
                        color(NamedTextColor.RED)
                    }
                }
            }

            val claim = griefPrevention.dataStore.getClaimAt(oldLocation, true, null)

            if (claim != null) {
                val claimAdditions = additions.getOrCreateClaimAdditionsWithId(claim.id)

                val isPlayerBanned = claimAdditions.bannedPlayers.contains(player.name)
                val isClaimWithBlockPlayersEnabled = claimAdditions.blockAllPlayersExceptTrusted
                val isPlayerTrusted = claim.playerHasAnyPermission(player.uniqueId)
                val isPlayerOwner = claim.ownerID == player.uniqueId

                // trying to teleport to a claim that the player is banned from
                // check if the player has trust or if the player is the owner of the claim
                if ((isPlayerBanned || isClaimWithBlockPlayersEnabled) && !(isPlayerTrusted || isPlayerOwner)) {
                    return context.sendMessage {
                        append("Não há como você voltar ao terreno em que estava. O dono do terreno te baniu ou bloqueou o acesso de jogadores!") {
                            color(NamedTextColor.RED)
                        }
                    }
                }
            }

            // trying to teleport to prohibited worlds
            if (oldLocation.world?.name in prohibitedWorlds) {
                m.storedLocations.remove(player.uniqueId) // remove the stored location

                return context.sendMessage {
                    append("Você não pode voltar para o mundo em que estava!") {
                        color(NamedTextColor.RED)
                    }
                }
            }

            // teleport the player back
            player.teleportAsync(oldLocation).thenRun {
                context.sendMessage {
                    append("(∩*´∀` )⊃━☆ﾟ.*･｡ﾟ whoosh! Teleportado com sucesso!") {
                        color(NamedTextColor.GREEN)
                    }
                }

                m.storedLocations.remove(player.uniqueId)
            }
        }
    }

    private fun Claim.playerHasAnyPermission(player: UUID) = ClaimPermission.entries.any { hasExplicitPermission(player, it) }
}