package net.perfectdreams.pantufa.interactions.vanilla.economy.transactions

import dev.minn.jda.ktx.interactions.components.option
import dev.minn.jda.ktx.messages.InlineEmbed
import dev.minn.jda.ktx.messages.InlineMessage
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.perfectdreams.pantufa.PantufaBot
import net.perfectdreams.pantufa.api.economy.TransactionCurrency
import net.perfectdreams.pantufa.api.economy.TransactionType
import net.perfectdreams.pantufa.dao.Transaction
import net.perfectdreams.pantufa.network.Databases
import net.perfectdreams.pantufa.tables.Transactions
import net.perfectdreams.pantufa.utils.*
import net.perfectdreams.pantufa.utils.extensions.await
import net.perfectdreams.pantufa.utils.extensions.username
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.math.ceil

object TransactionsUtils {
    private const val TRANSACTIONS_PER_PAGE = 10

    fun createMessage(
        m: PantufaBot,
        author: Long,
        playerUniqueId: UUID,
        viewingTransactionsOfPlayerUniqueId: UUID,
        page: Long,
        userFacingTransactionTypeFilter: List<TransactionType>,
        currency: TransactionCurrency? = null
    ): suspend InlineMessage<*>.() -> (Unit) = {
        // If the list is empty, we will use *all* transaction types in the filter
        // This makes it easier because you don't need to manually deselect every single filter before you can filter by a specific
        // transaction type.
        val transactionTypeFilter = userFacingTransactionTypeFilter.ifEmpty { TransactionType.entries.toList() }

        val transactions = retrievePlayerTransactions(
            m,
            viewingTransactionsOfPlayerUniqueId,
            transactionTypeFilter,
            TRANSACTIONS_PER_PAGE,
            (page * TRANSACTIONS_PER_PAGE),
            currency
        )

        val totalTransactions = retrievePlayerTotalTransactions(
            m,
            viewingTransactionsOfPlayerUniqueId,
            transactionTypeFilter,
            currency
        )

        val totalPages = ceil((totalTransactions / TRANSACTIONS_PER_PAGE.toDouble())).toLong()
        val isSelf = viewingTransactionsOfPlayerUniqueId == playerUniqueId

        if (page >= totalPages && totalPages != 0L) {
            // ===[ EASTER EGG: USER INPUT TOO MANY PAGES ]===
            apply(
                createTooManyPagesMessage(
                    m,
                    author,
                    playerUniqueId,
                    viewingTransactionsOfPlayerUniqueId,
                    totalPages,
                    transactionTypeFilter,
                    currency
                )
            )
        } else {
            embed {
                if (totalPages != 0L) {
                    createTransactionViewEmbed(
                        m,
                        viewingTransactionsOfPlayerUniqueId,
                        transactions,
                        page,
                        isSelf,
                        totalTransactions,
                        currency
                    )
                } else {
                    apply(
                        createNoMatchingTransactionsEmbed(
                            isSelf,
                            viewingTransactionsOfPlayerUniqueId,
                            currency
                        )
                    )
                }
            }

            val addLeftButton = page != 0L && totalTransactions != 0L
            val addRightButton = totalPages > (page + 1) && totalTransactions != 0L

            actionRow(
                if (addLeftButton) {
                    m.interactivityManager.buttonForUser(
                        author,
                        ButtonStyle.PRIMARY,
                        builder = {
                            emoji = Emoji.fromCustom(
                                Constants.LEFT_EMOJI.name!!,
                                Constants.LEFT_EMOJI.id!!.value.toLong(),
                                Constants.LEFT_EMOJI.animated.discordBoolean
                            )
                        }
                    ) {
                        it.deferAndEditOriginal {
                            val builtMessage = createMessage(
                                m,
                                author,
                                playerUniqueId,
                                viewingTransactionsOfPlayerUniqueId,
                                page - 1,
                                userFacingTransactionTypeFilter,
                                currency
                            )

                            builtMessage.invoke(this)
                        }
                    }
                } else {
                    m.interactivityManager.disabledButton(
                        ButtonStyle.PRIMARY,
                        builder = {
                            emoji = Emoji.fromCustom(
                                Constants.LEFT_EMOJI.name!!,
                                Constants.LEFT_EMOJI.id!!.value.toLong(),
                                Constants.LEFT_EMOJI.animated.discordBoolean
                            )
                        }
                    )
                },

                if (addRightButton) {
                    m.interactivityManager.buttonForUser(
                        author,
                        ButtonStyle.PRIMARY,
                        builder = {
                            emoji = Emoji.fromCustom(
                                Constants.RIGHT_EMOJI.name!!,
                                Constants.RIGHT_EMOJI.id!!.value.toLong(),
                                Constants.RIGHT_EMOJI.animated.discordBoolean
                            )
                        }
                    ) {
                        it.deferAndEditOriginal {
                            val builtMessage = createMessage(
                                m,
                                author,
                                playerUniqueId,
                                viewingTransactionsOfPlayerUniqueId,
                                page + 1,
                                userFacingTransactionTypeFilter,
                                currency
                            )

                            builtMessage.invoke(this)
                        }
                    }
                } else {
                    m.interactivityManager.disabledButton(
                        ButtonStyle.PRIMARY,
                        builder = {
                            emoji = Emoji.fromCustom(
                                Constants.RIGHT_EMOJI.name!!,
                                Constants.RIGHT_EMOJI.id!!.value.toLong(),
                                Constants.RIGHT_EMOJI.animated.discordBoolean
                            )
                        }
                    )
                }
            )

            actionRow(
                m.interactivityManager.stringSelectMenuForUser(
                    author,
                    builder = {
                        val transactionTypes = TransactionType.entries
                        this.maxValues = transactionTypes.size

                        for (transactionType in transactionTypes) {
                            option(
                                transactionType.title,
                                transactionType.name,
                                transactionType.description,
                                default = transactionType in transactionTypeFilter
                            )
                        }
                    }
                ) { context, values ->
                    val builtMessage = createMessage(
                        m,
                        author,
                        playerUniqueId,
                        viewingTransactionsOfPlayerUniqueId,
                        0,
                        values.map { TransactionType.valueOf(it) },
                        currency
                    )

                    context.deferAndEditOriginal {
                        builtMessage.invoke(this)
                    }
                }
            )
        }
    }

    private fun InlineEmbed.createTransactionViewEmbed(
        m: PantufaBot,
        viewingTransactionsOfPlayerUniqueId: UUID,
        transactions: List<Transaction>,
        page: Long,
        isSelf: Boolean,
        totalTransactions: Long,
        currency: TransactionCurrency? = null
    ) {
        title = buildString {
            if (isSelf)
                append("Suas Transações ${if (currency == null) "(Sonecas & Pesadelos)" else "(${currency.displayName})"}")
            else if (viewingTransactionsOfPlayerUniqueId.username == null)
                append("O player que você inseriu não existe")
            else
                append("Transações de ${viewingTransactionsOfPlayerUniqueId.username} ${if (currency == null) "(Sonecas & Pesadelos)" else "(${currency.displayName})"}")

            append(" — ")
            append("Página ${page + 1}")
        }
        color = Constants.LORITTA_AQUA.rgb

        description = buildString {
            for (transaction in transactions) {
                val currency = transaction.currency
                val formattedAmount = currency.format(transaction.amount)

                append("[<t:${transaction.time / 1000}:d> <t:${transaction.time / 1000}:t> | <t:${transaction.time / 1000}:R>]")
                append(" ")
                when (transaction.type) {
                    TransactionType.PAYMENT -> {
                        val receivedTheSonecas = transaction.receiver == viewingTransactionsOfPlayerUniqueId

                        if (receivedTheSonecas) {
                            append(Emotes.DollarBill)
                            append(" ")
                            append("Recebeu $formattedAmount de `${transaction.payer?.username ?: "Desconhecido"}`")
                        } else {
                            append(Emotes.MoneyWithWings)
                            append(" ")
                            append("Enviou $formattedAmount para `${transaction.receiver?.username ?: "Desconhecido"}`")
                        }
                    }
                    TransactionType.BUY_SHOP_ITEM -> {
                        val isAServerOfficialStore = transaction.receiver == null
                        val isTheActualPlayerTheReceiver = transaction.receiver == viewingTransactionsOfPlayerUniqueId

                        append(Emotes.MoneyBag)
                        append(" ")
                        if (isAServerOfficialStore) {
                            append("Comprou `${transaction.extra}` por $formattedAmount numa loja oficial do servidor")
                        } else {
                            if (isTheActualPlayerTheReceiver) {
                                append("Vendeu `${transaction.extra}` por $formattedAmount em sua loja para `${transaction.payer?.username ?: "Desconhecido"}`")
                            } else {
                                append("Comprou `${transaction.extra}` por $formattedAmount na loja de `${transaction.receiver?.username ?: "Desconhecido"}`")
                            }
                        }
                    }
                    TransactionType.SELL_SHOP_ITEM -> {
                        val isAServerOfficialStore = transaction.payer == null
                        val isTheActualPlayerThePayer = transaction.payer == viewingTransactionsOfPlayerUniqueId

                        append(Emotes.CreditCard)
                        append(" ")
                        if (isAServerOfficialStore) {
                            append("Vendeu `${transaction.extra}` por $formattedAmount numa loja oficial do servidor")
                        } else {
                            if (isTheActualPlayerThePayer) {
                                append("Comprou `${transaction.extra}` por $formattedAmount em sua loja de `${transaction.receiver?.username ?: "Desconhecido"}`")
                            } else {
                                append("Vendeu `${transaction.extra}` por $formattedAmount na loja de `${transaction.payer?.username ?: "Desconhecido"}`")
                            }
                        }
                    }
                    TransactionType.VOTE_REWARDS -> {
                        append(Emotes.Envelope)
                        append(" ")
                        append("Recebeu $formattedAmount por votar no servidor")
                    }
                    TransactionType.BETTING -> {
                        val winnedSonecas = transaction.receiver != null

                        if (winnedSonecas) {
                            append(Emotes.Tickets)
                            append(" ")
                            append("Ganhou $formattedAmount ${transaction.extra}")
                        } else {
                            append(Emotes.Tickets)
                            append(" ")
                            append("Gastou $formattedAmount apostando ${transaction.extra}")
                        }
                    }
                    TransactionType.EVENTS -> {
                        append(Emotes.Dart)
                        append(" ")
                        append("Ganhou $formattedAmount jogando no evento `${transaction.extra ?: "Desconhecido"}`")
                    }
                    TransactionType.SECRET_BOXES -> {
                        append(Emotes.Gift)
                        append(" ")
                        append("Recebeu $formattedAmount em uma caixa secreta")
                    }
                    TransactionType.LSX -> {
                        // If the receiver is null, it means that the transaction was sent from Loritta to SparklyPower
                        // And if the receiver is not null, it means that the transaction was sent from SparklyPower to Loritta
                        val sentFromLoritta = transaction.receiver != null

                        val asSonhos = currency.format(transaction.amount / 2.0, "sonhos")

                        // This command wouldn't work if the extra (user ID) is null, so we can safely assume it's not null
                        val userInfo = m.jda.retrieveUserById(transaction.extra!!).complete()

                        val output = if (sentFromLoritta) {
                            "Transferiu $asSonhos da Loritta pela conta `${userInfo.name} (${transaction.extra})` para o SparklyPower ($formattedAmount)"
                        } else {
                            "Transferiu $formattedAmount do SparklyPower para a Loritta pela conta `${userInfo.name} (${transaction.extra})` ($asSonhos)"
                        }

                        append(Emotes.DollarBill)
                        append(" ")
                        append(output)
                    }
                    TransactionType.UNSPECIFIED -> {
                        val isTheActualPlayerTheReceiver = transaction.receiver == viewingTransactionsOfPlayerUniqueId

                        append(Emotes.PantufaOverThinking)
                        append(" ")

                        if (isTheActualPlayerTheReceiver) {
                            append("Recebeu $formattedAmount ao ${transaction.extra}")
                        } else {
                            append("Enviou $formattedAmount ao ${transaction.extra}")
                        }
                    }

                    TransactionType.PESADELOS_BUNDLE_PURCHASE -> {
                        append(Emotes.Pesadelos)
                        append(" ")
                        append("Comprou $formattedAmount pesadelos na loja do SparklyPower")
                    }
                }
                append("\n")
            }
        }

        footer {
            name = "Quantidade de Transações: $totalTransactions"
        }
    }

    private fun createNoMatchingTransactionsEmbed(
        isSelf: Boolean,
        playerUniqueId: UUID,
        currency: TransactionCurrency? = null
    ): InlineEmbed.() -> (Unit) = {
        title = buildString{
            if (isSelf)
                append("Suas Transações ${if (currency == null) "(Sonecas & Pesadelos)" else "(${currency.displayName})"}")
            else if (playerUniqueId.username == null)
                append("O player que você inseriu não existe")
            else
                append("Transações de ${playerUniqueId.username} ${if (currency == null) "(Sonecas & Pesadelos)" else "(${currency.displayName})"}")
        }

        color = Constants.LORITTA_ERROR
        description = Constants.emptyMessages.random()

        image = "https://assets.perfectdreams.media/sparklypower/emotes/pantufa-sob.png"
    }

    private suspend fun createTooManyPagesMessage(
        m: PantufaBot,
        author: Long,
        playerUniqueId: UUID,
        viewingTransactionsOfPlayerUniqueId: UUID,
        totalPages: Long,
        transactionTypeFilter: List<TransactionType>,
        currency: TransactionCurrency? = null
    ): InlineMessage<*>.() -> (Unit) = {
        embed {
            title = "Onde você foi parar?"

            description = buildString {
                appendLine("Você está em um lugar frio, escuro e vazio...\n")
                appendLine("No final do horizonte você consegue ver páginas gigantes, cheias de linhas e números...\n")
                appendLine("Você tenta voar até elas, mas uma força te impede de conseguir se mover...\n")
                appendLine("Você começa a ficar desesperado, como você pode ter parado aqui? Será que foi porque você tentou ir em uma página que não existe...?\n")
                appendLine("E aí, uma luz ilumina uma mão gigante que estava na sua frente durante todo esse tempo.")
            }

            color = Constants.LORITTA_ERROR

            image = "https://cdn.discordapp.com/attachments/513405772911345664/930945637841788958/fon_final_v3_sticker_small.png"
        }

        actionRow(
            m.interactivityManager.buttonForUser(
                author,
                ButtonStyle.PRIMARY,
                "Estou com medo, me deixe em um lugar seguro!",
                builder = {
                    emoji = Emoji.fromCustom(
                        Emotes.PantufaSob.name,
                        Emotes.PantufaSob.id,
                        Emotes.PantufaSob.animated
                    )
                }
            ) {
                val builtMessage = createMessage(
                    m,
                    author,
                    playerUniqueId,
                    viewingTransactionsOfPlayerUniqueId,
                    totalPages - 1,
                    transactionTypeFilter,
                    currency
                )

                it.deferAndEditOriginal {
                    builtMessage.invoke(this)
                }
            }
        )
    }

    private suspend fun retrievePlayerTotalTransactions(
        m: PantufaBot,
        playerUniqueId: UUID,
        transactionTypeFilter: List<TransactionType>,
        currency: TransactionCurrency? = null
    ): Long {
        return m.transactionOnSparklyPowerDatabase {
            buildTransactionQuery(playerUniqueId, transactionTypeFilter, currency)
                .count()
        }
    }

    private suspend fun retrievePlayerTransactions(
        m: PantufaBot,
        playerUniqueId: UUID,
        transactionTypeFilter: List<TransactionType>,
        limit: Int,
        offset: Long,
        currency: TransactionCurrency? = null
    ): List<Transaction> {
        return m.transactionOnSparklyPowerDatabase {
            buildTransactionQuery(playerUniqueId, transactionTypeFilter, currency)
                .orderBy(Transactions.time, SortOrder.DESC)
                .limit(limit, offset)
                .map { Transaction.wrapRow(it) }
        }
    }

    private fun buildTransactionQuery(
        playerUniqueId: UUID,
        transactionTypeFilter: List<TransactionType>,
        currency: TransactionCurrency? = null
    ): Query {
        return transaction(Databases.sparklyPower) {
            if (currency == null) {
                Transactions.selectAll()
                    .where { Transactions.payer eq playerUniqueId or (Transactions.receiver eq playerUniqueId) and (Transactions.type inList transactionTypeFilter) }
            } else {
                Transactions.selectAll()
                    .where { Transactions.payer eq playerUniqueId or (Transactions.receiver eq playerUniqueId) and (Transactions.type inList transactionTypeFilter) and (Transactions.currency eq currency) }
            }
        }
    }
}