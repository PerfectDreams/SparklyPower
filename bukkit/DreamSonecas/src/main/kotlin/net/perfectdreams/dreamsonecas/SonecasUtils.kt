package net.perfectdreams.dreamsonecas

import kotlinx.coroutines.Dispatchers
import net.kyori.adventure.text.format.NamedTextColor
import net.perfectdreams.dreamcore.dao.Ban
import net.perfectdreams.dreamcore.tables.Bans
import net.perfectdreams.dreamcore.tables.TrackedOnlineHours
import net.perfectdreams.dreamcore.tables.Users
import net.perfectdreams.dreamcore.utils.Databases
import net.perfectdreams.dreamcore.utils.DreamUtils
import net.perfectdreams.dreamcore.utils.TransactionContext
import net.perfectdreams.dreamcore.utils.TransactionType
import net.perfectdreams.dreamcore.utils.adventure.append
import net.perfectdreams.dreamcore.utils.adventure.textComponent
import net.perfectdreams.dreamsonecas.commands.SonecasCommand.Companion.prefix
import net.perfectdreams.dreamsonecas.tables.PlayerSonecas
import org.bukkit.Bukkit
import org.bukkit.SoundCategory
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.util.*

object SonecasUtils {
    private val brazilLocale = Locale("pt", "BR")
    private val numberFormat = NumberFormat.getNumberInstance(brazilLocale)
        .apply {
            this.minimumFractionDigits = 2
            this.maximumFractionDigits = 2
        }

    fun formatSonecasAmount(amount: Double): String {
        return numberFormat.format(amount)
    }

    fun formatSonecasAmountWithCurrencyName(amount: Double): String {
        val formattedNumber = formatSonecasAmount(amount)

        return if (amount == 1.0) {
            "$formattedNumber soneca"
        } else {
            "$formattedNumber sonecas"
        }
    }

    /**
     * Transfers sonecas from one player to another player
     */
    suspend fun transferSonecasFromPlayerToPlayer(
        giverName: String,
        giverUniqueId: UUID,
        receiverName: String,
        quantity: Double,
        bypassLastActiveTime: Boolean
    ): TransferSonhosResult {
        return net.perfectdreams.exposedpowerutils.sql.transaction(Dispatchers.IO, Databases.databaseNetwork) {
            // Does the other account exist?
            val receiverData = Users.selectAll().where { Users.username eq receiverName }.firstOrNull()

            // The other user does not exist at all!
            if (receiverData == null)
                return@transaction TransferSonhosResult.UserDoesNotExist

            return@transaction _transferSonecasFromPlayerToPlayer(
                giverUniqueId,
                receiverData,
                quantity,
                bypassLastActiveTime
            )
        }.also { postTransfer(giverName, giverUniqueId, it) }
    }

    /**
     * Transfers sonecas from one player to another player
     */
    suspend fun transferSonecasFromPlayerToPlayer(
        giverName: String,
        giverUniqueId: UUID,
        receiverUniqueId: UUID,
        quantity: Double,
        bypassLastActiveTime: Boolean
    ): TransferSonhosResult {
        return net.perfectdreams.exposedpowerutils.sql.transaction(Dispatchers.IO, Databases.databaseNetwork) {
            // Does the other account exist?
            val receiverData = Users.selectAll().where { Users.id eq receiverUniqueId }.firstOrNull()

            // The other user does not exist at all!
            if (receiverData == null)
                return@transaction TransferSonhosResult.UserDoesNotExist

            return@transaction _transferSonecasFromPlayerToPlayer(
                giverUniqueId,
                receiverData,
                quantity,
                bypassLastActiveTime
            )
        }.also { postTransfer(giverName, giverUniqueId, it) }
    }

    private fun postTransfer(giverName: String, giverUniqueId: UUID, result: TransferSonhosResult) {
        if (result is TransferSonhosResult.Success) {
            // This sucks
            TransactionContext(
                payer = giverUniqueId,
                receiver = result.receiverId,
                type = TransactionType.PAYMENT,
                amount = result.quantityGiven
            ).saveToDatabase()

            // Is the player online?
            val receiverPlayer = Bukkit.getPlayer(result.receiverId)

            if (receiverPlayer != null) {
                receiverPlayer.sendMessage(
                    textComponent {
                        color(NamedTextColor.GREEN)
                        append(prefix())
                        appendSpace()

                        append("Você recebeu ")
                        append(NamedTextColor.WHITE, "\uE283")
                        appendSpace()
                        append(NamedTextColor.GREEN, SonecasUtils.formatSonecasAmountWithCurrencyName(result.quantityGiven))
                        append(" de ")
                        append(NamedTextColor.AQUA, giverName)
                        append("!")
                    }
                )

                receiverPlayer.sendMessage(
                    textComponent {
                        color(NamedTextColor.YELLOW)
                        append(prefix())
                        appendSpace()

                        append("Você agora possui ")
                        append(NamedTextColor.WHITE, "\uE283")
                        appendSpace()
                        append(NamedTextColor.GREEN, SonecasUtils.formatSonecasAmountWithCurrencyName(result.receiverMoney))
                        append(" e está em #${result.receiverRanking} lugar no ranking!")
                    }
                )

                receiverPlayer.playSound(receiverPlayer.location, "sparklypower.sfx.money", SoundCategory.RECORDS, 1.0f, DreamUtils.random.nextFloat(0.9f, 1.1f))
            }
        }
    }

    private fun _transferSonecasFromPlayerToPlayer(
        giverUniqueId: UUID,
        receiverData: ResultRow,
        quantity: Double,
        bypassLastActiveTime: Boolean
    ): TransferSonhosResult {
        // You can't transfer sonecas to yourself!
        if (receiverData[Users.id].value == giverUniqueId)
            return TransferSonhosResult.CannotTransferSonecasToSelf

        // check if the player is banned
        if (Ban.find { Bans.player eq giverUniqueId }.any())
            return TransferSonhosResult.YouAreBanned

        // check if the target player is banned
        if (Ban.find { Bans.player eq receiverData[Users.id].value }.any())
            return TransferSonhosResult.YouAreTryingToTransferToABannedUser

        // Do we have enough money?
        val selfSonecas = PlayerSonecas.selectAll().where { PlayerSonecas.id eq giverUniqueId }.firstOrNull()?.get(PlayerSonecas.money)?.toDouble() ?: 0.0

        if (quantity > selfSonecas) {
            // You don't have enough naps!
            return TransferSonhosResult.NotEnoughSonecas(selfSonecas)
        }

        // Before we transfer, we will check if the user has logged in for the last 14 days, if they haven't, we will send a warning to them
        if (!bypassLastActiveTime) {
            // When was the last time that player logged in?
            val lastTimeThatTheUserLoggedIn = TrackedOnlineHours.select(TrackedOnlineHours.loggedIn).where {
                TrackedOnlineHours.player eq receiverData[Users.id].value
            }.orderBy(TrackedOnlineHours.loggedIn, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(TrackedOnlineHours.loggedIn)

            if (lastTimeThatTheUserLoggedIn == null || lastTimeThatTheUserLoggedIn.isBefore(Instant.now().minusSeconds(86400 * 14))) { // 14 days
                return TransferSonhosResult.PlayerHasNotJoinedRecently
            }
        }

        // We need to manually create the account if the user does not have a sonecas account yet
        // We don't need to create an account for the current user because if the player has != 0.0 then it means that they have an account already
        if (PlayerSonecas.selectAll().where { PlayerSonecas.id eq receiverData[Users.id].value }.count() == 0L) {
            PlayerSonecas.insert {
                it[PlayerSonecas.id] = receiverData[Users.id].value
                it[PlayerSonecas.money] = BigDecimal.ZERO
                it[PlayerSonecas.updatedAt] = Instant.now()
            }
        }

        // Take money!!!
        val selfUpdateReturningStatement = PlayerSonecas.updateReturning(listOf(PlayerSonecas.money), { PlayerSonecas.id eq giverUniqueId }) {
            with(SqlExpressionBuilder) {
                it[PlayerSonecas.money] = PlayerSonecas.money - quantity.toBigDecimal()
            }
            it[PlayerSonecas.updatedAt] = Instant.now()
        }.first()
        val selfMoney = selfUpdateReturningStatement[PlayerSonecas.money]

        // Give money!!!
        val receiverUpdateReturningStatement = PlayerSonecas.updateReturning(listOf(PlayerSonecas.money), { PlayerSonecas.id eq receiverData[Users.id] }) {
            with(SqlExpressionBuilder) {
                it[PlayerSonecas.money] = PlayerSonecas.money + quantity.toBigDecimal()
            }
            it[PlayerSonecas.updatedAt] = Instant.now()
        }.first()
        val receiverMoney = receiverUpdateReturningStatement[PlayerSonecas.money]

        // And now figure out where are we in the ranking!
        val selfRanking = PlayerSonecas.selectAll().where { PlayerSonecas.money greaterEq selfMoney }
            .count()
        val receiverRanking = PlayerSonecas.selectAll().where { PlayerSonecas.money greaterEq receiverMoney }
            .count()

        return TransferSonhosResult.Success(
            receiverData[Users.username],
            receiverData[Users.id].value,
            quantity,
            selfMoney.toDouble(),
            selfRanking,
            receiverMoney.toDouble(),
            receiverRanking
        )
    }

    /**
     * Converts a shortened [String] number (1k, 1.5k, 1M, 2.3kk, etc) to a [Double] number
     *
     * This also converts a normal number (non shortened) to a [Double]
     *
     * @param input the shortened number
     * @return      the number as long or null if it is a non valid (example: text) number
     */
    fun convertShortenedNumberToLong(input: String): Double? {
        val inputAsLowerCase = input.lowercase()

        return when {
            inputAsLowerCase.endsWith("m") -> inputAsLowerCase.removeSuffix("m").toDoubleOrNull()?.times(1_000_000)
            inputAsLowerCase.endsWith("kk") -> inputAsLowerCase.removeSuffix("kk").toDoubleOrNull()?.times(1_000_000)
            inputAsLowerCase.endsWith("k") -> inputAsLowerCase.removeSuffix("k").toDoubleOrNull()?.times(1_000)
            else -> inputAsLowerCase.toDoubleOrNull()
        }
    }

    sealed class TransferSonhosResult {
        data object UserDoesNotExist : TransferSonhosResult()
        data object CannotTransferSonecasToSelf : TransferSonhosResult()
        data object PlayerHasNotJoinedRecently : TransferSonhosResult()
        data object YouAreBanned : TransferSonhosResult()
        data object YouAreTryingToTransferToABannedUser : TransferSonhosResult()
        data class NotEnoughSonecas(val currentUserMoney: Double) : TransferSonhosResult()
        data class Success(
            val receiverName: String,
            val receiverId: UUID,
            val quantityGiven: Double,
            val selfMoney: Double,
            val selfRanking: Long,
            val receiverMoney: Double,
            val receiverRanking: Long
        ) : TransferSonhosResult()
    }
}