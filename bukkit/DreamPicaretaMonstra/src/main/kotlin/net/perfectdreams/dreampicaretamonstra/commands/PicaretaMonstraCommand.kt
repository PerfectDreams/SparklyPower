package net.perfectdreams.dreampicaretamonstra.commands

import net.perfectdreams.dreamcore.utils.commands.DSLCommandBase
import net.perfectdreams.dreamcore.utils.extensions.storeMetadata
import net.perfectdreams.dreamcore.utils.lore
import net.perfectdreams.dreamcore.utils.rename
import net.perfectdreams.dreampicaretamonstra.DreamPicaretaMonstra
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object PicaretaMonstraCommand : DSLCommandBase<DreamPicaretaMonstra> {
    override fun command(plugin: DreamPicaretaMonstra) = create(
            listOf("picaretamonstra")
    ) {
        permission = "sparklypower.picaretamonstra"

        executes {
            val picaretaMonstra = ItemStack(Material.NETHERITE_PICKAXE).storeMetadata("isMonsterPickaxe", "true")
                    .rename("§6§lPicareta Monstra")
                    .lore("§6Tá saindo da jaula o monstro!")

            player.inventory.addItem(picaretaMonstra)
        }
    }
}
