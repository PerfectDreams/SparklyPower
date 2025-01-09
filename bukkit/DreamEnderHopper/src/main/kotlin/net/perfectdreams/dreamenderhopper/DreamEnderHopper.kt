package net.perfectdreams.dreamenderhopper

import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcustomitems.DreamCustomItems
import net.perfectdreams.dreamcustomitems.utils.CustomCraftingRecipe
import net.perfectdreams.dreamcustomitems.utils.CustomItems
import net.perfectdreams.dreamenderhopper.commands.declarations.DreamEnderHopperDeclaration
import net.perfectdreams.dreamenderhopper.listeners.EnderHopperListener
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

class DreamEnderHopper : KotlinPlugin(), Listener {
	companion object {
		val HOPPER_TELEPORTER = SparklyNamespacedBooleanKey("hopper_teleporter")
		val HOPPER_COORDINATES = SparklyNamespacedKey("hopper_coordinates", PersistentDataType.STRING)
	}

	override fun softEnable() {
		super.softEnable()

		registerEvents(EnderHopperListener(this))
		registerCommand(DreamEnderHopperDeclaration(this))

		DreamCustomItems.registerCustomRecipe(
			CustomCraftingRecipe(
				this,
				CustomCraftingRecipe.RUBY_REMAP,
				true,
				addRecipe(
					"enderhopper",
					createEnderHopper(),
					listOf(
						"IEI",
						"IHI",
						"DID"
					)
				) {
					it.setIngredient('D', Material.DIAMOND)
					it.setIngredient('H', Material.HOPPER)
					it.setIngredient('E', Material.ENDER_EYE)
					it.setIngredient('I', Material.IRON_INGOT)
				}
			)
		)
	}

	override fun softDisable() {
		super.softDisable()
	}

	fun createEnderHopper() = ItemStack(Material.HOPPER)
		.rename("§5§lEnder Hopper")
		.lore("§7Funil que teletransporta itens para outros lugares")
		.meta<ItemMeta> {
			persistentDataContainer.set(HOPPER_TELEPORTER, true)
			addEnchant(Enchantment.UNBREAKING, 1, true)
			addItemFlags(ItemFlag.HIDE_ENCHANTS)
		}
}