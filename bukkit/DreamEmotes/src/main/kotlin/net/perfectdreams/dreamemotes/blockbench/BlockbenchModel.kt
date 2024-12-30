package net.perfectdreams.dreamemotes.blockbench

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.sparklypower.rpc.UUIDAsStringSerializer
import java.util.*

// .bbmodel
@Serializable
data class BlockbenchModel(
    val elements: List<Element>,
    // This CAN be a list of UUIDs if there aren't any groups
    val outliner: List<Outliner>,
    val animations: List<Animation> = listOf()
) {
    @Serializable
    data class Element(
        val name: String,
        val from: List<Double>,
        val to: List<Double>,
        val origin: List<Double>,
        val uuid: @Serializable(with = UUIDAsStringSerializer::class) UUID,
        val visibility: Boolean = true
    )

    @Serializable
    data class Outliner(
        val name: String,
        @Serializable(with = UUIDAsStringSerializer::class)
        val uuid: UUID,
        val origin: List<Double>,
        val rotation: List<Double> = listOf(0.0, 0.0, 0.0),
        // This is a list of JSON Objects (nested outliners) OR a UUID
        val children: List<@Serializable(with = ChildrenOutlinerSerializer::class) ChildrenOutliner>,
        val visibility: Boolean
    )

    @Serializable
    sealed class ChildrenOutliner {
        @Serializable
        data class ElementReference(@Serializable(with = UUIDAsStringSerializer::class) val uuid: UUID) : ChildrenOutliner()

        @Serializable
        data class NestedOutliner(val outliner: Outliner) : ChildrenOutliner()
    }

    @Serializable
    data class Animation(
        @Serializable(with = UUIDAsStringSerializer::class)
        val uuid: UUID,
        val name: String,
        val length: Double,
        // This can be a UUID (for outlines/elements) or a string ("effects")
        val animators: Map<String, Animator> = mapOf()
    )

    @Serializable
    data class Animator(
        val name: String,
        val keyframes: List<@Serializable(with = KeyframeSerializer::class) Keyframe>
    )

    @Serializable
    sealed class Keyframe {
        @Serializable
        data class Position(
            val channel: String,
            @SerialName("data_points")
            val dataPoints: List<VectorDataPoint>,
            val time: Double,
            val interpolation: String,
        ) : Keyframe()

        @Serializable
        data class Rotation(
            val channel: String,
            @SerialName("data_points")
            val dataPoints: List<VectorDataPoint>,
            val time: Double,
            val interpolation: String,
        ) : Keyframe()

        @Serializable
        data class Scale(
            val channel: String,
            @SerialName("data_points")
            val dataPoints: List<VectorDataPoint>,
            val time: Double,
            val interpolation: String,
        ) : Keyframe()

        @Serializable
        data class Sound(
            val channel: String,
            @SerialName("data_points")
            val dataPoints: List<SoundDataPoint>,
            val time: Double,
            val interpolation: String,
        ) : Keyframe()
    }

    @Serializable
    data class SoundDataPoint(
        val effect: String,
        val file: String
    )

    @Serializable
    data class VectorDataPoint(
        val x: Double,
        val y: Double,
        val z: Double
    )
}

object ChildrenOutlinerSerializer : JsonContentPolymorphicSerializer<BlockbenchModel.ChildrenOutliner>(BlockbenchModel.ChildrenOutliner::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        element is JsonObject -> BBModelNestedOutlinerSerializer
        element is JsonPrimitive && element.isString -> BBModelNestedUUIDSerializer
        else -> error("I don't know how to parse this")
    }

    object BBModelNestedOutlinerSerializer : KSerializer<BlockbenchModel.ChildrenOutliner.NestedOutliner> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BBModelNestedOutlinerSerializer", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BlockbenchModel.ChildrenOutliner.NestedOutliner) {
            TODO()
        }

        override fun deserialize(decoder: Decoder): BlockbenchModel.ChildrenOutliner.NestedOutliner {
            val result = BlockbenchModel.Outliner.serializer().deserialize(decoder)
            return BlockbenchModel.ChildrenOutliner.NestedOutliner(result)
        }
    }

    object BBModelNestedUUIDSerializer : KSerializer<BlockbenchModel.ChildrenOutliner.ElementReference> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BBModelNestedUUIDSerializer", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BlockbenchModel.ChildrenOutliner.ElementReference) {
            TODO()
        }

        override fun deserialize(decoder: Decoder): BlockbenchModel.ChildrenOutliner.ElementReference {
            return BlockbenchModel.ChildrenOutliner.ElementReference(UUID.fromString(decoder.decodeString()))
        }
    }
}

object KeyframeSerializer : JsonContentPolymorphicSerializer<BlockbenchModel.Keyframe>(BlockbenchModel.Keyframe::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<BlockbenchModel.Keyframe> {
        val channel = element.jsonObject["channel"]!!.jsonPrimitive.content

        return when {
            channel == "position" -> BlockbenchModel.Keyframe.Position.serializer()
            channel == "rotation" -> BlockbenchModel.Keyframe.Rotation.serializer()
            channel == "scale" -> BlockbenchModel.Keyframe.Scale.serializer()
            channel == "sound" -> BlockbenchModel.Keyframe.Sound.serializer()
            else -> error("I don't know how to parse this")
        }
    }
}