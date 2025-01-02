package net.perfectdreams.dreamxizum.modes.vanilla

import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamxizum.DreamXizum
import net.perfectdreams.dreamxizum.modes.AbstractXizumBattleMode
import net.perfectdreams.dreamxizum.utils.XizumBattleMode
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class PvPWithPotionXizumMode(m: DreamXizum) : AbstractXizumBattleMode(XizumBattleMode.PVP_WITH_POTION, m) {
    override fun setupInventory(players: Pair<Player, Player>) {
        val helmet = ItemStack(Material.DIAMOND_HELMET)
        helmet.addEnchantments(
            mapOf(
                Enchantment.PROTECTION to 4,
                Enchantment.UNBREAKING to 3
            )
        )

        val chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
        chestplate.addEnchantments(
            mapOf(
                Enchantment.PROTECTION to 4,
                Enchantment.UNBREAKING to 3
            )
        )

        val leggings = ItemStack(Material.DIAMOND_LEGGINGS)
        leggings.addEnchantments(
            mapOf(
                Enchantment.PROTECTION to 4,
                Enchantment.UNBREAKING to 3
            )
        )

        val boots = ItemStack(Material.DIAMOND_BOOTS)
        boots.addEnchantments(
            mapOf(
                Enchantment.PROTECTION to 4,
                Enchantment.UNBREAKING to 3
            )
        )

        val sword = ItemStack(Material.DIAMOND_SWORD)
        sword.addEnchantments(
            mapOf(
                Enchantment.SHARPNESS to 5,
                Enchantment.FIRE_ASPECT to 2,
                Enchantment.UNBREAKING to 3
            )
        )

        val speedPotion = ItemStack(Material.POTION).meta<PotionMeta> {
            displayName(textComponent {
                append("§a§lPoção de Velocidade")
            })
            addCustomEffect(PotionEffect(PotionEffectType.SPEED, 20 * 60 * 3, 1), true)
        }

        val fireResistancePotion = ItemStack(Material.POTION).meta<PotionMeta> {
            displayName(textComponent {
                append("§a§lPoção de Resistência ao Fogo")
            })
            addCustomEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60 * 3, 1), true)
        }

        val healthPotion = ItemStack(Material.SPLASH_POTION, 1).meta<PotionMeta> {
            displayName(textComponent {
                append("§a§lPoção de Cura")
            })
            addCustomEffect(PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 2), true)
        }

        for (player in players.toList()) {
            player.inventory.clear()

            player.inventory.armorContents = arrayOf(
                boots,
                leggings,
                chestplate,
                helmet
            )

            player.inventory.setItem(0, sword)
            player.inventory.setItem(2, speedPotion)
            player.inventory.setItem(3, fireResistancePotion)
            player.inventory.setItem(8, ItemStack(Material.COOKED_BEEF, 64))
            player.inventory.setItem(1, ItemStack(Material.ENDER_PEARL, 16))

            for (i in 1..32) {
                player.inventory.addItem(healthPotion)
            }
        }
    }

    override fun addAfterCountdown(players: Pair<Player, Player>) {
       // don't need to do anything here
    }
}