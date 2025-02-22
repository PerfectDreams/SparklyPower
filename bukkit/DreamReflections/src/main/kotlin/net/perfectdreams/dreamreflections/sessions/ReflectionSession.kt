package net.perfectdreams.dreamreflections.sessions

import kotlinx.datetime.Instant
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.appendTextComponent
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamreflections.DreamReflections
import net.perfectdreams.dreamreflections.modules.lbnofallhoplite.LBNoFallHopliteListener
import net.perfectdreams.dreamreflections.sessions.storedmodules.*
import org.bukkit.entity.Player

class ReflectionSession(
    val m: DreamReflections,
    val player: Player,
    val minecraftVersion: String,
    val isBedrockClient: Boolean,
    val createdAt: Instant
) {
    val clientGameState = ClientGameState()
    val serverGameState = ServerGameState()

    val swingsPerSecond = SwingsPerSecond(this)
    val boatFly = BoatFly(this)
    val wurstNoFall = WurstNoFall(this)
    val killAuraRotation = KillAuraRotation(this)
    val killAura = KillAura(this)
    val autoRespawn = AutoRespawn(this)
    val fastPlace = FastPlace(this)
    val wurstCreativeFlight = WurstCreativeFlight(this)
    val lbNoFallHoplite = LBNoFallHoplite(this)
    val lbNoFallForceJump = LBNoFallForceJump(this)
    val noFall = NoFall(this)

    val violationCounterModules = listOf(
        boatFly,
        killAura,
        killAuraRotation,
        fastPlace,
        wurstCreativeFlight,
        noFall,
        wurstNoFall,
        lbNoFallHoplite,
        lbNoFallForceJump,
    )

    fun runOnMainThread(block: (ReflectionSession) -> (Unit)) {
        m.launchMainThread {
            block.invoke(this@ReflectionSession)
        }
    }

    fun createCheckFailMessage(moduleName: String, hits: Int): TextComponent {
        return createCheckFailMessage(moduleName, hits.toString() + "x")
    }

    fun createCheckFailMessage(moduleName: String, reasoning: String): TextComponent {
        return textComponent {
            color(NamedTextColor.GRAY)

            appendTextComponent {
                content(this@ReflectionSession.player.name)
                color(NamedTextColor.AQUA)
            }

            appendTextComponent {
                content(" falhou ")
            }

            appendTextComponent {
                color(DreamReflections.MODULE_NAME_COLOR)
                content(moduleName)
            }

            appendTextComponent {
                append(" (")
                appendTextComponent {
                    color(NamedTextColor.RED)
                    content(reasoning)
                }
                append(")")
            }
        }
    }

    fun createSerializableSessionData() {
        // TODO: This!
    }
}