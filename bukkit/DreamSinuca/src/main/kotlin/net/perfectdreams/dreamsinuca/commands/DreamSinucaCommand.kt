package net.perfectdreams.dreamsinuca.commands

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamcore.utils.SparklyNamespacedKey
import net.perfectdreams.dreamcore.utils.commands.context.CommandArguments
import net.perfectdreams.dreamcore.utils.commands.context.CommandContext
import net.perfectdreams.dreamcore.utils.commands.declarations.SparklyCommandDeclarationWrapper
import net.perfectdreams.dreamcore.utils.commands.declarations.sparklyCommand
import net.perfectdreams.dreamcore.utils.commands.executors.SparklyCommandExecutor
import net.perfectdreams.dreamcore.utils.commands.options.CommandOptions
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.set
import net.perfectdreams.dreamsinuca.DreamSinuca
import net.perfectdreams.dreamsinuca.sinuca.PoolTable
import net.perfectdreams.dreamsinuca.sinuca.PoolTableData
import net.perfectdreams.dreamsinuca.sinuca.PoolTableOrientation
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.joml.Matrix4f

class DreamSinucaCommand(val m: DreamSinuca) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("dreamsinuca")) {
        permission = "dreamsinuca.setup"

        subcommand(listOf("spawn")) {
            permission = "dreamsinuca.setup"
            executor = SpawnSinucaExecutor(m)
        }
    }

    class SpawnSinucaExecutor(val m: DreamSinuca) : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val orientation = greedyString("orientation") { context, builder ->
                PoolTableOrientation.values().forEach {
                    builder.suggest(it.name.lowercase())
                }
            }
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val orientation = PoolTableOrientation.valueOf(args[options.orientation].uppercase())

            val player = context.requirePlayer()

            val spawnLocation = player.location
                .clone()

            // How it works?
            // The item display is always spawned at the center of where it should be
            spawnLocation.x = (player.location.blockX + 0.5)
            spawnLocation.z = (player.location.blockZ + 0.5)
            spawnLocation.yaw = when (orientation) {
                PoolTableOrientation.EAST -> 90f
                PoolTableOrientation.SOUTH -> 0f
            }
            spawnLocation.pitch = 0f

            val entity = player.world.spawn(
                spawnLocation,
                ItemDisplay::class.java
            ) {
                it.setItemStack(
                    ItemStack.of(Material.PAPER)
                        .meta<ItemMeta> {
                            itemModel = SparklyNamespacedKey("pool_table")
                        }
                )

                it.persistentDataContainer.set(
                    DreamSinuca.POOL_TABLE_ENTITY,
                    Json.encodeToString<PoolTableData>(PoolTableData(orientation))
                )

                it.setTransformationMatrix(
                    Matrix4f()
                        .translate(0f, 4f, 0f)
                        .scale(8f)
                )
            }

            val poolTable = PoolTable(m, entity, orientation)
            poolTable.makeBarrierBlocks()
            poolTable.configure()
            m.poolTables[entity] = poolTable
        }
    }
}