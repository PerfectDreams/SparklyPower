package net.perfectdreams.dreammoverspawners

import com.gmail.nossr50.events.skills.repair.McMMOPlayerRepairCheckEvent
import com.gmail.nossr50.events.skills.salvage.McMMOPlayerSalvageCheckEvent
import com.okkero.skedule.schedule
import net.perfectdreams.commands.annotation.Subcommand
import net.perfectdreams.commands.bukkit.SparklyCommand
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.extensions.toItemStack
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.CreatureSpawner
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

class DreamMoverSpawners : KotlinPlugin(), Listener {
    companion object {
        val IS_MOVE_SPAWNERS_KEY = SparklyNamespacedBooleanKey("is_move_spawners_tool")
        val SPAWNER_TYPE_KEY = SparklyNamespacedKey("spawner_type", PersistentDataType.STRING)

        fun isMoveSpawnersTool(item: ItemStack) = item.hasItemMeta() && item.itemMeta.persistentDataContainer.get(IS_MOVE_SPAWNERS_KEY)
    }

    val onlyInWorld = listOf("world")
    val onlyInNether = listOf("nether")
    val defaultBreakPermission = "dreammoverspawners.breakdefault"
    val specialBreakPermission = "dreammoverspawners.breakspecial"

    val validSpawners = listOf(
        MobSpawnerBreakable(
            EntityType.ZOMBIE,
            onlyInWorld,
            defaultBreakPermission
        ),
        MobSpawnerBreakable(
            EntityType.CAVE_SPIDER,
            onlyInWorld,
            defaultBreakPermission
        ),
        MobSpawnerBreakable(
            EntityType.SPIDER,
            onlyInWorld,
            defaultBreakPermission
        ),
        MobSpawnerBreakable(
            EntityType.CREEPER,
            onlyInWorld,
            defaultBreakPermission
        ),
        MobSpawnerBreakable(
            EntityType.SKELETON,
            onlyInWorld,
            defaultBreakPermission
        ),
        MobSpawnerBreakable(
            EntityType.PIGLIN,
            onlyInNether + onlyInWorld,
            specialBreakPermission
        ),
        MobSpawnerBreakable(
            EntityType.BLAZE,
            onlyInNether + onlyInWorld,
            specialBreakPermission
        ),
        MobSpawnerBreakable(
            EntityType.MAGMA_CUBE,
            onlyInNether + onlyInWorld,
            specialBreakPermission
        )
    )

    override fun softEnable() {
        super.softEnable()

        registerCommand(object: SparklyCommand(arrayOf("moverspawners"), permission = "sparklypower.moverspawners") {
            @Subcommand
            fun spawn(player: Player) {
                val eachCount = player.world.entities.groupingBy { it.type }.eachCount()
                eachCount.entries.sortedByDescending { it.value }.first().key
                player.inventory.addItem(
                    ItemStack(Material.GOLDEN_PICKAXE)
                        .rename("§6§lPicareta de Mover Spawners")
                        .lore("§7Querendo mover spawners para outros lugares?", "§7Então utilize a incrível picareta de mover spawners!", "§7", "§7Cuidado que ela quebra bem rápido!")
                        .apply {
                            this.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                            this.addUnsafeEnchantment(Enchantment.INFINITY, 1)
                        }
                        .meta<ItemMeta> {
                            this.persistentDataContainer.set(IS_MOVE_SPAWNERS_KEY, true)
                        }
                )
            }
        })

        registerEvents(this)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRepair(e: McMMOPlayerRepairCheckEvent) {
        if (isMoveSpawnersTool(e.repairedObject)) {
            e.isCancelled = true
            e.player.sendMessage("§cVocê não pode reparar uma picareta de mover spawners!")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSalvage(e: McMMOPlayerSalvageCheckEvent) {
        if (isMoveSpawnersTool(e.salvageItem)) {
            e.isCancelled = true
            e.player.sendMessage("§cVocê não pode salvar uma picareta de mover spawners!")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(e: BlockPlaceEvent) {
        if (e.itemInHand.type != Material.SPAWNER)
            return

        val center = e.block.location.add(0.5, 0.5, 0.5)
        val spawnerType = e.itemInHand.itemMeta.persistentDataContainer.get(SPAWNER_TYPE_KEY) ?: return
        val mobType = EntityType.valueOf(spawnerType)
        val spawnerX = validSpawners.firstOrNull { it.type == mobType }
        if (spawnerX == null) {
            e.isCancelled = true
            e.player.sendMessage("§cDesculpe, mas o meu poder não permite você colocar esses tipos de spawners...")
            center.world.spawnParticle(Particle.WITCH, center, 1)
            return
        }

        if (!e.player.hasPermission(spawnerX.requiredPermission)) {
            e.isCancelled = true
            e.player.sendMessage("§cDesculpe, mas você não tem permissão para colocar esse spawner no chão... Ele é poderoso demais para você!")
            center.world.spawnParticle(Particle.WITCH, center, 1)
            return
        }

        val spawner = e.blockPlaced.state as CreatureSpawner
        spawner.spawnedType = mobType
        spawner.update()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) {
        val inHand = e.player.inventory.itemInMainHand

        if (e.player.inventory.itemInMainHand.type != Material.GOLDEN_PICKAXE)
            return

        if (!isMoveSpawnersTool(inHand))
            return

        if (inHand.hasItemMeta() && !inHand.itemMeta.hasCustomModelData()) {
            inHand.itemMeta = inHand.itemMeta.apply {
                setCustomModelData(1)
            }
        }

        if (e.block.world.name == "MinaRecheada")
            return

        for (enchantment in inHand.enchantments.filter { it.key != Enchantment.INFINITY })
            inHand.removeEnchantment(enchantment.key)

        val broken = e.block

        if (broken.type == Material.SPAWNER) {
            val mobSpawner = broken.state as CreatureSpawner
            val type = mobSpawner.spawnedType

            e.isCancelled = true

            // I don't know how, and why, the type can be null
            // But oh well
            if (type == null)
                return

            val center = e.block.location.add(0.5, 0.5, 0.5)

            schedule {
                repeat(10) {
                    center.world.spawnParticle(Particle.HAPPY_VILLAGER, center, 1)
                    waitFor(20L)
                }
            }

            val spawner = validSpawners.firstOrNull { it.type == type }
            if (spawner == null) {
                e.player.sendMessage("§cDesculpe, mas o meu poder não permite quebrar esses tipos de spawners...")
                center.world.spawnParticle(Particle.WITCH, center, 1)
                return
            }

            if (!e.player.hasPermission(spawner.requiredPermission)) {
                e.player.sendMessage("§cDesculpe, mas você não tem permissão para quebrar esse spawner... Ele é poderoso demais para você!")
                center.world.spawnParticle(Particle.WITCH, center, 1)
                return
            }

            var newDurability = e.player.inventory.itemInMainHand.durability

            newDurability = when {
                e.player.hasPermission("dreammoverspawners.vip++") -> {
                    (newDurability + 1).toShort()
                }
                e.player.hasPermission("dreammoverspawners.vip+") -> {
                    (newDurability + 2).toShort()
                }
                else -> {
                    (newDurability + 4).toShort()
                }
            }

            e.player.inventory.itemInMainHand.durability = newDurability

            if (newDurability >= 32.toShort()) {
                e.player.inventory.setItemInMainHand(ItemStack(Material.AIR))
                e.player.sendMessage("§cSua picareta de mover spawners quebrou!")
            }

            val drops = listOf(
                Material.SPAWNER.toItemStack()
                    .lore("§7Spawner de §a${type.name}")
                    .meta<ItemMeta> {
                        persistentDataContainer.set(SPAWNER_TYPE_KEY, type.toString())
                    }
            )

            e.block.type = Material.AIR // rip

            // Using "dropItemNaturally" is kinda bad because the item can stay inside of blocks
            e.block.world.dropItem(center, drops.first())

            center.world.spawnParticle(Particle.HAPPY_VILLAGER, center, 8, 1.0, 1.0, 1.0)
            center.world.spawnParticle(Particle.FIREWORK, center, 8, 1.0, 1.0, 1.0)
            e.player.playSound(center, Sound.BLOCK_ANVIL_LAND, 1f, 1f)
        } else {
            e.isCancelled = true
            e.player.sendMessage("§cVocê está tentando quebrar um bloco normal com a picareta de mover spawners! Não faça isso, economize a durabilidade dela <3")
        }
    }

    data class MobSpawnerBreakable(
        val type: EntityType,
        val allowedWorlds: List<String>,
        val requiredPermission: String
    )
}