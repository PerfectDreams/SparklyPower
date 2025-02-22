package net.perfectdreams.loritta.morenitta.interactions.commands

import dev.minn.jda.ktx.interactions.commands.*
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.build.*
import net.perfectdreams.loritta.morenitta.interactions.UnleashedContext
import net.perfectdreams.loritta.morenitta.interactions.commands.options.*
import net.perfectdreams.pantufa.PantufaBot
import net.perfectdreams.pantufa.api.commands.PantufaReply
import net.perfectdreams.pantufa.api.commands.exceptions.SilentCommandException
import net.perfectdreams.pantufa.api.commands.exceptions.CommandException
import net.perfectdreams.pantufa.api.commands.styled
import net.perfectdreams.pantufa.interactions.vanilla.economy.*
import net.perfectdreams.pantufa.interactions.vanilla.discord.*
import net.perfectdreams.pantufa.interactions.vanilla.magic.*
import net.perfectdreams.pantufa.interactions.vanilla.minecraft.*
import net.perfectdreams.pantufa.interactions.vanilla.misc.*
import net.perfectdreams.pantufa.interactions.vanilla.moderation.*
import net.perfectdreams.pantufa.interactions.vanilla.utils.*
import net.perfectdreams.pantufa.network.Databases
import net.perfectdreams.pantufa.pantufa
import net.perfectdreams.pantufa.tables.Users
import net.perfectdreams.pantufa.utils.*
import net.perfectdreams.pantufa.utils.extensions.normalize
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.CancellationException
import kotlin.reflect.jvm.jvmName

class UnleashedCommandManager(val m: PantufaBot) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val slashCommands = mutableListOf<SlashCommandDeclaration>()
    private var commandPathToDeclaration = mutableMapOf<String, SlashCommandDeclaration>()

    init {
        logger.info { "Registering Unleashed commands..." }

        // ===[ Discord ]===
        register(PingCommand())

        // ===[ Miscellaneous ]===
        register(GuildsCommand())

        // ===[ Economy ]===
        register(LSXCommand())
        register(PesadelosCommand())
        register(SonecasCommand())
        register(TransactionsCommand())

        // ===[ Magic ]===
        register(ExecuteCommand())

        // ===[ Minecraft ]===
        register(RegisterCommand())
        register(OnlineCommand(m))
        register(VIPInfoCommand())
        register(ChatColorCommand())
        register(SparklyPlayerCommand())
        register(CustomMapCommand(pantufa))
        register(SparklySkinCommand(pantufa))

        // ===[ Moderation ]===
        register(AdminConsoleBungeeCommand())
        register(CommandsLogCommand())
        register(AllCommandsCommand())
        register(SayCommand())

        // ===[ Utils ]===
        register(ChangePassCommand())
        register(NotificarCommand())
        register(VerificarCommand())
        register(TPSCommand())

        // After all commands are registered, we need to update them to path declarations.
        updateCommandPathToDeclarations()
    }

    fun register(declaration: SlashCommandDeclarationWrapper) {
        val builtDeclaration = declaration.command().build()

        if (builtDeclaration.enableLegacyMessageSupport) {
            val executors = mutableListOf<Any>()

            if (builtDeclaration.executor != null)
                executors.add(builtDeclaration.executor)

            for (subcommand in builtDeclaration.subcommands) {
                if (subcommand.executor != null) {
                    executors.add(subcommand.executor)
                }
            }

            for (subcommandGroup in builtDeclaration.subcommandGroups) {
                for (subcommand in subcommandGroup.subcommands) {
                    if (subcommand.executor != null) {
                        executors.add(subcommand.executor)
                    }
                }
            }

            for (executor in executors) {
                if (executor !is LorittaLegacyMessageCommandExecutor) {
                    error("${executor::class.simpleName} does not inherit LorittaLegacyMessageCommandExecutor, but enable legacy message support is enabled!")                }
            }
        }

        logger.info { "Registering command ${declaration::class.jvmName}!" }

        slashCommands += builtDeclaration
    }

    /**
     * Converts a InteraKTions Unleashed [declaration] to JDA
     */
    fun convertDeclarationToJDA(declaration: SlashCommandDeclaration): SlashCommandData {
        return Commands.slash(declaration.name, declaration.description).apply {
            if (declaration.subcommands.isNotEmpty() || declaration.subcommandGroups.isNotEmpty()) {
                if (declaration.executor != null)
                    error("Command ${declaration::class.simpleName} has a root executor, but it also has subcommand/subcommand groups!")

                for (subcommand in declaration.subcommands) {
                    subcommand(subcommand.name, subcommand.description) {
                        val executor = subcommand.executor ?: error("Subcommand does not have a executor!")

                        for (ref in executor.options.registeredOptions) {
                            addOptions(*createOption(ref).toTypedArray())
                        }
                    }
                }

                for (group in declaration.subcommandGroups) {
                    group(group.name, group.description) {
                        for (subcommand in group.subcommands) {
                            subcommand(subcommand.name, subcommand.description) {
                                val executor = subcommand.executor ?: error("Subcommand does not have a executor!")

                                for (ref in executor.options.registeredOptions) {
                                    addOptions(*createOption(ref).toTypedArray())
                                }
                            }
                        }
                    }
                }
            } else {
                val executor = declaration.executor

                if (executor != null) {
                    for (ref in executor.options.registeredOptions) {
                        addOptions(*createOption(ref).toTypedArray())
                    }
                }
            }
        }
    }

    private fun createOption(interaKTionsOption: OptionReference<*>): List<OptionData> {
        when (interaKTionsOption) {
            is DiscordOptionReference -> {
                val description = interaKTionsOption.description

                when (interaKTionsOption) {
                    // First primitives then entities.
                    is LongDiscordOptionReference -> {
                        return listOf(
                            Option<Long>(
                                interaKTionsOption.name,
                                description,
                                interaKTionsOption.required
                            ).apply {
                                if (interaKTionsOption.autocompleteExecutor != null) {
                                    isAutoComplete = true
                                }

                                for (choice in interaKTionsOption.choices) {
                                    when (choice) {
                                        is LongDiscordOptionReference.Choice.RawChoice -> choice(choice.name, choice.value)
                                    }
                                }
                            }
                        )
                    }

                    is StringDiscordOptionReference -> {
                        return listOf(
                            Option<String>(
                                interaKTionsOption.name,
                                description,
                                interaKTionsOption.required
                            ).apply {
                                if (interaKTionsOption.autocompleteExecutor != null) {
                                    isAutoComplete = true
                                }

                                for (choice in interaKTionsOption.choices) {
                                    when (choice) {
                                        is StringDiscordOptionReference.Choice.RawChoice -> choice(choice.name, choice.value)
                                    }
                                }
                            }
                        )
                    }

                    is IntDiscordOptionReference -> {
                        return listOf(
                            Option<Int>(
                                interaKTionsOption.name,
                                description,
                                interaKTionsOption.required
                            ).apply {
                                if (interaKTionsOption.autocompleteExecutor != null) {
                                    isAutoComplete = true
                                }

                                for (choice in interaKTionsOption.choices) {
                                    when (choice) {
                                        is IntDiscordOptionReference.Choice.RawChoice -> choice(choice.name, choice.value)
                                    }
                                }
                            }
                        )
                    }

                    is UserDiscordOptionReference -> {
                        return listOf(
                            Option<User>(
                                interaKTionsOption.name,
                                description,
                                interaKTionsOption.required
                            )
                        )
                    }

                    is ChannelDiscordOptionReference -> {
                        return listOf(
                            Option<GuildChannel>(
                                interaKTionsOption.name,
                                description,
                                interaKTionsOption.required
                            )
                        )
                    }

                    is AttachmentDiscordOptionReference -> {
                        return listOf(
                            Option<Attachment>(
                                interaKTionsOption.name,
                                description,
                                interaKTionsOption.required
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun matches(event: MessageReceivedEvent, rawArguments: List<String>): Boolean {
        val start = System.currentTimeMillis()
        var rootDeclaration: SlashCommandDeclaration? = null
        var slashDeclaration: SlashCommandDeclaration? = null

        var argumentsToBeDropped = 0

        var bestMatch: SlashCommandDeclaration? = null
        var absolutePathSize = 0

        commandDeclarationLoop@for((commandPath, declaration) in commandPathToDeclaration) {
            argumentsToBeDropped = 0

            val absolutePathSplit = commandPath.split(" ")

            if (absolutePathSize > absolutePathSplit.size)
                continue

            for ((index, pathSection) in absolutePathSplit.withIndex()) {
                val rawArgument = rawArguments.getOrNull(index)?.lowercase()?.normalize() ?: continue@commandDeclarationLoop

                if (pathSection.normalize() == rawArgument) {
                    argumentsToBeDropped++
                } else {
                    continue@commandDeclarationLoop
                }
            }

            bestMatch = declaration
            absolutePathSize = argumentsToBeDropped
        }

        if (bestMatch != null) {
            rootDeclaration = bestMatch
            slashDeclaration = bestMatch
            argumentsToBeDropped = absolutePathSize
        }

        if (rootDeclaration == null || slashDeclaration == null)
            return false

        val executor = slashDeclaration.executor ?: error("Missing executor on $slashDeclaration!")

        if (executor !is LorittaLegacyMessageCommandExecutor)
            error("$executor doesn't inherit LorittaLegacyMessageCommandExecutor!")

        var context: UnleashedContext? = null
        val sparklyPower = m.config.sparklyPower

        try {
            val rawArgumentsAfterDrop = rawArguments.drop(argumentsToBeDropped)

            context = LegacyMessageCommandContext(
                m,
                event,
                rawArgumentsAfterDrop,
                slashDeclaration,
                rootDeclaration
            )

            // First let's check if the user is using the command in a non-whitelisted channel.
            if (event.channel.idLong !in sparklyPower.guild.whitelistedChannels &&
                event.guild.idLong != 268353819409252352L && // Ideias Aleatórias
                event.member?.roles?.any { it.idLong in sparklyPower.guild.whitelistedRoles } == false) {
                // If the user is using the command in a non-whitelisted channel, send to him an error message.
                event.channel.sendMessage(
                    PantufaReply(
                        "Você só pode usar meus lindos e incríveis comandos nos canais de comandos!",
                        Constants.ERROR
                    ).build(event.author)
                ).complete()

                return false
            }

            if (event.message.isFromType(ChannelType.TEXT)) {
                logger.info("(${event.message.guild.name} -> ${event.message.channel.name}) ${event.author.name}#${event.author.discriminator} (${event.author.id}): ${event.message.contentDisplay}")
            } else {
                logger.info("(Direct Message) ${event.author.name}#${event.author.discriminator} (${event.author.id}): ${event.message.contentDisplay}")
            }

            if (slashDeclaration.requireMinecraftAccount) {
                val discordAccount = m.getDiscordAccountFromId(event.author.idLong)

                if (discordAccount == null || !discordAccount.isConnected) {
                    context.reply(false) {
                        styled(
                            "Você precisa associar a sua conta do SparklyPower antes de poder usar este comando! Para associar, use `-registrar NomeNoServidor`!",
                            Constants.ERROR
                        )
                    }
                    return true
                } else {
                    context.discordAccount = discordAccount

                    val user = transaction(Databases.sparklyPower) {
                        net.perfectdreams.pantufa.dao.User.find { Users.id eq context.discordAccount!!.minecraftId }.firstOrNull()
                    }

                    if (user == null) {
                        context.reply(false) {
                            styled(
                                "Parece que você tem uma conta associada, mas não existe o seu username salvo no banco de dados! Bug?",
                                Constants.ERROR
                            )
                        }

                        return true
                    }

                    val userBan = context.getUserBanned(discordAccount.minecraftId)

                    if (userBan != null) {
                        context.reply(false) {
                            styled(
                                "Você está banido do SparklyPower!"
                            )
                        }

                        return true
                    }

                    context.sparklyPlayer = user
                }
            }

            val argMap = executor.convertToInteractionsArguments(context, rawArgumentsAfterDrop)

            if (argMap != null) {
                val args = SlashCommandArguments(
                    SlashCommandArgumentsSource.SlashCommandArgumentsMapSource(argMap)
                )

                executor.execute(
                    context,
                    args
                )

                val end = System.currentTimeMillis()

                if (event.message.isFromType(ChannelType.TEXT)) {
                    logger.info("(${event.message.guild.name} -> ${event.message.channel.name}) ${event.author.name}#${event.author.discriminator} (${event.author.id}): ${event.message.contentDisplay} - OK! Processado em ${end - start}ms")
                } else {
                    logger.info("(Direct Message) ${event.author.name}#${event.author.discriminator} (${event.author.id}): ${event.message.contentDisplay} - OK! Processado em ${end - start}ms")
                }

                return true
            }
        } catch (e: Exception) {
            when (e) {
                is CommandException -> {
                    context?.reply(e.ephemeral, e.builder)
                    return true
                }

                is CancellationException -> {
                    logger.error(e) { "RestAction in command ${executor::class.simpleName} has been cancelled" }
                    return true
                }

                is SilentCommandException -> return true
            }

            logger.error(e) { "Exception when executing ${rootDeclaration.name} command!" }

            if (!e.message.isNullOrEmpty()) {
                event.channel.sendMessage(
                    PantufaReply(
                        e.message!!,
                        Emotes.PantufaSob.toString()
                    ).build(event.author)
                ).queue()
            }

            return true
        }

        return false
    }

    fun findDeclarationPath(endDeclaration: SlashCommandDeclaration): List<Any> {
        for (declaration in slashCommands) {
            if (declaration == endDeclaration) {
                return listOf(declaration)
            }

            for (subcommandDeclaration in declaration.subcommands) {
                if (subcommandDeclaration == endDeclaration) {
                    return listOf(declaration, subcommandDeclaration)
                }
            }

            for (group in declaration.subcommandGroups) {
                for (subcommandDeclaration in group.subcommands) {
                    if (subcommandDeclaration == endDeclaration) {
                        return listOf(declaration, group, subcommandDeclaration)
                    }
                }
            }
        }

        error("Declaration path is null for $endDeclaration! This should never happen! Are you trying to find a declaration that isn't registered in InteraKTions Unleashed?")
    }

    private fun updateCommandPathToDeclarations() {
        fun isDeclarationExecutable(declaration: SlashCommandDeclaration) = declaration.executor != null

        val commandPathToDeclarations = mutableMapOf<String, SlashCommandDeclaration>()

        fun putNormalized(key: String, value: SlashCommandDeclaration) {
            commandPathToDeclarations[key.normalize()] = value
        }

        for (declaration in slashCommands.filter { it.enableLegacyMessageSupport }) {
            val rootLabels = listOf(declaration.name) + declaration.alternativeLegacyLabels

            if (isDeclarationExecutable(declaration)) {
                for (rootLabel in rootLabels) {
                    putNormalized(rootLabel, declaration)
                }

                for (absolutePath in declaration.alternativeLegacyAbsoluteCommandPaths) {
                    putNormalized(absolutePath, declaration)
                }
            }

            declaration.subcommands.forEach { subcommand ->
                if (isDeclarationExecutable(subcommand)) {
                    val subcommandLabels = listOf(subcommand.name) + subcommand.alternativeLegacyLabels

                    for (rootLabel in rootLabels) {
                        for (subcommandLabel in subcommandLabels) {
                            putNormalized("$rootLabel $subcommandLabel", subcommand)
                        }
                    }

                    for (absolutePath in subcommand.alternativeLegacyAbsoluteCommandPaths) {
                        putNormalized(absolutePath, subcommand)
                    }
                }
            }

            declaration.subcommandGroups.forEach { group ->
                val subcommandGroupLabels = listOf(group.name) + group.alternativeLegacyLabels

                group.subcommands.forEach { subcommand ->
                    if (isDeclarationExecutable(subcommand)) {
                        val subcommandLabels = listOf(subcommand.name) + subcommand.alternativeLegacyLabels

                        for (rootLabel in rootLabels) {
                            for (subcommandGroupLabel in subcommandGroupLabels) {
                                for (subcommandLabel in subcommandLabels) {
                                    putNormalized("$rootLabel $subcommandGroupLabel $subcommandLabel", subcommand)
                                }
                            }
                        }

                        for (absolutePath in subcommand.alternativeLegacyAbsoluteCommandPaths) {
                            putNormalized(absolutePath.normalize(), subcommand)
                        }
                    }
                }
            }
        }
        this.commandPathToDeclaration = commandPathToDeclarations
    }
}