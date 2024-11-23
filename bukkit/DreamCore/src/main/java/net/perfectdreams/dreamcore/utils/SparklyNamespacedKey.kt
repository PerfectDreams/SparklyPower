package net.perfectdreams.dreamcore.utils

import io.papermc.paper.persistence.PersistentDataContainerView
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

// Yeah yeah, deprecated because it is "internal use only", but we don't really care tbh, we are 99,99% sure that Mojang will never use "sparklypower" as the namespace
// and this is useful if we end up changing the plugin name
fun SparklyNamespacedKey(key: String) = NamespacedKey("sparklypower", key)

fun <T, Z> SparklyNamespacedKey(key: String, type: PersistentDataType<T, Z>) = SparklyNamespacedKeyWithType(SparklyNamespacedKey(key), type)
class SparklyNamespacedKeyWithType<T, Z>(val key: NamespacedKey, val type: PersistentDataType<T, Z>)

// PersistentDataContainerView is immutable, PersistentDataContainer is mutable
fun <T, Z> PersistentDataContainerView.get(key: SparklyNamespacedKeyWithType<T, Z>) = this.get(key.key, key.type)
fun <T, Z> PersistentDataContainerView.get(key: SparklyNamespacedBooleanKey<T, Z>): Boolean {
    // This code is like this because FOR SOME REASON something changed in Paper that is now causing a slew of errors
    // "java.lang.NullPointerException: Cannot invoke "java.lang.Number.byteValue()" because the return value of "org.bukkit.persistence.PersistentDataContainer.get(org.bukkit.NamespacedKey, org.bukkit.persistence.PersistentDataType)" is null"
    val result = this.get(key.key, PersistentDataType.BYTE) ?: return false
    return result == 1.toByte()
}

fun <T, Z : Any> PersistentDataContainer.set(key: SparklyNamespacedKeyWithType<T, Z>, value: Z) = this.set(key.key, key.type, value)
fun <T, Z> PersistentDataContainer.remove(key: SparklyNamespacedKeyWithType<T, Z>) = this.remove(key.key)

fun SparklyNamespacedBooleanKey(key: String) = SparklyNamespacedBooleanKey<Boolean, Boolean>(SparklyNamespacedKey(key))
class SparklyNamespacedBooleanKey<T, Z>(val key: NamespacedKey)

fun <T, Z : Any> PersistentDataContainer.set(key: SparklyNamespacedBooleanKey<T, Z>, value: Boolean) = this.set(key.key, PersistentDataType.BYTE, if (value) 1 else 0)

fun <T, Z> PersistentDataContainer.remove(key: SparklyNamespacedBooleanKey<T, Z>) = this.remove(key.key)
