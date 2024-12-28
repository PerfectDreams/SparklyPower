package net.perfectdreams.dreamemotes.gestures

import net.minecraft.network.protocol.game.*
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamcore.utils.extensions.meta
import net.perfectdreams.dreamcore.utils.extensions.sendPacket
import net.perfectdreams.dreamcore.utils.extensions.showPlayerWithoutRemovingFromPlayerList
import net.perfectdreams.dreamemotes.DreamEmotes
import net.perfectdreams.dreamemotes.OrbitalCamera
import net.perfectdreams.dreamemotes.blockbench.BlockbenchModel
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.profile.PlayerTextures
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import kotlin.math.abs

// Playbacks a gesture
class PlayerGesturePlayback(
    val m: DreamEmotes,
    val player: Player,
    val blockbenchModel: BlockbenchModel,
    val sparklyGesture: SparklyGestures.SparklyGesture,
    val gestureSkinHeads: GestureSkinHeads,
    val skinModel: PlayerTextures.SkinModel,
    val location: Location,
    val teleportAtEndLocation: Location,
    val targetYaw: Float,

    val orbitalCamera: OrbitalCamera,
    val cameraEntity: Entity,
    val entityToBeMountedNetworkId: Int,
) {
    companion object {
        const val TARGET_PLAYBACK_SPEED_TICKS = 1L
        const val INTERPOLATION_DURATION_TICKS = (TARGET_PLAYBACK_SPEED_TICKS + 1).toInt()
    }

    var ticksLived = 0
    var relativeTicksCurrentGestureLived = 0
    val elementIdToEntities = mutableMapOf<UUID, Display>()
    val elementIdToMatrix4f = mutableMapOf<UUID, Matrix4f>()
    var currentActionIdx = 0

    fun tick() {
        val currentAction = sparklyGesture.actions[currentActionIdx]

        val animation = when (currentAction) {
            is SparklyGestures.GestureAction.Play -> currentAction.animation
            is SparklyGestures.GestureAction.PlayAndHold -> currentAction.animation
            is SparklyGestures.GestureAction.PlayAndLoop -> currentAction.animation
        }

        // This is the ELAPSED TIME of the animation
        val blockbenchTime = (relativeTicksCurrentGestureLived / 20.0)

        // TODO: The shadow probably needs to follow the player...
        /* val shadow = player.world.spawn(
            location,
            TextDisplay::class.java
        ) {
            it.isPersistent = false
            it.isShadowed = true
            it.shadowRadius = 0.5f
        } */

        val TARGET_SCALE = 0.06f

        val animationDuration = animation.length
        val animationDurationInTicks = (animationDuration * 20).toInt()

        fun processOutliner(
            outline: BlockbenchModel.Outliner,
            parentMatrix4f: Matrix4f,
            parentOffsetX: Double,
            parentOffsetY: Double,
            parentOffsetZ: Double,
        ) {
            // If rotations are wrong, check if the "pivot" in the animation is in the right place!
            // (Yes, there is the outliner pivot AND a animation-specific pivot)
            /* if (outline.name == "head_with_nameplate" || outline.name == "head") {
                Bukkit.broadcastMessage("${outline.name}: parentOffsetY is ${parentOffsetY}")
                Bukkit.broadcastMessage("${outline.name}: Stuff: $parentRotY")
            } */
            // Bukkit.broadcastMessage("Processing outline ${outline.name} - ${outline.children}")
            val matrix4f = Matrix4f(parentMatrix4f) // Matrix4f(parentMatrix4f)

            var outlineOriginX = outline.origin[0]
            var outlineOriginY = outline.origin[1]
            var outlineOriginZ = outline.origin[2]

            var offsetX = 0.0 + parentOffsetX
            var offsetY = 0.0 + parentOffsetY
            var offsetZ = 0.0 + parentOffsetZ

            var outlineRotationX = outline.rotation[0]
            var outlineRotationY = outline.rotation[1]
            var outlineRotationZ = outline.rotation[2]

            val animator = animation.animators[outline.uuid.toString()]

            fun easeLinear(start: Double, end: Double, percent: Double): Double {
                return start + (end - start) * percent
            }

            // TODO: The keyframe values are based from the DEFAULT POSE, FIX THIS
            // TODO 2: ... I think this is already fixed, check later

            // Keyframes in Blockbench are weird, some axis use "-", some use "+"
            // X and Y: -
            // Z: +

            if (animator != null) {
                run {
                    // Sort keyframes by their time (I'm not sure WHAT is the order that Blockbench uses, sometimes it is end to start, sometimes it is start to end)
                    // So we'll sort it ourselves
                    val sortedKeyframes = animator.keyframes.filterIsInstance<BlockbenchModel.Keyframe.Rotation>()
                        .sortedBy { it.time }

                    if (sortedKeyframes.isEmpty())
                        return@run

                    val availableKeyframes = sortedKeyframes.filter { blockbenchTime >= it.time }
                    val futureKeyframes = sortedKeyframes.filter { it.time > blockbenchTime }

                    // Find the current and next keyframes
                    val currentKeyframe = availableKeyframes.lastOrNull()
                    val nextKeyframe = futureKeyframes.firstOrNull()

                    // player.sendMessage("[BB Time: $blockbenchTime] Current Keyframe: $currentKeyframe")
                    // player.sendMessage("[BB Time: $blockbenchTime] Next Keyframe: $nextKeyframe")

                    if (currentKeyframe != null && nextKeyframe != null) {
                        val rotCur = currentKeyframe.dataPoints.first()
                        val rotNext = nextKeyframe.dataPoints.first()

                        val relStart = blockbenchTime - currentKeyframe.time

                        val progress = relStart / (nextKeyframe.time - currentKeyframe.time)
                        // player.sendMessage("[BB Time: $blockbenchTime] Animator is not null! Progress is ${progress}")

                        outlineRotationX = easeLinear(
                            outlineRotationX - (rotCur.x),
                            outlineRotationX - (rotNext.x),
                            progress
                        )
                        outlineRotationY = easeLinear(
                            outlineRotationY - (rotCur.y),
                            outlineRotationY - (rotNext.y),
                            progress
                        )
                        outlineRotationZ = easeLinear(
                            outlineRotationZ + (rotCur.z),
                            outlineRotationZ + (rotNext.z),
                            progress
                        )
                        // player.sendMessage("[BB Time: $blockbenchTime] outlineRotationX (eased): $outlineRotationX")
                        // player.sendMessage("[BB Time: $blockbenchTime] outlineRotationY (eased): $outlineRotationY")
                        // player.sendMessage("[BB Time: $blockbenchTime] outlineRotationZ (eased): $outlineRotationZ")
                    } else if (currentKeyframe != null) {
                        val rotCur = currentKeyframe.dataPoints.first()

                        // hold last keyframe
                        // FOR SOME REASON BLOCKBENCH USES INVERTED VALUES FOR KEYFRAMES
                        outlineRotationX -= (rotCur.x)
                        outlineRotationY -= (rotCur.y)
                        outlineRotationZ += (rotCur.z)
                    }
                }

                // Remember when I said that keyframes are weird? Well, positions also use yet another different set
                // X: -
                // Y and Z: +
                run {
                    // Sort keyframes by their time (I'm not sure WHAT is the order that Blockbench uses, sometimes it is end to start, sometimes it is start to end)
                    // So we'll sort it ourselves
                    val sortedKeyframes = animator.keyframes.filterIsInstance<BlockbenchModel.Keyframe.Position>()
                        .sortedBy { it.time }

                    if (sortedKeyframes.isEmpty())
                        return@run

                    val availableKeyframes = sortedKeyframes.filter { blockbenchTime >= it.time }
                    val futureKeyframes = sortedKeyframes.filter { it.time > blockbenchTime }

                    // Find the current and next keyframes
                    val currentKeyframe = availableKeyframes.lastOrNull()
                    val nextKeyframe = futureKeyframes.firstOrNull()

                    if (currentKeyframe != null && nextKeyframe != null) {
                        val rotCur = currentKeyframe.dataPoints.first()
                        val rotNext = nextKeyframe.dataPoints.first()

                        val relStart = blockbenchTime - currentKeyframe.time

                        // player.sendMessage("Current Keyframe: $currentKeyframe")
                        // player.sendMessage("Next Keyframe: $nextKeyframe")
                        // player.sendMessage("Animator is not null! Progress is ${(currentKeyframe.time + relStart) / nextKeyframe.time}")
                        offsetX -= easeLinear(
                            rotCur.x,
                            rotNext.x,
                            (currentKeyframe.time + relStart) / nextKeyframe.time
                        )
                        offsetY += easeLinear(
                            rotCur.y,
                            rotNext.y,
                            (currentKeyframe.time + relStart) / nextKeyframe.time
                        )
                        offsetZ += easeLinear(
                            rotCur.z,
                            rotNext.z,
                            (currentKeyframe.time + relStart) / nextKeyframe.time
                        )
                        // player.sendMessage("outlineOriginX (eased): $outlineOriginX")
                        // player.sendMessage("outlineOriginY (eased): $outlineOriginY")
                        // player.sendMessage("outlineOriginZ (eased): $outlineOriginZ")
                    } else if (currentKeyframe != null) {
                        val rotCur = currentKeyframe.dataPoints.first()

                        // hold last keyframe
                        offsetX -= rotCur.x
                        offsetY += rotCur.y
                        offsetZ += rotCur.z
                    }
                }
            }

            // Bukkit.broadcastMessage("${outline.name} offsetY: $offsetY")
            outlineOriginX += offsetX
            outlineOriginY += offsetY
            outlineOriginZ += offsetZ

            // Bukkit.broadcastMessage("${outline.name} Origin X: $outlineOriginX")
            // Bukkit.broadcastMessage("${outline.name} Origin Y: $outlineOriginY")
            // Bukkit.broadcastMessage("${outline.name} Origin Z: $outlineOriginZ")

            // Bukkit.broadcastMessage("${outline.name} Outline Rot X: $outlineRotationX")
            // Bukkit.broadcastMessage("${outline.name} Outline Rot Y: $outlineRotationY")
            // Bukkit.broadcastMessage("${outline.name} Outline Rot Z: $outlineRotationZ")

            // Bukkit.broadcastMessage("Rotating ${outline.name} by $outlineRotationX, $outlineRotationY, $outlineRotationZ, pivot is at ${outlineOriginX}, ${outlineOriginY}, ${outlineOriginZ}")
            val outlineRotationXRad = Math.toRadians(outlineRotationX)
            val outlineRotationYRad = Math.toRadians(outlineRotationY)
            val outlineRotationZRad = Math.toRadians(outlineRotationZ)

            // And translate it back to the world origin
            matrix4f.translate(outlineOriginX.toFloat(), outlineOriginY.toFloat(), outlineOriginZ.toFloat())

            // YES THIS IS THE ROTATION ORDER (Z -> Y -> X) BLOCKBENCH USES (I DON'T KNOW HOW)
            // CHATGPT TOLD ME IT IS LIKE THIS SO WE TAKE THOSE W I GUESS
            // https://chatgpt.com/c/67664f62-f33c-8007-afb1-a88159d98143
            // I DON'T KNOW WHY THE ROTATION ORDER MATTERS
            //
            // The reason it matters (probably?) is that the rotation order impacts the rotation, so you need to match what rotation your 3D editor uses
            matrix4f.rotateZ(outlineRotationZRad.toFloat()) // Rotate around Z-axis
            matrix4f.rotateY(outlineRotationYRad.toFloat()) // Rotate around Y-axis
            matrix4f.rotateX(outlineRotationXRad.toFloat()) // Rotate around X-axis

            matrix4f.translate(-outlineOriginX.toFloat(), -outlineOriginY.toFloat(), -outlineOriginZ.toFloat())

            val childrenUniqueIds = outline.children.filterIsInstance<BlockbenchModel.ChildrenOutliner.ElementReference>().map { it.uuid }
            val elements = blockbenchModel.elements.filter { it.uuid in childrenUniqueIds }

            for (element in elements) {
                // This is a bit of an hack, but hey, what isn't a hack here? xd
                //
                // Don't render arms that are not for us
                if ((element.name.startsWith("arm_left_") || element.name.startsWith("arm_right_")) && !element.name.endsWith(skinModel.name.lowercase()))
                    continue

                if (!element.visibility)
                    continue

                val topX = element.from[0]
                val topY = element.from[1]
                val topZ = element.from[2]
                val bottomX = element.to[0]
                val bottomY = element.to[1]
                val bottomZ = element.to[2]

                val centerX = ((topX + bottomX) / 2)
                val centerY = ((topY + bottomY) / 2)
                val centerZ = ((topZ + bottomZ) / 2)

                var scaleX = abs(topX - element.to[0])
                var scaleY = abs(topY - element.to[1])
                var scaleZ = abs(topZ - element.to[2])

                val originX = element.origin[0]
                val originY = element.origin[1]
                val originZ = element.origin[2]

                var localOffsetX = 0.0
                var localOffsetY = 0.0
                var localOffsetZ = 0.0
                var localRotationX = 0.0
                var localRotationY = 0.0
                var localRotationZ = 0.0

                if (element.name == "hat") {
                    // The hat is a bit wonky and hacky and VERY hacky HACKY HACKY HACKY!!!
                    scaleX *= 2.3f
                    scaleY *= 2.3f
                    scaleZ *= 2.3f

                    localOffsetY -= 5.6
                }

                // Bukkit.broadcastMessage("${element.name} offsets: $offsetX; $offsetY; $offsetZ")
                // Bukkit.broadcastMessage("${element.name} centers: $centerX; $centerY ($bottomY); $centerZ")

                // We don't need to manipulate the coordinates, display entities' translations are not capped! So we only need to translate on the transformation itself
                val sourceLocation = location.clone()
                val existingEntity = elementIdToEntities[element.uuid]

                val itemScaleX = (scaleX.toFloat() * 2f)
                val itemScaleY = (scaleY.toFloat() * 2f)
                val itemScaleZ = (scaleZ.toFloat() * 2f)

                val displayTransformationMatrix = Matrix4f(matrix4f)
                    .translate(
                        (centerX + offsetX).toFloat(),
                        (bottomY + localOffsetY + offsetY).toFloat(),
                        (centerZ + offsetZ).toFloat()
                    )
                    .apply {
                        if (element.name == "hat") {
                            rotateY(Math.toRadians(180.0).toFloat())
                        }
                    }
                    .scale(
                        itemScaleX,
                        itemScaleY,
                        itemScaleZ,
                    )

                val currentTransform = elementIdToMatrix4f[element.uuid]
                elementIdToMatrix4f[element.uuid] = displayTransformationMatrix

                if (existingEntity != null) {
                    // existingEntity.teleport(sourceLocation)
                    if (element.name == "nameplate") {
                        // We DO NOT want rotation, because that causes the nameplate to rotate based on its origin
                        val transformedPos = Vector3f((centerX + offsetX).toFloat(), (centerY + offsetY).toFloat(), (centerZ + offsetZ).toFloat())
                        matrix4f.transformPosition(transformedPos)

                        val nameplateLocation = location.clone()
                            .add(
                                transformedPos.x.toDouble(),
                                transformedPos.y.toDouble(),
                                transformedPos.z.toDouble()
                            )

                        existingEntity.teleport(nameplateLocation)
                    } else {
                        if (currentTransform == displayTransformationMatrix) {
                            // Fixes jittery elements that had a dynamic transformation set but doesn't have them anymore
                            existingEntity.interpolationDelay = 0
                            existingEntity.interpolationDuration = 0
                        } else {
                            existingEntity.interpolationDelay = -1
                            existingEntity.interpolationDuration = INTERPOLATION_DURATION_TICKS
                            existingEntity.setTransformationMatrix(displayTransformationMatrix)
                        }
                    }
                } else {
                    val entity = if (element.name == "nameplate") {
                        // For nameplates, we do a special case due to the way text displays work
                        // We DO NOT want rotation, because that causes the nameplate to rotate based on its origin
                        // val elementMatrix4f = Matrix4f(matrix4f)
                        val transformedPos = Vector3f((centerX + offsetX).toFloat(), (centerY + offsetY).toFloat(), (centerZ + offsetZ).toFloat())
                        matrix4f.transformPosition(transformedPos)

                        // Bukkit.broadcastMessage("${element.name}: coordinates: ${transformedPos.x}; ${transformedPos.y}; ${transformedPos.z}")

                        // We don't need to manipulate the coordinates, display entities' translations are not capped! So we only need to translate on the transformation itself
                        val nameplateLocation = location.clone()
                            .add(
                                transformedPos.x.toDouble(),
                                transformedPos.y.toDouble(),
                                transformedPos.z.toDouble()
                            )

                        // Special handling for the nameplate
                        var nameplateName = player.name()
                        // Try getting the prefix and suffix of the player in the PhoenixScoreboard, to make it look even fancier

                        val scoreboard = DreamCore.INSTANCE.scoreboardManager.getScoreboard(player)

                        if (scoreboard != null) {
                            val team = scoreboard.scoreboard.getEntityTeam(player)

                            if (team != null) {
                                nameplateName = textComponent {
                                    if (team.hasColor())
                                        color(team.color())
                                    append(team.prefix())
                                    append(player.name)
                                    append(team.suffix())
                                }
                            }
                        }

                        location.world.spawn(
                            // We INTENTIONALLY use topY instead of centerY, because Minecraft scales based on the item's TOP LOCATION, not the CENTER
                            nameplateLocation, // location.clone().add(centerX, bottomY, centerZ),
                            TextDisplay::class.java
                        ) {
                            it.text(nameplateName)
                            it.billboard = Display.Billboard.CENTER

                            it.teleportDuration = 1
                            it.isPersistent = false
                        }
                    } else {
                        location.world.spawn(
                            // We INTENTIONALLY use topY instead of centerY, because Minecraft scales based on the item's TOP LOCATION, not the CENTER
                            sourceLocation, // location.clone().add(centerX, bottomY, centerZ),
                            ItemDisplay::class.java
                        ) {
                            // A normal player head item has 0.5 scale in Blockbench
                            // A cube is 2x2x2 in Blockbench
                            it.interpolationDelay = -1
                            // We do 2 interpolation duration because 1 feels like it doesn't interpolate anything at all
                            // We should always keep this (delay between frames) + 1

                            it.interpolationDuration = INTERPOLATION_DURATION_TICKS
                            it.teleportDuration = 1
                            it.isPersistent = false

                            // if (true || outline.name == "arm_right") {
                            // If we use outlineRotationX.toFloat, it does work, but why that works while toRadians is borked??
                            // player.sendMessage("outlineRotationXRad: $outlineRotationXRad")
                            // player.sendMessage("outlineRotationYRad: $outlineRotationYRad")
                            // player.sendMessage("outlineRotationZRad: $outlineRotationZRad")

                            it.setTransformationMatrix(displayTransformationMatrix)

                            val itemStack = if (element.name != "hat") {
                                ItemStack.of(Material.PLAYER_HEAD)
                                    .meta<SkullMeta> {
                                        when (element.name) {
                                            "torso_top" -> {
                                                this.playerProfile = gestureSkinHeads.torsoTop
                                            }

                                            "torso_bottom" -> {
                                                this.playerProfile = gestureSkinHeads.torsoBottom
                                            }

                                            "leg_left_top" -> {
                                                this.playerProfile = gestureSkinHeads.legLeftTop
                                            }

                                            "leg_left_bottom" -> {
                                                this.playerProfile = gestureSkinHeads.legLeftBottom
                                            }

                                            "leg_right_top" -> {
                                                this.playerProfile = gestureSkinHeads.legRightTop
                                            }

                                            "leg_right_bottom" -> {
                                                this.playerProfile = gestureSkinHeads.legRightBottom
                                            }

                                            "arm_left_top_classic", "arm_left_top_slim" -> {
                                                this.playerProfile = gestureSkinHeads.armLeftTop
                                            }

                                            "arm_left_bottom_classic", "arm_left_bottom_slim" -> {
                                                this.playerProfile = gestureSkinHeads.armLeftBottom
                                            }

                                            "arm_right_top_classic", "arm_right_top_slim" -> {
                                                this.playerProfile = gestureSkinHeads.armRightTop
                                            }

                                            "arm_right_bottom_classic", "arm_right_bottom_slim" -> {
                                                this.playerProfile = gestureSkinHeads.armRightBottom
                                            }

                                            else -> {
                                                this.playerProfile = player.playerProfile
                                            }
                                        }
                                    }
                            } else {
                                // TODO: Do NOT do it like this
                                player.inventory.helmet
                            }

                            it.setItemStack(itemStack)

                            if (element.name == "hat") {
                                it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.HEAD
                            }
                        }
                    }

                    elementIdToEntities[element.uuid] = entity
                }
            }

            for (childrenOutliner in outline.children.filterIsInstance<BlockbenchModel.ChildrenOutliner.NestedOutliner>().filter { it.outliner.visibility }) {
                processOutliner(
                    childrenOutliner.outliner,
                    matrix4f,
                    offsetX,
                    offsetY,
                    offsetZ
                )
            }
        }

        for (outline in blockbenchModel.outliner.filter { it.visibility }) {
            // The scale sets the "target scale" of the scene
            processOutliner(outline, Matrix4f().scale(TARGET_SCALE).rotateY(Math.toRadians(targetYaw.toDouble()).toFloat()), 0.0, 0.0, 0.0)
        }

        val additionalKeyframes = currentAction.sidecarKeyframes[relativeTicksCurrentGestureLived]

        if (additionalKeyframes != null) {
            for (keyframe in additionalKeyframes) {
                location.world.playSound(location, keyframe.soundKey, keyframe.volume, keyframe.pitch)
            }
        }

        currentAction.onKeyframe.invoke(ticksLived, player)

        if (animationDurationInTicks == relativeTicksCurrentGestureLived) {
            when (currentAction) {
                is SparklyGestures.GestureAction.Play -> {
                    if (currentActionIdx + 1 == sparklyGesture.actions.size) {
                        // It's joever
                        m.gesturesManager.stopGesturePlayback(player)
                    } else {
                        // Reset and move to the next action!
                        relativeTicksCurrentGestureLived = 0
                        currentActionIdx++
                    }
                }
                is SparklyGestures.GestureAction.PlayAndHold -> {
                    relativeTicksCurrentGestureLived = animationDurationInTicks
                }
                is SparklyGestures.GestureAction.PlayAndLoop -> {
                    relativeTicksCurrentGestureLived = 0
                }
            }
        } else {
            relativeTicksCurrentGestureLived++
        }

        // player.sendMessage("Finished!")
    }

    fun stop() {
        elementIdToEntities.forEach { t, u ->
            u.remove()
        }

        val spectate2 = ClientboundSetCameraPacket((player as CraftPlayer).handle)
        // TODO: Add this to the helpful NMS packet changes
        val f2 = ClientboundSetCameraPacket::class.java.getDeclaredField("cameraId")
        f2.isAccessible = true
        f2.set(spectate2, player.entityId)

        // That "jumpy" animation is caused by the "entityToBeMounted" removal up ahead
        // There isn't a proper solution, because if we skip all that, then we have another issue of the client "easing" from the text entity to the player's eyes

        // We do this via packets because we have regions that the player can't ride vehicles and because only the player needs to know if they are sitting
        // (because we intercept via packets) we don't really need to make this using the API
        val removeEntityToBeMountedPacket = ClientboundRemoveEntitiesPacket(entityToBeMountedNetworkId)

        player.sendPacket(
            ClientboundBundlePacket(
                listOf(
                    spectate2,
                    ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, player.gameMode.value.toFloat()),
                    removeEntityToBeMountedPacket
                )
            )
        )

        player.teleport(teleportAtEndLocation)

        // player.velocity = Vector(0, 0, 0)
        // player.momentum = Vector(0, 0, 0)

        // Bukkit.broadcastMessage("Player end location is ${player.location} with vel ${player.velocity}")

        orbitalCamera.alive = false
        m.orbitalCameras.remove(player, orbitalCamera)

        Bukkit.getOnlinePlayers()
            .forEach {
                it.showPlayerWithoutRemovingFromPlayerList(m, player)
            }

        // shadow.remove()
        cameraEntity.remove()
    }
}