package net.perfectdreams.dreamcaixasecreta

import club.minnced.discord.webhook.WebhookClient
import com.xxmicloxx.NoteBlockAPI.model.Song
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.perfectdreams.commands.annotation.Subcommand
import net.perfectdreams.commands.bukkit.SparklyCommand
import net.perfectdreams.dreamcaixasecreta.listeners.BlockListener
import net.perfectdreams.dreamcaixasecreta.listeners.CraftListener
import net.perfectdreams.dreamcaixasecreta.utils.RandomItem
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.extensions.toItemStack
import net.perfectdreams.dreamcustomitems.DreamCustomItems
import net.perfectdreams.dreamcustomitems.items.SparklyItemsRegistry
import net.perfectdreams.dreamcustomitems.utils.CustomCraftingRecipe
import net.perfectdreams.dreamcustomitems.utils.CustomItems
import net.perfectdreams.dreamjetpack.DreamJetpack
import org.bukkit.JukeboxSong
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class DreamCaixaSecreta : KotlinPlugin() {
	companion object {
		val CAIXA_SECRETA_LEVEL_KEY = SparklyNamespacedKey("caixa_secreta_level", PersistentDataType.INTEGER)
		val CAIXA_SECRETA_WORLD_KEY = SparklyNamespacedKey("caixa_secreta_world", PersistentDataType.STRING)
	}

	lateinit var itemReceived: Song
	var prizes = mutableListOf<RandomItem>()

	val nitroNotifyWebhook = WebhookClient.withUrl(config.getString("nitro-notify")!!)
	val COMBINE_BOXES_KEY = SparklyNamespacedKey("combine_secret_boxes")

	override fun softEnable() {
		super.softEnable()

		DreamCustomItems.registerCustomRecipe(
			CustomCraftingRecipe(
				this,
				CustomCraftingRecipe.RUBY_REMAP,
				true,
				addRecipe(
					COMBINE_BOXES_KEY,
					ShapelessRecipe(
						COMBINE_BOXES_KEY, Material.CHEST.toItemStack()
					).addIngredient(2, Material.CHEST)
				)
			)
		)

		itemReceived = NBSDecoder.parse(File(dataFolder, "item-received.nbs"))
		registerEvents(BlockListener(this))
		registerEvents(CraftListener(this))

		registerCommand(object: SparklyCommand(arrayOf("caixasecretagen"), permission = "sparkly.anybox") {
			@Subcommand
			fun test(player: Player, level: String) {
				player.sendMessage("§eGerando caixa secreta...")
				player.inventory.addItem(generateCaixaSecreta(level.toIntOrNull()))
				player.sendMessage("§aCaixa gerada e adicionada!")
			}
		})

		var chance = 0.05
		for (enchantment in Enchantment.values()) {
			for (level in enchantment.startLevel..enchantment.maxLevel) {
				prizes.add(
					RandomItem(
						ItemStack(
							Material.ENCHANTED_BOOK,
						).meta<EnchantmentStorageMeta> {
							this.addStoredEnchant(enchantment, level, false)
						},
						chance
					)
				)
			}
		}

		chance = 0.1

		prizes.add(
			RandomItem(
				ItemStack(
					Material.BEACON
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.NETHERITE_INGOT
				), chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_club_classics").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_motteke_sailor_fuku").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_ta_facil_dizer_que_me_ama").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_two_months_off").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_live_and_learn").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_take_my_time").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_bad_apple").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_chala_head_chala").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_hino_nacional_brasil").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("music_disc_fallen_kingdom").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("moai_head").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("shikanoko_antlers").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("bunny_ears").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("deal_with_it_glasses").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("shark_blahaj").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("pomni_jester_hat").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("squirrel_hat").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("sleepy_squirrel").createItemStack(),
				chance
			)
		)

		val ldt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))

		val startChristmas = LocalDateTime.of(2024, 12, 1, 0, 0, 0, 0)
		val endChristmas = LocalDateTime.of(2025, 1, 1, 0, 0, 0, 0)

		if (ldt in startChristmas..endChristmas) {
			prizes.add(
				RandomItem(
					SparklyItemsRegistry.getItemById("santa_hat").createItemStack(),
					chance
				)
			)

			prizes.add(
				RandomItem(
					SparklyItemsRegistry.getItemById("candy_cane_pickaxe").createItemStack(),
					chance
				)
			)
		}

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_pandastica_hora_de_arrepiar").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_meu_deus_um_anjo").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_smart_dachshund").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_bliss_kawaii").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_lori_lick").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_pantufa_lick").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_gabi_lick").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_power_lick").createItemStack(),
				chance
			)
		)

		prizes.add(
			RandomItem(
				SparklyItemsRegistry.getItemById("painting_gessy_lick").createItemStack(),
				chance
			)
		)

		chance = 0.2

		prizes.add(
			RandomItem(
				ItemStack(
					Material.NETHER_STAR
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.NETHERITE_SCRAP
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.CONDUIT
				), chance
			)
		)
		chance = 0.5
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DIAMOND_HELMET
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DIAMOND_CHESTPLATE
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DIAMOND_LEGGINGS
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DIAMOND_BOOTS
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DIAMOND_SWORD
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DIAMOND_PICKAXE
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DIAMOND_SHOVEL
				), chance, true
			)
		)
		chance = 1.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.IRON_HELMET
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.IRON_CHESTPLATE
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.IRON_LEGGINGS
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.IRON_BOOTS
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.IRON_SWORD
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.IRON_PICKAXE
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.IRON_SHOVEL
				), chance, true
			)
		)
		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLDEN_HELMET
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLDEN_CHESTPLATE
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLDEN_LEGGINGS
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLDEN_BOOTS
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLDEN_SWORD
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLDEN_PICKAXE
				), chance, true
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLDEN_SHOVEL
				), chance, true
			)
		)
		chance = 1.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.ANVIL
				), chance
			)
		)
		chance = 1.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLDEN_APPLE,
					1
				), chance
			)
		)

		for (material in Material.values().filter { !it.isLegacy && it.isItem }) {
			if (material.name.startsWith("MUSIC_DISC_")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							1
						),
						0.1
					)
				)
			}

			if (material.name.endsWith("STAINED_GLASS")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							4
						), chance
					)
				)
			}

			if (material.name.endsWith("STAINED_GLASS_PANE")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							8
						), chance
					)
				)
			}

			if (material.name.endsWith("_CONCRETE") && material != Material.SPARKLYPOWER_RAINBOW_CONCRETE) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							8
						), chance
					)
				)
			}

			if (material.name.endsWith("CONCRETE_POWDER")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							8
						), chance
					)
				)
			}

			if (material.name.endsWith("CORAL_BLOCK")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							2
						), chance
					)
				)
			}

			if (material.name.endsWith("CORAL")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							2
						), chance
					)
				)
			}

			if (material.name.endsWith("CORAL_FAN")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							2
						), chance
					)
				)
			}

			if (material.name.endsWith("CANDLE")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							2
						), chance
					)
				)
			}

			if (material.name.endsWith("TERRACOTTA")) {
				prizes.add(
					RandomItem(
						ItemStack(
							material,
							8
						), chance
					)
				)
			}
		}

		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.SLIME_BLOCK
				), chance
			)
		)

		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.FIRE_CHARGE
				), chance
			)
		)

		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.SHULKER_BOX
				), chance
			)
		)

		chance = 1.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.TURTLE_SCUTE
				), chance
			)
		)

		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.CHAINMAIL_CHESTPLATE
				).rename("§6§lJetpack")
					.meta<ItemMeta> {
						this.persistentDataContainer.set(DreamJetpack.IS_JETPACK_KEY, true)
					}

				, chance
			)
		)
		chance = 3.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.HOPPER
				), chance
			)
		)
		chance = 3.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.SPONGE,
					2
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.EMERALD
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.QUARTZ_BLOCK,
					16
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.EGG,
					16
				), chance
			)
		)
		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.FIREWORK_ROCKET,
					4
				), chance
			)
		)
		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GILDED_BLACKSTONE,
					4
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.AMETHYST_BLOCK,
					4
				), chance
			)
		)
		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.CHAIN,
					8
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.AMETHYST_SHARD,
					8
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.CHISELED_QUARTZ_BLOCK,
					16
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.QUARTZ_PILLAR,
					16
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.JUKEBOX
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.NOTE_BLOCK
				), chance
			)
		)
		chance = 4.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.EXPERIENCE_BOTTLE,
					16
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.OAK_LOG,
					64
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.ACACIA_LOG,
					64
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.BIRCH_LOG,
					64
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DARK_OAK_LOG,
					64
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.SPRUCE_LOG,
					64
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.JUNGLE_LOG,
					64
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.MANGROVE_LOG,
					64
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.WARPED_STEM,
					64
				), chance
			)
		)
		prizes.add(
			RandomItem(
				ItemStack(
					Material.CRIMSON_STEM,
					64
				), chance
			)
		)

		chance = 5.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.END_STONE,
					16
				), chance
			)
		)
		chance = 5.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DIAMOND,
					1
				), chance
			)
		)
		chance = 6.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GOLD_INGOT,
					16
				), chance
			)
		)
		chance = 7.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.IRON_INGOT,
					32
				), chance
			)
		)
		chance = 7.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.NOTE_BLOCK,
					4
				), chance
			)
		)
		chance = 8.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.REDSTONE,
					64
				), chance
			)
		)
		chance = 12.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.CAKE
				), chance
			)
		)
		chance = 12.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.GLOWSTONE,
					12
				), chance
			)
		)
		chance = 12.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.SEA_LANTERN,
					4
				), chance
			)
		)
		chance = 12.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.PRISMARINE,
					4
				), chance
			)
		)
		chance = 12.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.PRISMARINE,
					4,
					1.toShort()
				), chance
			)
		)
		chance = 12.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.PRISMARINE,
					4,
					2.toShort()
				), chance
			)
		)
		chance = 14.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.PAINTING,
					8
				), chance
			)
		)
		chance = 15.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.COBBLESTONE,
					64
				), chance
			)
		)
		chance = 15.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.DEEPSLATE,
					64
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.COOKED_BEEF,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.COOKED_CHICKEN,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.COOKED_MUTTON,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.COOKED_RABBIT,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.COOKED_COD,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.BREAD,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.MELON_SLICE,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.RABBIT_STEW,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.BEETROOT,
					4
				), chance
			)
		)
		chance = 16.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.APPLE,
					4
				), chance
			)
		)
		chance = 8.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.HONEYCOMB,
					2
				), chance
			)
		)
		chance = 2.0
		prizes.add(
			RandomItem(
				ItemStack(
					Material.TNT,
					2
				), chance
			)
		)

		logger.info("${prizes.size} registered prizes!")
	}

	override fun softDisable() {
		super.softDisable()
	}

	fun generateCaixaSecreta(_level: Int? = null, worldName: String? = null): ItemStack {
		val level = _level ?: DreamUtils.random.nextInt(0, 5)

		var caixa = ItemStack(Material.CHEST)
			.rename("§6✪ §a§lCaixa Secreta §6✪")
			.meta<ItemMeta> {
				setCustomModelData(level + 1)
			}

		val rarityLevel = when (level) {
			4 -> "§8[§a|||||§8]"
			3 -> "§8[§a||||§4|§8]"
			2 -> "§8[§a|||§4||§8]"
			1 -> "§8[§a||§4|||§8]"
			0 -> "§8[§a|§4||||§8]"
			else -> throw RuntimeException("Trying to create secret box with invalid level! Level: $level")
		}

		caixa = caixa.lore("§7Mas... o que será que tem aqui dentro?", "§7", "§3Coloque no chão e descubra!", "§7", "§7Nível de raridade: ${rarityLevel}")
		caixa = caixa.meta<ItemMeta> {
			persistentDataContainer.set(CAIXA_SECRETA_LEVEL_KEY, level)
			if (worldName != null)
				persistentDataContainer.set(CAIXA_SECRETA_WORLD_KEY, worldName)
		}

		return caixa
	}
}