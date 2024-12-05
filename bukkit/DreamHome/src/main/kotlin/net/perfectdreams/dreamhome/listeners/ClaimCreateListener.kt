package net.perfectdreams.dreamhome.listeners

import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.adventure.appendCommand
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamhome.DreamHome
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ClaimCreateListener(val m: DreamHome) : Listener {
    @EventHandler
    fun onClaimCreate(e: ClaimCreatedEvent) {
        // Tem novatos que esquecem de marcar a casa ao proteger o terreno
        // Para a gente evitar isto, iremos verificar se o player tem alguma home e, se não
        // iremos criar uma casa para ele e avisar.
        val creator = e.creator
        if (creator !is Player)
            return

        m.loadHouses(creator) {
            if (it.isEmpty()) {
                m.createHouse(
                    creator,
                    null,
                    "casa",
                    true,
                    null,
                    creator.location
                ) {
                    creator.sendMessage(
                        textComponent {
                            color(NamedTextColor.YELLOW)
                            appendTextComponent {
                                content("Hey, eu percebi que você está marcando o seu primeiro terreno! Para não perdê-lo, eu marquei um teletransporte rápido para ela! ")
                                appendCommand("/home casa")
                            }
                            appendNewline()
                            appendTextComponent {
                                content("Se você quiser ver a lista de teletransportes rápidos marcados, use ")
                                appendCommand("/home")
                            }
                            appendNewline()
                            appendTextComponent {
                                content("Se você quiser remarcar a posição do teletransporte rápido, use ")
                                appendCommand("/sethome casa")
                            }
                            appendNewline()
                            appendTextComponent {
                                content("Se você quiser deletar o teletransporte rápido, use ")
                                appendCommand("/delhome casa")
                            }
                            appendNewline()
                            appendTextComponent {
                                content("Boa sorte em sua nova aventura! ^-^")
                            }
                        }
                    )
                }
            }
        }
    }
}