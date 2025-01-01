package net.perfectdreams.dreamemotes.gestures

import com.destroystokyo.paper.profile.ProfileProperty
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundGameEventPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket
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
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
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
    val sparklyGesture: SparklyGestureData,
    val gestureSkinHeads: GestureSkinHeads,
    val skinModel: PlayerTextures.SkinModel,
    val location: Location,
    val teleportAtEndLocation: Location,
    val targetYaw: Float,

    val orbitalCamera: OrbitalCamera,
    val cameraEntity: Entity,
    val soundEmitter: Entity,
    val entityToBeMountedNetworkId: Int,
) {
    companion object {
        const val TARGET_PLAYBACK_SPEED_TICKS = 1L
        const val INTERPOLATION_DURATION_TICKS = (TARGET_PLAYBACK_SPEED_TICKS + 1).toInt()
        const val TARGET_SCALE = 0.06f
        private val DEFAULT_ELEMENT_NAMES = setOf(
            "hat",
            "head",

            "torso_top",
            "torso_bottom",

            "leg_left_top",
            "leg_left_bottom",

            "leg_right_top",
            "leg_right_bottom",

            "arm_left_top_classic",
            "arm_left_top_slim",

            "arm_left_bottom_classic",
            "arm_left_bottom_slim",

            "arm_right_top_classic",
            "arm_right_top_slim",

            "arm_right_bottom_classic",
            "arm_right_bottom_slim"
        )
    }

    var ticksLived = 0
    var relativeTicksCurrentGestureLived = 0
    val elementIdToEntities = mutableMapOf<UUID, Display>()
    val elementIdToMatrix4f = mutableMapOf<UUID, Matrix4f>()
    // When starting, the current action is ALWAYS the first action on the list
    var currentAction = sparklyGesture.actions.first()
    var progressTimelineWithoutLooping = false

    fun tick() {
        val animation = blockbenchModel.animations.first { it.name == currentAction.blockbenchAnimation }

        val animationDuration = animation.length
        val animationDurationInTicks = (animationDuration * 20).toInt()

        // This is the ELAPSED TIME of the animation
        val blockbenchTime = if (animationDurationInTicks == 0 || progressTimelineWithoutLooping) {
            (relativeTicksCurrentGestureLived / 20.0)
        } else {
            ((relativeTicksCurrentGestureLived % animationDurationInTicks) / 20.0)
        }

        // Bukkit.broadcastMessage("Blockbench Time: $blockbenchTime - RelTicks: $relativeTicksCurrentGestureLived")

        // TODO: The shadow probably needs to follow the player...
        /* val shadow = player.world.spawn(
            location,
            TextDisplay::class.java
        ) {
            it.isPersistent = false
            it.isShadowed = true
            it.shadowRadius = 0.5f
        } */

        fun processOutliner(
            outline: BlockbenchModel.Outliner,
            parentMatrix4f: Matrix4f,

            parentOffsetX: Double,
            parentOffsetY: Double,
            parentOffsetZ: Double,

            parentRotX: Double,
            parentRotY: Double,
            parentRotZ: Double
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

            var outlineScaleX = 1.0
            var outlineScaleY = 1.0
            var outlineScaleZ = 1.0

            val animator = animation.animators[outline.uuid.toString()]

            fun easeLinear(start: Double, end: Double, percent: Double): Double {
                return start + (end - start) * percent
            }

            // Interpolates a point using Catmull-Rom spline between four points
            fun catmullRomInterpolation(p0: Vector3f, p1: Vector3f, p2: Vector3f, p3: Vector3f, t: Double): Vector3f {
                val t2 = t * t
                val t3 = t2 * t

                val x = 0.5 * (
                        (2 * p1.x) +
                                (-p0.x + p2.x) * t +
                                (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                                (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3
                        )

                val y = 0.5 * (
                        (2 * p1.y) +
                                (-p0.y + p2.y) * t +
                                (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                                (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3
                        )

                val z = 0.5 * (
                        (2 * p1.z) +
                                (-p0.z + p2.z) * t +
                                (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 +
                                (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3
                        )

                return Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
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
                    val previousKeyframe = availableKeyframes.getOrNull(futureKeyframes.size - 2)
                    val currentKeyframe = availableKeyframes.lastOrNull()
                    val nextKeyframe = futureKeyframes.firstOrNull()
                    val nextNextKeyframe = futureKeyframes.getOrNull(1)


                    // player.sendMessage("[BB Time: $blockbenchTime] Current Keyframe: $currentKeyframe")
                    // player.sendMessage("[BB Time: $blockbenchTime] Next Keyframe: $nextKeyframe")

                    if (currentKeyframe != null && nextKeyframe != null) {
                        val rotCur = currentKeyframe.dataPoints.first()
                        val rotNext = nextKeyframe.dataPoints.first()

                        val relCurrentTime = blockbenchTime - currentKeyframe.time
                        val relEnd = nextKeyframe.time - currentKeyframe.time
                        val progress = relCurrentTime / relEnd

                        // player.sendMessage("[BB Time: $blockbenchTime] Animator is not null! Progress is ${progress}")

                        if (currentKeyframe.interpolation == "catmullrom") {
                            val previousKeyframeDataPoint = previousKeyframe?.dataPoints?.first() ?: rotCur
                            val nextNextKeyframeDataPoint = nextNextKeyframe?.dataPoints?.first() ?: rotNext

                            // The prevVec3f and endEndVec3f are the catmullrom's "control points", they control how the curvature works
                            // In Blockbench, the control points are the previous keyframe and the next keyframe
                            val prevVec3f = Vector3f(previousKeyframeDataPoint.x.toFloat(), previousKeyframeDataPoint.y.toFloat(), previousKeyframeDataPoint.z.toFloat())
                            val startVec3f = Vector3f(rotCur.x.toFloat(), rotCur.y.toFloat(), rotCur.z.toFloat())
                            val endVec3f = Vector3f(rotNext.x.toFloat(), rotNext.y.toFloat(), rotNext.z.toFloat())
                            val endEndVec3f = Vector3f(nextNextKeyframeDataPoint.x.toFloat(), nextNextKeyframeDataPoint.y.toFloat(), nextNextKeyframeDataPoint.z.toFloat())

                            // p0 and p3 are the control points
                            val interpolationResult = catmullRomInterpolation(
                                prevVec3f,
                                startVec3f,
                                endVec3f,
                                endEndVec3f,
                                progress
                            )

                            outlineRotationX -= interpolationResult.x.toDouble()
                            outlineRotationY -= interpolationResult.y.toDouble()
                            outlineRotationZ += interpolationResult.z.toDouble()
                        } else {
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
                        }

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
                    val previousKeyframe = availableKeyframes.getOrNull(futureKeyframes.size - 2)
                    val currentKeyframe = availableKeyframes.lastOrNull()
                    val nextKeyframe = futureKeyframes.firstOrNull()
                    val nextNextKeyframe = futureKeyframes.getOrNull(1)

                    if (currentKeyframe != null && nextKeyframe != null) {
                        val rotCur = currentKeyframe.dataPoints.first()
                        val rotNext = nextKeyframe.dataPoints.first()

                        val relCurrentTime = blockbenchTime - currentKeyframe.time
                        val relEnd = nextKeyframe.time - currentKeyframe.time
                        val progress = relCurrentTime / relEnd

                        // Bukkit.broadcastMessage("Current Progress: $progress")
                        // player.sendMessage("Current Keyframe: $currentKeyframe")
                        // player.sendMessage("Next Keyframe: $nextKeyframe")
                        // player.sendMessage("Animator is not null! Progress is ${(currentKeyframe.time + relStart) / nextKeyframe.time}")

                        if (currentKeyframe.interpolation == "catmullrom") {
                            val previousKeyframeDataPoint = previousKeyframe?.dataPoints?.first() ?: rotCur
                            val nextNextKeyframeDataPoint = nextNextKeyframe?.dataPoints?.first() ?: rotNext

                            // The prevVec3f and endEndVec3f are the catmullrom's "control points", they control how the curvature works
                            // In Blockbench, the control points are the previous keyframe and the next keyframe
                            val prevVec3f = Vector3f(previousKeyframeDataPoint.x.toFloat(), previousKeyframeDataPoint.y.toFloat(), previousKeyframeDataPoint.z.toFloat())
                            val startVec3f = Vector3f(rotCur.x.toFloat(), rotCur.y.toFloat(), rotCur.z.toFloat())
                            val endVec3f = Vector3f(rotNext.x.toFloat(), rotNext.y.toFloat(), rotNext.z.toFloat())
                            val endEndVec3f = Vector3f(nextNextKeyframeDataPoint.x.toFloat(), nextNextKeyframeDataPoint.y.toFloat(), nextNextKeyframeDataPoint.z.toFloat())

                            // p0 and p3 are the control points
                            val interpolationResult = catmullRomInterpolation(
                                prevVec3f,
                                startVec3f,
                                endVec3f,
                                endEndVec3f,
                                progress
                            )

                            offsetX -= interpolationResult.x
                            offsetY += interpolationResult.y
                            offsetZ += interpolationResult.z
                        } else {
                            offsetX -= easeLinear(
                                rotCur.x,
                                rotNext.x,
                                progress
                            )
                            offsetY += easeLinear(
                                rotCur.y,
                                rotNext.y,
                                progress
                            )
                            offsetZ += easeLinear(
                                rotCur.z,
                                rotNext.z,
                                progress
                            )
                        }
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

                run {
                    // Sort keyframes by their time (I'm not sure WHAT is the order that Blockbench uses, sometimes it is end to start, sometimes it is start to end)
                    // So we'll sort it ourselves
                    val sortedKeyframes = animator.keyframes.filterIsInstance<BlockbenchModel.Keyframe.Scale>()
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

                        val relCurrentTime = blockbenchTime - currentKeyframe.time
                        val relEnd = nextKeyframe.time - currentKeyframe.time
                        val progress = relCurrentTime / relEnd

                        // player.sendMessage("Current Keyframe: $currentKeyframe")
                        // player.sendMessage("Next Keyframe: $nextKeyframe")
                        // player.sendMessage("Animator is not null! Progress is ${(currentKeyframe.time + relStart) / nextKeyframe.time}")
                        outlineScaleX *= easeLinear(
                            rotCur.x,
                            rotNext.x,
                            progress
                        )
                        outlineScaleY *= easeLinear(
                            rotCur.y,
                            rotNext.y,
                            progress
                        )
                        outlineScaleZ *= easeLinear(
                            rotCur.z,
                            rotNext.z,
                            progress
                        )
                        // player.sendMessage("outlineOriginX (eased): $outlineOriginX")
                        // player.sendMessage("outlineOriginY (eased): $outlineOriginY")
                        // player.sendMessage("outlineOriginZ (eased): $outlineOriginZ")
                    } else if (currentKeyframe != null) {
                        val rotCur = currentKeyframe.dataPoints.first()

                        // hold last keyframe
                        outlineScaleX *= rotCur.x
                        outlineScaleY *= rotCur.y
                        outlineScaleZ *= rotCur.z
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

            // This is a bit hard to do, and to make it work...
            matrix4f.translate(outlineOriginX.toFloat(), outlineOriginY.toFloat(), outlineOriginZ.toFloat())
            matrix4f.scale(outlineScaleX.toFloat(), outlineScaleY.toFloat(), outlineScaleZ.toFloat())
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

                // This is the custom gesture prop mapper
                val propMapper = sparklyGesture.props[element.name]

                if (propMapper == null) {
                    // It isn't a custom prop! (player mesh and stuff)
                    // Everything NOT related to custom props is here, to avoid any "whoopsie" breakages breaking any of the default props
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

                    // TODO: Fix scaling non-player head display items
                    //  Currently, if the original item model is 8x8x8 on Blockbench, it does scale around the center without any pivot meddling
                    //  But anything else does NOT scale correctly
                    //  A diamond block model, to scale correctly around the center without any pivot meddling, it should be 8f x 8f x 8f in Blockbench, and
                    //  xyz 0.5 scale in the config
                    val displayTransformationMatrix = Matrix4f(matrix4f)
                        .translate(
                            (centerX + offsetX).toFloat(),
                            (bottomY + localOffsetY + offsetY).toFloat(),
                            (centerZ + offsetZ).toFloat()
                        )
                        .scale(
                            itemScaleX,
                            itemScaleY,
                            itemScaleZ
                        )
                        .apply {
                            if (element.name == "hat") {
                                rotateY(Math.toRadians(180.0).toFloat())
                            }
                        }

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
                            // The != check is to fix jittery elements that had a dynamic transformation set but doesn't have them anymore
                            if (currentTransform != displayTransformationMatrix) {
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

                                if (element.name in DEFAULT_ELEMENT_NAMES) {
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

                                it.setTransformationMatrix(displayTransformationMatrix)
                            }
                        }

                        elementIdToEntities[element.uuid] = entity
                    }
                } else {
                    // Custom prop handling goes here!
                    when (propMapper) {
                        is PropMapper.ItemDisplay -> {
                            // ITEM DISPLAYS ARE SCALED FROM THE **CENTER OF THE ITEM**
                            // ACTUALLY I LIED, IT DEPENDS ON THE ITEM YOU ARE SCALING, BY DEFAULT IT IS AT THE CENTER BTW
                            // BUT THINGS LIKE PLAYER HEADS ARE NOT SCALED FROM THE CENTER (probably because the item itself has "padding")
                            //
                            // THE TRANSLATION ITSELF DOES NOT SEEM TO CHANGE THE SCALE POSITION OR WHATEVER THE FUCK WE ARE DOING
                            // (YOU CAN TEST THAT BY SCALING TWO ITEMS, ONE PLAYER HEAD AND ANOTHER DIAMOND BLOCK)
                            //
                            // THIS ALSO MEANS THAT TWO ELEMENTS IN BB THAT ARE MAPPED TO DIFFERENT ITEMS, WILL HAVE DIFFERENT APPEARANCES
                            //
                            // This is why there is a "offsetYType" for props, sometimes you may want to use "bottom" (player heads), other times you may want to use "center" (blocks)
                            val customPropOffsetYType = when (propMapper.offsetYType) {
                                PropMapper.ItemDisplay.OffsetType.BOTTOM -> bottomY
                                PropMapper.ItemDisplay.OffsetType.CENTER -> centerY
                                PropMapper.ItemDisplay.OffsetType.TOP -> topY
                            }

                            val displayTransformationMatrix = Matrix4f(matrix4f)
                                .translate(
                                    (centerX + offsetX).toFloat(),
                                    (customPropOffsetYType + localOffsetY + offsetY).toFloat(),
                                    (centerZ + offsetZ).toFloat()
                                )
                                .scale(
                                    propMapper.scaleX,
                                    propMapper.scaleY,
                                    propMapper.scaleZ,
                                )

                            val existingEntity = elementIdToEntities[element.uuid]

                            val currentTransform = elementIdToMatrix4f[element.uuid]
                            elementIdToMatrix4f[element.uuid] = displayTransformationMatrix

                            if (existingEntity != null) {
                                // The != check is to fix jittery elements that had a dynamic transformation set but doesn't have them anymore
                                if (currentTransform != displayTransformationMatrix) {
                                    existingEntity.interpolationDelay = -1
                                    existingEntity.interpolationDuration = INTERPOLATION_DURATION_TICKS
                                    existingEntity.setTransformationMatrix(displayTransformationMatrix)
                                }
                            } else {
                                val entity = location.world.spawn(
                                    // We INTENTIONALLY use topY instead of centerY, because Minecraft scales based on the item's TOP LOCATION, not the CENTER
                                    location,
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

                                    it.setTransformationMatrix(displayTransformationMatrix)

                                    it.setItemStack(
                                        ItemStack.of(propMapper.item.material)
                                            .meta<ItemMeta> {
                                                this.itemModel = NamespacedKey.fromString(propMapper.item.itemModel)

                                                if (this is SkullMeta) {
                                                    val playerProfileSkin = propMapper.item.playerProfileSkin

                                                    if (playerProfileSkin != null) {
                                                        val profile = Bukkit.createProfile(UUID(0L, 0L), "")
                                                        profile.setProperty(
                                                            ProfileProperty(
                                                                "textures",
                                                                playerProfileSkin.value,
                                                                playerProfileSkin.signature
                                                            )
                                                        )

                                                        this.playerProfile = profile
                                                    }
                                                }
                                            }
                                    )

                                    it.brightness = propMapper.brightness?.let {
                                        Display.Brightness(
                                            it.blockLight,
                                            it.skyLight
                                        )
                                    }
                                }

                                elementIdToEntities[element.uuid] = entity
                            }
                        }
                        is PropMapper.TextDisplay -> {
                            // Text Displays are more finicky when using the billboard function (except when using FIXED), because it rotates around the entity's origin
                            // It also borks out when using any kind of rotation
                            //
                            // You can't extract a "usable" euler angle from the rotation matrix
                            // In fact, when implementing cameras in LWJGL, the right way is to store a pos/rot and then create a Matrix4f from that
                            // https://lwjglgamedev.gitbooks.io/3d-game-development-with-lwjgl/content/chapter08/chapter8.html
                            // So we are going to skill all of that, and use the rotation of the current element as the yaw/pitch

                            // We DO NOT want rotation, because that causes the nameplate to rotate based on its origin
                            val transformedPos = Vector3f((centerX + offsetX).toFloat(), (centerY + offsetY).toFloat(), (centerZ + offsetZ).toFloat())
                            matrix4f.transformPosition(transformedPos)

                            val nameplateLocation = location.clone()
                                .add(
                                    transformedPos.x.toDouble(),
                                    transformedPos.y.toDouble(),
                                    transformedPos.z.toDouble()
                                )

                            val scale = matrix4f.getScale(Vector3f())

                            val displayTransformationMatrix = Matrix4f()
                                .scale(scale.x * propMapper.scaleX, scale.y * propMapper.scaleY, scale.z * propMapper.scaleZ)

                            val existingEntity = elementIdToEntities[element.uuid]

                            val currentTransform = elementIdToMatrix4f[element.uuid]
                            elementIdToMatrix4f[element.uuid] = displayTransformationMatrix

                            if (existingEntity != null) {
                                // The != check is to fix jittery elements that had a dynamic transformation set but doesn't have them anymore
                                if (currentTransform != displayTransformationMatrix) {
                                    existingEntity.interpolationDelay = -1
                                    existingEntity.interpolationDuration = INTERPOLATION_DURATION_TICKS
                                    existingEntity.setTransformationMatrix(displayTransformationMatrix)
                                }
                            } else {
                                val entity = location.world.spawn(
                                    nameplateLocation,
                                    TextDisplay::class.java
                                ) {
                                    // A normal player head item has 0.5 scale in Blockbench
                                    // A cube is 2x2x2 in Blockbench
                                    it.interpolationDelay = -1
                                    // We do 2 interpolation duration because 1 feels like it doesn't interpolate anything at all
                                    // We should always keep this (delay between frames) + 1

                                    it.interpolationDuration = INTERPOLATION_DURATION_TICKS
                                    it.teleportDuration = 1
                                    it.isPersistent = false

                                    it.setTransformationMatrix(displayTransformationMatrix)
                                    it.billboard = propMapper.billboard
                                    it.backgroundColor = Color.fromARGB(propMapper.backgroundColor)
                                    it.brightness = propMapper.brightness?.let {
                                        Display.Brightness(
                                            it.blockLight,
                                            it.skyLight
                                        )
                                    }

                                    it.text(MiniMessage.miniMessage().deserialize(propMapper.text))
                                }

                                elementIdToEntities[element.uuid] = entity
                            }
                        }
                    }
                }
            }

            for (childrenOutliner in outline.children.filterIsInstance<BlockbenchModel.ChildrenOutliner.NestedOutliner>().filter { it.outliner.visibility }) {
                processOutliner(
                    childrenOutliner.outliner,
                    matrix4f,
                    offsetX,
                    offsetY,
                    offsetZ,
                    parentRotX + outlineRotationX,
                    parentRotY + outlineRotationY,
                    parentRotZ + outlineRotationZ,
                )
            }
        }

        for (outline in blockbenchModel.outliner.filter { it.visibility }) {
            // The scale sets the "target scale" of the scene
            processOutliner(outline, Matrix4f().scale(TARGET_SCALE).rotateY(Math.toRadians(targetYaw.toDouble()).toFloat()), 0.0, 0.0, 0.0, 0.0, targetYaw.toDouble(), 0.0)
        }

        for (tickActions in currentAction.onTick) {
            if (tickActions.tick == relativeTicksCurrentGestureLived) {
                for (action in tickActions.actions) {
                    when (action) {
                        is GestureAction.TickAction.PlaySound -> {
                            soundEmitter.world.playSound(
                                soundEmitter,
                                action.soundKey,
                                action.volume,
                                action.pitch
                            )
                        }

                        is GestureAction.TickAction.TextDisplayText -> {
                            val element = blockbenchModel.elements.first { it.name == action.elementName }
                            val entity = elementIdToEntities[element.uuid] as TextDisplay
                            entity.text(MiniMessage.miniMessage().deserialize(action.text))
                        }
                    }
                }
            }
        }

        // We need to tick it BEFORE processing end action events
        this.relativeTicksCurrentGestureLived++

        val totalRepeats = if (animationDurationInTicks == 0) {
            relativeTicksCurrentGestureLived
        } else {
            relativeTicksCurrentGestureLived / animationDurationInTicks
        }

        // Bukkit.broadcastMessage("Total repeats: $totalRepeats")

        if (totalRepeats >= currentAction.loopCount) {
            // It's finished, let's execute the finish action!
            when (val onFinishAction = this.currentAction.onFinish) {
                is GestureAction.OnFinishAction.JumpToAction -> {
                    // Reset and jump to the new action!
                    this.relativeTicksCurrentGestureLived = 0
                    this.currentAction = sparklyGesture.actions.first { it.name == onFinishAction.name }
                    this.progressTimelineWithoutLooping = false
                }
                GestureAction.OnFinishAction.HoldAction -> {
                    // We don't actually need to do anything
                    this.progressTimelineWithoutLooping = true
                    return
                }
                GestureAction.OnFinishAction.StopGestureAction -> {
                    // It's so joever
                    m.gesturesManager.stopGesturePlayback(player)
                    this.progressTimelineWithoutLooping = false
                    return
                }
            }
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
        soundEmitter.remove()
    }
}