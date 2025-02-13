package net.perfectdreams.dreamquickharvest.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.*
import net.perfectdreams.dreamcash.utils.Cash
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.scheduler.onMainThread
import net.perfectdreams.dreamquickharvest.DreamQuickHarvest
import net.perfectdreams.dreamquickharvest.tables.PlayerQuickHarvestUpgrades
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class ColheitaUpgradeExecutor(val m: DreamQuickHarvest) : SparklyCommandExecutor() {
    override fun execute(context: CommandContext, args: CommandArguments) {
        val player = context.requirePlayer()

        m.launchAsyncThread {
            val boughtEnergy = net.perfectdreams.exposedpowerutils.sql.transaction(
                Dispatchers.IO,
                Databases.databaseNetwork
            ) {
                val sumField = PlayerQuickHarvestUpgrades.energy.sum()

                PlayerQuickHarvestUpgrades.slice(sumField).select {
                    PlayerQuickHarvestUpgrades.playerId eq player.uniqueId and (PlayerQuickHarvestUpgrades.expiresAt greaterEq Instant.now())
                }.firstOrNull()
                    ?.getOrNull(sumField)
            } ?: 0

            onMainThread {
                val menu = createMenu(9, "Upgrade") {
                    // 1000 - 100
                    // 2100 - 200
                    // 3200 - 300
                    // 4300 - 400
                    // 5400 - 500
                    // 6500 - 600
                    for ((index, upgrade) in getUpgrades(boughtEnergy.toLong()).withIndex()) {
                        slot(index) {
                            item = ItemStack(upgrade.material)
                                .rename("§e+${upgrade.energy} energia§a por ${upgrade.pesadelos} pesadelos")
                                .lore(
                                    "§7Aumenta a sua energia, o upgrade dura 30 dias!",
                                    "§7",
                                    "§7Quanto mais energias ativas você possui, mais caro fica",
                                    "§7o preço para fazer upgrade."
                                )

                            onClick {
                                it.closeInventory()

                                InventoryUtils.askForConfirmation(
                                    player,
                                    afterAccept = {
                                        it.closeInventory()

                                        m.launchAsyncThread {
                                            val cash = Cash.getCash(player)

                                            if (upgrade.pesadelos > cash) {
                                                onMainThread {
                                                    player.sendMessage("§cVocê não tem pesadelos suficientes para este upgrade!")
                                                }
                                                return@launchAsyncThread
                                            }

                                            val now = Clock.System.now()
                                            transaction(Databases.databaseNetwork) {
                                                PlayerQuickHarvestUpgrades.insert {
                                                    it[PlayerQuickHarvestUpgrades.playerId] = player.uniqueId
                                                    it[PlayerQuickHarvestUpgrades.energy] = upgrade.energy
                                                    it[PlayerQuickHarvestUpgrades.boughtAt] = now.toJavaInstant()
                                                    it[PlayerQuickHarvestUpgrades.expiresAt] = now.plus(
                                                        30,
                                                        DateTimeUnit.DAY,
                                                        TimeZone.of("America/Sao_Paulo")
                                                    ).toJavaInstant()
                                                }
                                            }

                                            Cash.takeCash(player, upgrade.pesadelos, TransactionContext(extra = "upgrade de ${upgrade.energy} energia no sistema de colheita rápida"))

                                            onMainThread {
                                                player.sendMessage("§aUpgrade aplicado!")
                                            }
                                        }
                                    },
                                    afterDecline = {
                                        it.closeInventory()
                                    }
                                )
                            }
                        }
                    }
                }

                menu.sendTo(player)
            }
        }
    }

    private fun getUpgrades(currentBoughtEnergy: Long): List<EnergyUpgrade> {
        fun calculateWithCurrentBoughtEnergy(priceTarget: Long) = (priceTarget + (priceTarget * (currentBoughtEnergy / 6_500.0))).toLong()

        return listOf(
            EnergyUpgrade(
                Material.COAL_BLOCK,
                1_000,
                calculateWithCurrentBoughtEnergy(100)
            ),
            EnergyUpgrade(
                Material.IRON_BLOCK,
                2_100,
                calculateWithCurrentBoughtEnergy(200)
            ),
            EnergyUpgrade(
                Material.GOLD_BLOCK,
                3_200,
                calculateWithCurrentBoughtEnergy(300)
            ),
            EnergyUpgrade(
                Material.DIAMOND_BLOCK,
                4_300,
                calculateWithCurrentBoughtEnergy(400)
            ),
            EnergyUpgrade(
                Material.EMERALD_BLOCK,
                5_400,
                calculateWithCurrentBoughtEnergy(500)
            ),
            EnergyUpgrade(
                Material.NETHERITE_BLOCK,
                6_500,
                calculateWithCurrentBoughtEnergy(600)
            )
        )
    }

    private data class EnergyUpgrade(
        val material: Material,
        val energy: Int,
        val pesadelos: Long
    )
}