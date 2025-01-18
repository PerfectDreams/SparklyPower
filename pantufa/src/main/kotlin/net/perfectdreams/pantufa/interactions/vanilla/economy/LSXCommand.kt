package net.perfectdreams.pantufa.interactions.vanilla.economy

import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.toMessageFormat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toKotlinInstant
import net.perfectdreams.loritta.cinnamon.pudding.utils.SimpleSonhosTransactionsLogUtils
import net.perfectdreams.loritta.common.commands.CommandCategory
import net.perfectdreams.loritta.common.utils.SparklyPowerLSXTransactionEntryAction
import net.perfectdreams.loritta.morenitta.interactions.UnleashedContext
import net.perfectdreams.loritta.morenitta.interactions.commands.*
import net.perfectdreams.loritta.morenitta.interactions.commands.options.ApplicationCommandOptions
import net.perfectdreams.loritta.morenitta.interactions.commands.options.OptionReference
import net.perfectdreams.loritta.serializable.StoredSparklyPowerLSXSonhosTransaction
import net.perfectdreams.pantufa.PantufaBot
import net.perfectdreams.pantufa.api.commands.PantufaReply
import net.perfectdreams.pantufa.api.commands.styled
import net.perfectdreams.pantufa.api.economy.TransferOptions
import net.perfectdreams.pantufa.dao.Profile
import net.perfectdreams.pantufa.dao.Transaction
import net.perfectdreams.pantufa.api.economy.TransactionCurrency
import net.perfectdreams.pantufa.api.economy.TransactionType
import net.perfectdreams.pantufa.network.Databases
import net.perfectdreams.pantufa.tables.LuckPermsUserPermissions
import net.perfectdreams.pantufa.tables.PlayerSonecas
import net.perfectdreams.pantufa.tables.Profiles
import net.perfectdreams.pantufa.utils.*
import net.perfectdreams.pantufa.utils.extensions.lorittaProfile
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class LSXCommand : SlashCommandDeclarationWrapper {
    companion object {
        val mutex = Mutex()
        var loriToSparklyExchangeRate = 2L

        fun withdrawFromLoritta(
            profile: Profile,
            playerName: String,
            playerUniqueId: UUID,
            quantity: Long,
            sparklyPowerQuantity: Long
        ): Boolean {
            return transaction(Databases.loritta) {
                profile.refresh()

                if (quantity > profile.money)
                    return@transaction false

                Profiles.update({ Profiles.id eq profile.userId }) {
                    with (SqlExpressionBuilder) {
                        it.update(money, money - quantity)
                    }
                }

                val now = Instant.now()

                SimpleSonhosTransactionsLogUtils.insert(
                    profile.userId,
                    now,
                    net.perfectdreams.loritta.common.utils.TransactionType.SPARKLYPOWER_LSX,
                    quantity,
                    StoredSparklyPowerLSXSonhosTransaction(
                        SparklyPowerLSXTransactionEntryAction.EXCHANGED_TO_SPARKLYPOWER,
                        sparklyPowerQuantity,
                        playerName,
                        playerUniqueId.toString(),
                        loriToSparklyExchangeRate.toDouble()
                    )
                )

                return@transaction true
            }
        }

        fun withdrawFromSparklyPower(
            playerUniqueId: UUID,
            quantity: Long
        ): Boolean {
            return transaction(Databases.sparklyPower) {
                val playerSonecasData = PlayerSonecas.selectAll().where {
                    PlayerSonecas.id eq playerUniqueId
                }.firstOrNull()

                if (playerSonecasData == null)
                    return@transaction false

                if (quantity > playerSonecasData[PlayerSonecas.money].toDouble())
                    return@transaction false

                PlayerSonecas.update({ PlayerSonecas.id eq playerUniqueId }) {
                    with(SqlExpressionBuilder) {
                        it.update(money, money - quantity.toBigDecimal())
                    }
                }

                return@transaction true
            }
        }

        fun giveToSparklyPower(playerUniqueId: UUID, quantity: Long): Boolean {
            return transaction(Databases.sparklyPower) {
                val playerSonecasData = PlayerSonecas.selectAll().where {
                    PlayerSonecas.id eq playerUniqueId
                }.firstOrNull()

                if (playerSonecasData == null)
                    return@transaction false

                PlayerSonecas.update({ PlayerSonecas.id eq playerUniqueId }) {
                    with(SqlExpressionBuilder) {
                        it.update(money, money + quantity.toBigDecimal())
                    }
                }

                return@transaction true
            }
        }

        fun giveToLoritta(
            profile: Profile,
            playerName: String,
            playerUniqueId: UUID,
            quantity: Long,
            sparklyPowerQuantity: Long
        ): Boolean {
            return transaction(Databases.loritta) {
                Profiles.update({ Profiles.id eq profile.userId }) {
                    with (SqlExpressionBuilder) {
                        it.update(money, money + quantity)
                    }
                }

                val now = Instant.now()

                SimpleSonhosTransactionsLogUtils.insert(
                    profile.userId,
                    now,
                    net.perfectdreams.loritta.common.utils.TransactionType.SPARKLYPOWER_LSX,
                    quantity,
                    StoredSparklyPowerLSXSonhosTransaction(
                        SparklyPowerLSXTransactionEntryAction.EXCHANGED_FROM_SPARKLYPOWER,
                        sparklyPowerQuantity,
                        playerName,
                        playerUniqueId.toString(),
                        loriToSparklyExchangeRate.toDouble()
                    )
                )
                return@transaction true
            }
        }
    }

    override fun command() = slashCommand("transferir", "Transfira sonhos da Loritta para o SparklyPower! (vice-versa)", CommandCategory.ECONOMY) {
        enableLegacyMessageSupport = true
        requireMinecraftAccount = true
        alternativeLegacyAbsoluteCommandPaths.apply {
            add("lsx")
        }

        executor = LSXCommandExecutor()
    }

    inner class LSXCommandExecutor : LorittaSlashCommandExecutor(), LorittaLegacyMessageCommandExecutor {
        inner class Options : ApplicationCommandOptions() {
            val source = optionalString("source", "Fonte do dinheiro (Sonhos ou Sonecas)") {
                choice("SparklyPower Survival", TransferOptions.SPARKLYPOWER_SURVIVAL.codeName)
                choice("Loritta :3", TransferOptions.LORITTA.codeName)
            }

            val destination = optionalString("destination", "Destino do dinheiro (Sonhos ou Sonecas)") {
                choice("SparklyPower Survival", TransferOptions.SPARKLYPOWER_SURVIVAL.codeName)
                choice("Loritta :3", TransferOptions.LORITTA.codeName)
            }

            val quantity = optionalString("quantity", "Quantidade de dinheiro a ser transferido!")
        }

        override val options = Options()

        override suspend fun execute(context: UnleashedContext, args: SlashCommandArguments) {
            val source = args[options.source]
            val destination = args[options.destination]
            val quantity = args[options.quantity]

            context.deferChannelMessage(false)

            val profile = context.user.lorittaProfile()
            val bannedState = profile.getBannedState()

            if (bannedState != null) {
                context.reply(false) {
                    styled(
                        "Você está banido de utilizar a Loritta!"
                    )
                }

                return
            }

            val accountInfo = context.retrieveConnectedMinecraftAccount()!!

            val userBan = context.getUserBanned(accountInfo.uniqueId)

            if (userBan != null) {
                context.reply(false) {
                    styled(
                        "Você está banido do SparklyPower!"
                    )
                }

                return
            }

            val survivalOnlineTrackedHours = context.pantufa.getPlayerTimeOnlineInTheLastXDays(accountInfo.uniqueId, 30)

            val playerUniqueId = accountInfo.uniqueId
            val requiredHours = getRequiredOnlineHours(context.pantufa, playerUniqueId)

            if (Duration.ofHours(requiredHours) >= survivalOnlineTrackedHours.duration) {
                context.reply(false) {
                    styled(
                        "Você precisa ter mais de $requiredHours horas online no SparklyPower Survival nos últimos 30 dias (desde ${survivalOnlineTrackedHours.since.toInstant().toKotlinInstant().toMessageFormat(
                            DiscordTimestampStyle.ShortDate)}) antes de poder transferir sonhos! Atualmente você tem ${
                            (survivalOnlineTrackedHours.duration.get(
                                ChronoUnit.SECONDS
                            ).div(3_600))
                        } horas.",
                        "\uD83D\uDCB5"
                    )
                }

                return
            }

            if (source != null && destination != null) {
                mutex.withLock {
                    val refreshedProfile = context.user.lorittaProfile()
                    val refreshedAccountInfo = context.retrieveConnectedMinecraftAccount()!!

                    val from = TransferOptions.entries.firstOrNull { it.codeName == source }
                    val to = TransferOptions.entries.firstOrNull { it.codeName == destination }

                    if (from != null && to != null && quantity != null) {
                        val parsedQuantity = NumberUtils.convertShortenedNumberToLong(quantity)

                        if (parsedQuantity == null) {
                            context.reply(false) {
                                styled(
                                    "Quantidade inválida!",
                                    Constants.ERROR
                                )
                            }

                            return@withLock
                        }

                        if (from == to)
                            return@withLock

                        if (0 >= parsedQuantity)
                            return@withLock

                        if (from == TransferOptions.LORITTA && to == TransferOptions.SPARKLYPOWER_SURVIVAL) {
                            val sparklyPowerQuantity = parsedQuantity * loriToSparklyExchangeRate

                            val fromBalance = withdrawFromLoritta(
                                refreshedProfile,
                                refreshedAccountInfo.username,
                                refreshedAccountInfo.uniqueId,
                                parsedQuantity,
                                sparklyPowerQuantity
                            )

                            if (!fromBalance) {
                                context.reply(false) {
                                    styled(
                                        "Você não possui dinheiro suficiente em `${from.fancyName}` para transferência!",
                                        Constants.ERROR
                                    )
                                }

                                return@withLock
                            }

                            giveToSparklyPower(
                                refreshedAccountInfo.uniqueId,
                                sparklyPowerQuantity
                            )

                            context.reply(false) {
                                styled(
                                    "Você transferiu **${parsedQuantity} Sonecas** (Valor final: $sparklyPowerQuantity) de `${from.fancyName}` para `${to.fancyName}`!",
                                    "\uD83D\uDCB8"
                                )
                            }

                            transaction(Databases.sparklyPower) {
                                Transaction.new {
                                    this.type = TransactionType.LSX
                                    this.receiver = refreshedAccountInfo.uniqueId
                                    this.currency = TransactionCurrency.MONEY
                                    this.time = System.currentTimeMillis()
                                    this.amount = sparklyPowerQuantity.toDouble()
                                    this.extra = context.user.id
                                }
                            }
                        } else if (from == TransferOptions.SPARKLYPOWER_SURVIVAL && to == TransferOptions.LORITTA) {
                            val lorittaQuantity = parsedQuantity / loriToSparklyExchangeRate

                            val fromBalance = withdrawFromSparklyPower(
                                refreshedAccountInfo.uniqueId,
                                parsedQuantity
                            )

                            if (!fromBalance) {
                                context.reply(false) {
                                    styled(
                                        "Você não possui dinheiro suficiente em `${from.fancyName}` para transferência!",
                                        Constants.ERROR
                                    )
                                }

                                return@withLock
                            }

                            giveToLoritta(
                                refreshedProfile,
                                refreshedAccountInfo.username,
                                refreshedAccountInfo.uniqueId,
                                lorittaQuantity,
                                parsedQuantity
                            )

                            context.reply(false) {
                                styled(
                                    "Você transferiu **${quantity} Sonhos** (Valor final: $lorittaQuantity) de `${from.fancyName}` para `${to.fancyName}`!",
                                    "\uD83D\uDCB8"
                                )
                            }

                            transaction(Databases.sparklyPower) {
                                Transaction.new {
                                    this.type = TransactionType.LSX
                                    this.payer = refreshedAccountInfo.uniqueId
                                    this.currency = TransactionCurrency.MONEY
                                    this.time = System.currentTimeMillis()
                                    this.amount = parsedQuantity.toDouble()
                                    this.extra = context.user.id
                                }
                            }
                        }
                        return@withLock
                    }
                }
            } else {
                val playerSonecasBalance = context.getPlayerSonecasBalance(accountInfo.uniqueId)

                val replies = mutableListOf(
                    PantufaReply(
                        "**LorittaLand Sonhos Exchange Service (LSX)**",
                        "\uD83D\uDCB5"
                    ),
                    PantufaReply(
                        "`-transferir Fonte Destino Quantidade`",
                        mentionUser = false
                    ),
                    PantufaReply(
                        "**Câmbio de Sonhos:**"
                    ),
                    PantufaReply(
                        "Um sonho da `loritta` equivalem a $loriToSparklyExchangeRate sonecas no `survival`"
                    ),
                    PantufaReply(
                        "*Locais disponíveis para transferência...*",
                        mentionUser = false
                    ),
                    PantufaReply(
                        "**Loritta** `${TransferOptions.LORITTA.codeName}` (*${profile.money} sonhos*)",
                        "<:sparklyPower:331179879582269451>",
                        mentionUser = false
                    ),
                    PantufaReply(
                        "**SparklyPower Survival** `${TransferOptions.SPARKLYPOWER_SURVIVAL.codeName}` (*$playerSonecasBalance sonecas*)",
                        "<a:pantufa_pickaxe:997671670468853770>",
                        mentionUser = false
                    )
                )

                context.reply(false) {
                    for (reply in replies) {
                        styled(reply)
                    }
                }
                return
            }
        }

        override suspend fun convertToInteractionsArguments(
            context: LegacyMessageCommandContext,
            args: List<String>
        ): Map<OptionReference<*>, Any?> {
            val source = args.getOrNull(0)
            val destination = args.getOrNull(1)
            val quantity = args.getOrNull(2)

            return mapOf(
                options.source to source?.lowercase(),
                options.destination to destination?.lowercase(),
                options.quantity to quantity
            )
        }

        suspend fun getRequiredOnlineHours(pantufa: PantufaBot, playerUniqueId: UUID): Long {
            val userPerms = pantufa.transactionOnLuckPermsDatabase {
                LuckPermsUserPermissions.selectAll().where {
                    LuckPermsUserPermissions.uuid eq playerUniqueId.toString()
                }.toList()
            }

            val bypassTransferPermission = userPerms.firstOrNull { it[LuckPermsUserPermissions.permission] == "group.dono" || it[LuckPermsUserPermissions.permission] == "group.admin" || it[LuckPermsUserPermissions.permission] == "group.moderador" }
            val vipPlusPlusPermission = userPerms.firstOrNull { it[LuckPermsUserPermissions.permission] == "group.vip++" }

            return if (bypassTransferPermission != null) {
                0
            } else if (vipPlusPlusPermission != null) {
                12
            } else {
                24
            }
        }
    }
}