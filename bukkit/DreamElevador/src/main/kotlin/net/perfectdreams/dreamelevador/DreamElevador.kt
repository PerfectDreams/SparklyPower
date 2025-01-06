package net.perfectdreams.dreamelevador

import com.okkero.skedule.schedule
import net.perfectdreams.dreamcore.utils.KotlinPlugin
import net.perfectdreams.dreamcore.utils.registerEvents
import net.perfectdreams.dreamcore.utils.scheduler
import net.perfectdreams.dreamcore.utils.withoutPermission
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Sign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class DreamElevador : KotlinPlugin(), Listener {
    override fun softEnable() {
        super.softEnable()
        registerEvents(this)
    }

    override fun softDisable() {
        super.softDisable()
    }

    @EventHandler
    fun onSign(e: SignChangeEvent) {
        if (e.lines[0] != "[Elevador]")
            return

        if (!e.player.hasPermission("dreamelevador.create")) {
            e.player.sendMessage(withoutPermission)
            e.isCancelled = true
            e.block.type = Material.AIR
            e.block.world.dropItemNaturally(e.block.location, ItemStack(Material.OAK_SIGN))
            return
        }

        e.setLine(0, "§9[Elevador]")
        e.setLine(1, "???")
        e.setLine(2, "Direito: Sobe")
        e.setLine(3, "Esquerdo: Desce")

        e.player.sendMessage("§aElevador criado com sucesso!")

        scheduler().schedule(this) { // É necessário esperar 1 tick antes de atualizar
            waitFor(1)
            updateStoryTrack(e.block.getState(false) as Sign)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onClick(e: PlayerInteractEvent) {
        if (e.player.isSneaking)
            return

        val clickedBlock = e.clickedBlock

        val material = clickedBlock?.type ?: return

        if (!material.name.contains("SIGN"))
            return

        val sign = clickedBlock.getState(false) as Sign

        if (!isElevador(sign))
            return

        val action = e.action
        val player = e.player

        e.isCancelled = true

        if (action == Action.RIGHT_CLICK_BLOCK) handleUp(player, sign)
        else if (action == Action.LEFT_CLICK_BLOCK) handleDown(player, sign)
    }

    fun handleUp(player: Player, sign: Sign) {
        updateStoryTrack(sign)
        var idx = 0
        val world = sign.world
        for (y in world.minHeight until world.maxHeight) {
            val block = world.getBlockAt(sign.x, y, sign.z)
            val material = block.type

            if (!material.name.contains("SIGN"))
                continue

            val _sign = block.getState(false) as Sign

            if (!isElevador(_sign))
                continue

            // Encontramos um elevador!
            if (sign.y >= y) { // Só iremos somar o andar atual caso o elevador esteja abaixo do desejado
                idx++
                continue
            }

            player.teleport(getTeleportLocation(player, _sign))

            val fancyName = idx.convertToNumeroNomeAdjetivo()
            player.sendTitle(
                "",
                "§7${fancyName ?: "Térreo"}${if (fancyName != null) " andar" else ""}",
                8,
                35,
                8
            )
            player.world.spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.location.add(0.0, 0.5, 0.0),
                10,
                0.5,
                0.5,
                0.5
            )
            return
        }
        player.sendTitle("§f", "§cVocê já está no último andar!", 8, 35, 8)
        player.world.spawnParticle(Particle.ANGRY_VILLAGER, player.location.add(0.0, 0.5, 0.0), 10, 0.5, 0.5, 0.5)
    }

    fun handleDown(player: Player, sign: Sign) {
        updateStoryTrack(sign)
        val world = sign.world
        for (y in world.maxHeight downTo world.minHeight) {
            val block = world.getBlockAt(sign.x, y, sign.z)
            val material = block.type

            if (!material.name.contains("SIGN"))
                continue

            val _sign = block.getState(false) as Sign

            if (!isElevador(_sign))
                continue

            // Encontramos um elevador!
            if (sign.y <= y) {
                continue
            }

            var idx = 0
            // Pegar o andar atual quando está descendo é mais difícil, é necessário fazer um loop "ao contrário"
            for (_y in world.minHeight until world.maxHeight) {
                val block = world.getBlockAt(sign.x, _y, sign.z)
                val material = block?.type ?: continue

                if (!material.name.contains("SIGN"))
                    continue

                val __sign = block.getState(false) as Sign

                if (!isElevador(__sign))
                    continue

                if (__sign.y == _sign.y) {
                    break
                }
                idx++
            }

            player.teleport(getTeleportLocation(player, _sign))

            val fancyName = idx.convertToNumeroNomeAdjetivo()
            player.sendTitle(
                "",
                "§7${fancyName ?: "Térreo"}${if (fancyName != null) " andar" else ""}",
                8,
                35,
                8
            )
            player.world.spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.location.add(0.0, 0.5, 0.0),
                10,
                0.5,
                0.5,
                0.5
            )
            return
        }
        player.sendTitle("§f", "§cVocê já está no primeiro andar!", 8, 35, 8)
        player.world.spawnParticle(Particle.ANGRY_VILLAGER, player.location.add(0.0, 0.5, 0.0), 10, 0.5, 0.5, 0.5)
    }

    fun getTeleportLocation(player: Player, sign: Sign): Location {
        val location = sign.location.add(0.5, -1.0, 0.5)
        // location.x = player.location.x
        // location.z = player.location.z
        location.yaw = player.location.yaw
        location.pitch = player.location.pitch
        return location
    }

    fun updateStoryTrack(sign: Sign) {
        // PEGAR TODOS OS ANDARES
        var total = 0
        val world = sign.world

        for (y in world.minHeight until world.maxHeight) {
            val block = world.getBlockAt(sign.x, y, sign.z)
            val material = block?.type ?: continue

            if (!material.name.contains("SIGN"))
                continue

            val _sign = block.getState(false) as Sign

            if (!isElevador(_sign))
                continue

            total++
        }

        // ALTERAR A PLACA
        var idx = 0

        for (y in world.minHeight until world.maxHeight) {
            val block = world.getBlockAt(sign.x, y, sign.z)
            val material = block?.type ?: continue

            if (!material.name.contains("SIGN"))
                continue

            val _sign = block.getState(false) as Sign

            if (!isElevador(_sign))
                continue

            idx++
            _sign.setLine(1, "$idx/$total")
            _sign.update()
        }
    }

    fun isElevador(sign: Sign): Boolean {
        val text = sign.lines[0]

        return text == "§9[Elevador]"
    }

    fun Int.convertToNumeroNomeAdjetivo() = when (this) {
        1 -> "Primeiro"
        2 -> "Segundo"
        3 -> "Terceiro"
        4 -> "Quarto"
        5 -> "Quinto"
        6 -> "Sexto"
        7 -> "S\u00e9timo"
        8 -> "Oitavo"
        9 -> "Nono"
        10 -> "D\u00e9cimo"
        11 -> "D\u00e9cimo primeiro"
        12 -> "D\u00e9cimo segundo"
        13 -> "D\u00e9cimo terceiro"
        14 -> "D\u00e9cimo quarto"
        15 -> "D\u00e9cimo quinto"
        16 -> "D\u00e9cimo sexto"
        17 -> "D\u00e9cimo s\u00e9timo"
        18 -> "D\u00e9cimo oitavo"
        19 -> "D\u00e9cimo nono"
        20 -> "Vig\u00e9simo"
        21 -> "Vig\u00e9simo primeiro"
        22 -> "Vig\u00e9simo segundo"
        23 -> "Vig\u00e9simo terceiro"
        24 -> "Vig\u00e9simo quarto"
        25 -> "Vig\u00e9simo quinto"
        26 -> "Vig\u00e9simo sexto"
        27 -> "Vig\u00e9simo s\u00e9timo"
        28 -> "Vig\u00e9simo oitavo"
        29 -> "Vig\u00e9simo nono"
        30 -> "Trig\u00e9simo"
        31 -> "Trig\u00e9simo primeiro"
        32 -> "Trig\u00e9simo segundo"
        33 -> "Trig\u00e9simo terceiro"
        34 -> "Trig\u00e9simo quarto"
        35 -> "Trig\u00e9simo quinto"
        36 -> "Trig\u00e9simo sexto"
        37 -> "Trig\u00e9simo s\u00e9timo"
        38 -> "Trig\u00e9simo oitavo"
        39 -> "Trig\u00e9simo nono"
        40 -> "Quadrag\u00e9simo"
        41 -> "Quadrag\u00e9simo primeiro"
        42 -> "Quadrag\u00e9simo segundo"
        43 -> "Quadrag\u00e9simo terceiro"
        44 -> "Quadrag\u00e9simo quarto"
        45 -> "Quadrag\u00e9simo quinto"
        46 -> "Quadrag\u00e9simo sexto"
        47 -> "Quadrag\u00e9simo s\u00e9timo"
        48 -> "Quadrag\u00e9simo oitavo"
        49 -> "Quadrag\u00e9simo nono"
        50 -> "Quinquag\u00e9simo"
        51 -> "Quinquag\u00e9simo primeiro"
        52 -> "Quinquag\u00e9simo segundo"
        53 -> "Quinquag\u00e9simo terceiro"
        54 -> "Quinquag\u00e9simo quarto"
        55 -> "Quinquag\u00e9simo quinto"
        56 -> "Quinquag\u00e9simo sexto"
        57 -> "Quinquag\u00e9simo s\u00e9timo"
        58 -> "Quinquag\u00e9simo oitavo"
        59 -> "Quinquag\u00e9simo nono"
        60 -> "Sexag\u00e9simo"
        61 -> "Sexag\u00e9simo primeiro"
        62 -> "Sexag\u00e9simo segundo"
        63 -> "Sexag\u00e9simo terceiro"
        64 -> "Sexag\u00e9simo quarto"
        65 -> "Sexag\u00e9simo quinto"
        66 -> "Sexag\u00e9simo sexto"
        67 -> "Sexag\u00e9simo s\u00e9timo"
        68 -> "Sexag\u00e9simo oitavo"
        69 -> "Sexag\u00e9simo nono"
        70 -> "Septuag\u00e9simo"
        71 -> "Septuag\u00e9simo primeiro"
        72 -> "Septuag\u00e9simo segundo"
        73 -> "Septuag\u00e9simo terceiro"
        74 -> "Septuag\u00e9simo quarto"
        75 -> "Septuag\u00e9simo quinto"
        76 -> "Septuag\u00e9simo sexto"
        77 -> "Septuag\u00e9simo s\u00e9timo"
        78 -> "Septuag\u00e9simo oitavo"
        79 -> "Septuag\u00e9simo nono"
        80 -> "Octog\u00e9simo"
        81 -> "Octog\u00e9simo primeiro"
        82 -> "Octog\u00e9simo segundo"
        83 -> "Octog\u00e9simo terceiro"
        84 -> "Octog\u00e9simo quarto"
        85 -> "Octog\u00e9simo quinto"
        86 -> "Octog\u00e9simo sexto"
        87 -> "Octog\u00e9simo s\u00e9timo"
        88 -> "Octog\u00e9simo oitavo"
        89 -> "Octog\u00e9simo nono"
        90 -> "Nonag\u00e9simo"
        91 -> "Nonag\u00e9simo primeiro"
        92 -> "Nonag\u00e9simo segundo"
        93 -> "Nonag\u00e9simo terceiro"
        94 -> "Nonag\u00e9simo quarto"
        95 -> "Nonag\u00e9simo quinto"
        96 -> "Nonag\u00e9simo sexto"
        97 -> "Nonag\u00e9simo s\u00e9timo"
        98 -> "Nonag\u00e9simo oitavo"
        99 -> "Nonag\u00e9simo nono"
        100 -> "Cent\u00e9simo"
        else -> null
    }
}