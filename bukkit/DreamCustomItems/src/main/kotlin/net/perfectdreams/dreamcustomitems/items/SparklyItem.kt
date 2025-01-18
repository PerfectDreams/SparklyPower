package net.perfectdreams.dreamcustomitems.items

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.MapItemColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.displayNameWithoutDecorations
import net.perfectdreams.dreamcore.utils.adventure.lore
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.set
import net.perfectdreams.dreamcustomitems.paintings.SparklyPaintingsRegistry
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.enginehub.piston.inject.Key
import kotlin.reflect.KProperty1

class SparklyItem(val data: SparklyItemData) {
    /**
     * Creates an [ItemStack] that represents this [SparklyItem]
     */
    fun createItemStack(): ItemStack {
        val rawMaterial = getAttributeCheckParents(SparklyItemData::material) ?: error("Missing Material for item ${data.id}! You can't create a ItemStack without a Material!")

        return ItemStack.of(rawMaterial)
            .apply {
                val rawMaxDamage = getAttributeCheckParents(SparklyItemData::maxDamage)

                if (rawMaxDamage != null) {
                    setData(DataComponentTypes.MAX_DAMAGE, rawMaxDamage)
                }

                val rawMaxStackSize = getAttributeCheckParents(SparklyItemData::maxStackSize)

                if (rawMaxStackSize != null) {
                    setData(DataComponentTypes.MAX_STACK_SIZE, rawMaxStackSize)
                }

                val rawMapColor = getAttributeCheckParents(SparklyItemData::mapColor)

                if (rawMapColor != null) {
                    setData(DataComponentTypes.MAP_COLOR, MapItemColor.mapItemColor().color(Color.fromRGB(rawMapColor)))
                }
            }
            .meta<ItemMeta> {
                persistentDataContainer.set(SparklyItemsRegistry.SPARKLYPOWER_CUSTOM_ITEM_ID, data.id)

                setRarity(getAttributeCheckParents(SparklyItemData::rarity))

                val customModelData = getAttributeCheckParents(SparklyItemData::customModelData)
                if (customModelData != null)
                    setCustomModelData(customModelData)

                val customItemModel = getAttributeCheckParents(SparklyItemData::itemModel)
                if (customItemModel != null) {
                    itemModel = NamespacedKey.fromString(customItemModel)
                }

                val rawItemName = getAttributeCheckParents(SparklyItemData::itemName)
                if (rawItemName != null) {
                    itemName(MiniMessage.miniMessage().deserialize(rawItemName))
                }

                val rawDisplayName = getAttributeCheckParents(SparklyItemData::displayName)
                if (rawDisplayName != null) {
                    displayNameWithoutDecorations {
                        append(MiniMessage.miniMessage().deserialize(rawDisplayName))
                    }
                }

                val loreLineSections = mutableListOf<List<Component>>()

                val rawHangablePainting = getAttributeCheckParents(SparklyItemData::hangablePainting)
                if (rawHangablePainting != null) {
                    val paintingVariant = SparklyPaintingsRegistry.getPaintingVariantById(rawHangablePainting.paintingKey) ?:  error("Painting Variant \"${rawHangablePainting.paintingKey}\" does not exist! Bug?")

                    loreLineSections.add(
                        listOf(
                            textComponent {
                                color(NamedTextColor.DARK_GRAY)
                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(rawHangablePainting.artist)
                                }

                                appendTextComponent {
                                    content(" - ")
                                }

                                appendTextComponent {
                                    color(NamedTextColor.AQUA)
                                    content(rawHangablePainting.title)
                                }
                            },
                            textComponent {
                                color(NamedTextColor.GOLD)
                                content("${paintingVariant.width}x${paintingVariant.height}")
                            }
                        )
                    )
                }

                val rawLore = getAttributeCheckParents(SparklyItemData::lore)
                if (rawLore != null) {
                    loreLineSections.add(rawLore.map { MiniMessage.miniMessage().deserialize(it) })
                }

                if (loreLineSections.isNotEmpty()) {
                    lore {
                        for ((index, group) in loreLineSections.withIndex()) {
                            if (index != 0) {
                                this.emptyLine()
                            }

                            for (line in group) {
                                this.textWithoutDecorations {
                                    color(NamedTextColor.GRAY)
                                    append(line)
                                }
                            }
                        }
                    }
                }

                val rawJukeboxPlayable = getAttributeCheckParents(SparklyItemData::jukeboxPlayable)
                if (rawJukeboxPlayable != null) {
                    setJukeboxPlayable(
                        jukeboxPlayable.apply {
                            this.songKey = NamespacedKey.fromString(rawJukeboxPlayable.songKey)!!
                        }
                    )
                }
            }
    }

    /**
     * Gets the value of the [kProperty1], checking the item [SparklyItemData] and all of its parents
     */
    fun <T> getAttributeCheckParents(kProperty1: KProperty1<SparklyItemData, T>): T? {
        val dataToBeChecked = mutableListOf<SparklyItemData>()

        fun add(data: SparklyItemData) {
            dataToBeChecked.add(data)

            if (data.parent != null) {
                add(SparklyItemsRegistry.getItemById(data.parent).data)
            }
        }

        add(data)

        for (data in dataToBeChecked) {
            val value = kProperty1.get(data)
            if (value != null)
                return value
        }

        return null
    }
}