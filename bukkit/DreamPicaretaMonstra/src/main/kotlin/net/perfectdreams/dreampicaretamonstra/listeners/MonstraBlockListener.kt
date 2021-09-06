package net.perfectdreams.dreampicaretamonstra.listeners

import me.ryanhamshire.GriefPrevention.GriefPrevention
import net.perfectdreams.dreamcore.utils.BlockUtils
import net.perfectdreams.dreamcore.utils.GeometryUtils
import net.perfectdreams.dreamcore.utils.PlayerUtils
import net.perfectdreams.dreamcore.utils.chance
import net.perfectdreams.dreamcore.utils.extensions.getStoredMetadata
import net.perfectdreams.dreamcustomitems.utils.CustomItems
import net.perfectdreams.dreampicaretamonstra.DreamPicaretaMonstra
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.ExperienceOrb
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta

class MonstraBlockListener(val m: DreamPicaretaMonstra) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) {
        val inHand = e.player.inventory.itemInMainHand

        if (inHand.type != Material.NETHERITE_PICKAXE && inHand.type != Material.DIAMOND_SHOVEL) {
            return
        }

        if (inHand.getStoredMetadata("isMonsterPickaxe") != "true")
            return

        val damageable = inHand.itemMeta as Damageable
        m.logger.info("Player ${e.player.name} used a Picareta Monstra at ${e.player.world.name} ${e.block.location.x}, ${e.block.location.y}, ${e.block.location.z}. Damage value: ${damageable.damage}")

        if (e.block.world.name == "MinaRecheada")
            return

        val isInClaim = GriefPrevention.instance.dataStore
                .getClaimAt(e.block.location, false, null)

        if (e.block.world.name == "world" && isInClaim == null) {
            e.player.sendMessage("§cVocê só pode usar a picareta monstra no seu terreno! Se você quer sair quebrando tudo, proteja o terreno ou vá no mundo de recursos, §6/warp recursos")
            return
        }

        val broken = e.block
        val heldItemType = inHand.type

        if (m.isValidForHeldItem(heldItemType, broken)) {
            e.isCancelled = true

            val blocks = GeometryUtils.sphere(e.block.location, 2, false) as Set<Location>
            var enchantmentLevel = 0
            var efficiencyLevel = 0
            var isSilky = false
            enchantmentLevel = inHand.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS)
            efficiencyLevel = inHand.getEnchantmentLevel(Enchantment.DURABILITY)
            isSilky = inHand.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0
            var below = false
            if (e.player.location.y > e.block.y) {
                below = true
            }
            var shouldPlay = true

            for (location in blocks) {
                if ((below || location.blockY > e.player.location.y - 1.0) && location.block.type !== Material.AIR && location.block.type !== Material.BEDROCK && m.isValidForHeldItem(heldItemType, location.block)) {
                    val center = location.add(0.0, 0.5, 0.0)
                    val claim = GriefPrevention.instance.dataStore
                            .getClaimAt(location, false, null)

                    if (PlayerUtils.canBreakAt(location, e.player, location.block.type) && (e.player.world.name != "world" || (e.player.world.name == "world" && claim != null))) {
                        if (chance(100.0 / (efficiencyLevel + 1))) {
                            if ((damageable.damage + 1) == inHand.type.maxDurability.toInt()) {
                                val name = if (heldItemType == Material.NETHERITE_PICKAXE)
                                    "picareta monstra"
                                else
                                    "pá monstra"

                                e.player.sendMessage("§cCuidado, a sua $name irá quebrar! Para te proteger, a gente te bloqueou de quebrar mais blocos para você poder reparar ela!")
                                return
                            }

                            damageable.damage = damageable.damage + 1
                            inHand.itemMeta = damageable as ItemMeta
                        }

                        if (damageable.damage > inHand.type.maxDurability) {
                            e.player.inventory.removeItem(inHand)
                        }

                        val drops = location.block.getDrops(inHand)
                                .toMutableList()
                        val exp = BlockUtils.getExpCount(location.block, enchantmentLevel)

                        if (!isSilky && location.block.type == Material.REDSTONE_ORE && CustomItems.checkIfRubyShouldDrop()) {
                            drops.add(CustomItems.RUBY.clone())
                        }

                        // Using "dropItemNaturally" is kinda bad because the item can stay inside of blocks
                        val dropsAsItems =  drops.map {
                            location.world.dropItem(
                                    location,
                                    it
                            )
                        }

                        m.doMcMMOStuff(e.player, e.block.state, dropsAsItems)

                        // Do not update physics, this tries to avoid a lot of "notify()" calls
                        // See: https://cdn.discordapp.com/attachments/513405772911345664/869387512924209182/wLnOZAeMZ5.sparkprofile
                        location.block.setType(Material.AIR, false)

                        if (exp > 0 && !isSilky) {
                            val orb = location.block.world.spawnEntity(center, EntityType.EXPERIENCE_ORB) as ExperienceOrb
                            orb.experience = exp
                        }

                        if (location.block.type != Material.AIR) {
                            if (shouldPlay) {
                                e.player.world.playSound(broken.location, Sound.ENTITY_PLAYER_LEVELUP, 0.15f, 1.25f)
                                shouldPlay = false
                            }
                            location.world.spawnParticle(Particle.VILLAGER_HAPPY, center, 1)
                            location.world.spawnParticle(Particle.FIREWORKS_SPARK, center, 1)
                        } else {
                            location.world.spawnParticle(Particle.SPELL_WITCH, center, 1)
                        }
                    } else {
                        if (location.block.type === Material.AIR) {
                            continue
                        }
                        location.world.spawnParticle(Particle.VILLAGER_ANGRY, center, 1)
                    }
                }
            }
        }
    }
}
