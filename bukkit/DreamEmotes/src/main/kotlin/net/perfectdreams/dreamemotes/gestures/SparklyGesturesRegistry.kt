package net.perfectdreams.dreamemotes.gestures

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamemotes.DreamEmotes
import net.perfectdreams.dreamemotes.blockbench.BlockbenchModel
import java.io.File

object SparklyGesturesRegistry {
    val animations = mutableMapOf<String, SparklyGestureData>()
    val blockbenchModels = mutableMapOf<String, BlockbenchModel>()

    private fun getGestureById(id: String): SparklyGestureData {
        return animations[id]!!
    }

    /**
     * Reloads the SparklyPower gestures registry
     */
    fun reload(m: DreamEmotes) {
        // Clear all animations
        animations.clear()

        if (m.gesturesFolder.exists()) {
            for (gestureFile in m.gesturesFolder.listFiles()) {
                if (gestureFile.extension == "yml" || gestureFile.extension == "yaml") {
                    val gesture = Yaml.default.decodeFromString<SparklyGestureData>(gestureFile.readText(Charsets.UTF_8))

                    // Preload the blockbench model
                    // Multiple animations may use the same model, so we don't need to load if a model with the same name has already been loaded
                    if (!blockbenchModels.containsKey(gesture.blockbenchModel)) {
                        val blockbenchModel = Json {
                            ignoreUnknownKeys = true
                        }.decodeFromString<BlockbenchModel>(
                            File(m.modelsFolder, "${gesture.blockbenchModel}.bbmodel").readText()
                        )

                        blockbenchModels[gesture.blockbenchModel] = blockbenchModel
                    }

                    animations[gestureFile.nameWithoutExtension] = gesture
                }
            }
        }
    }
}