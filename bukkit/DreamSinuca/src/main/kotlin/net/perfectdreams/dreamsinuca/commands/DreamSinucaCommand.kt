package net.perfectdreams.dreamsinuca.commands

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
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.joml.Matrix4f

class DreamSinucaCommand(val m: DreamSinuca) : SparklyCommandDeclarationWrapper {
    override fun declaration() = sparklyCommand(listOf("dreamsinuca")) {
        subcommand(listOf("spawn")) {
            permission = "dreamsinuca.setup"
            executor = SpawnSinucaExecutor(m)
        }
    }

    class SpawnSinucaExecutor(val m: DreamSinuca) : SparklyCommandExecutor() {
        inner class Options : CommandOptions() {
            val player = player("player")
        }

        override val options = Options()

        override fun execute(context: CommandContext, args: CommandArguments) {
            val player = context.requirePlayer()

            val spawnLocation = player.location
                .clone()

            spawnLocation.x = player.location.blockX + 0.5
            spawnLocation.z = player.location.blockZ + 0.5
            spawnLocation.yaw = 90f
            spawnLocation.pitch = 0f

            val entity = player.world.spawn(
                spawnLocation,
                ItemDisplay::class.java
            ) {
                it.setItemStack(
                    ItemStack.of(Material.PAPER)
                        .meta<ItemMeta> {
                            itemModel = SparklyNamespacedKey("pool_table")
                            persistentDataContainer.set(DreamSinuca.POOL_TABLE_ENTITY, true)
                        }
                )

                it.setTransformationMatrix(
                    Matrix4f()
                        .translate(0f, 4f, 0f)
                        .scale(8f)
                )
            }

            for (z in -1..1) {
                for (x in -2..2) {
                    spawnLocation.clone().add(x.toDouble(), 0.0, z.toDouble()).block.type = Material.BARRIER
                }
            }
        }
    }
}