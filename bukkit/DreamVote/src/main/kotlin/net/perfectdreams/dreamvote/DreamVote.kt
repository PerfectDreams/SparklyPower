package net.perfectdreams.dreamvote

import com.github.salomonbrys.kotson.fromJson
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toKotlinInstant
import net.perfectdreams.dreamcash.utils.Cash
import net.perfectdreams.dreamcore.DreamCore
import net.perfectdreams.dreamcore.utils.*
import net.perfectdreams.dreamcore.utils.DreamUtils.gson
import net.perfectdreams.dreamcore.utils.scheduler.delayTicks
import net.perfectdreams.dreamcore.utils.preferences.BroadcastType
import net.perfectdreams.dreamcore.utils.preferences.broadcastMessage
import net.perfectdreams.dreamcorreios.DreamCorreios
import net.perfectdreams.dreamvote.commands.VotarCommand
import net.perfectdreams.dreamvote.dao.Vote
import net.perfectdreams.dreamvote.listeners.TagListener
import net.perfectdreams.dreamvote.listeners.VoteListener
import net.perfectdreams.dreamvote.tables.Votes
import net.perfectdreams.dreamvote.tables.VotesUserAvailableNotifications
import net.perfectdreams.dreamvote.utils.VoteAward
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getPlayerExact
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.*
import java.util.*
import kotlin.time.Duration.Companion.seconds

class DreamVote : KotlinPlugin() {
	companion object {
		val INSTANCE get() = Bukkit.getPluginManager().getPlugin("DreamVote") as DreamVote
	}

	var alwaysAwards = mutableListOf<VoteAward>()
	var randomAwards = mutableListOf<VoteAward>()
	var lastVoter: String? = null
	var earnedMoney = 0.0

	override fun softEnable() {
		super.softEnable()

		transaction(Databases.databaseNetwork) {
			SchemaUtils.createMissingTablesAndColumns(
				Votes,
				VotesUserAvailableNotifications
			)
		}

		dataFolder.mkdirs()

		lastVoter = this.config.getString("last-voter")
		earnedMoney = this.config.getDouble("earned-money", 0.0)

		alwaysAwards = gson.fromJson(File(dataFolder, "alwaysawards.json").readText())
		randomAwards = gson.fromJson(File(dataFolder, "randomawards.json").readText())

		registerCommand(VotarCommand(this))
		registerEvents(TagListener(this))
		registerEvents(VoteListener(this))

		launchAsyncThread {
			while (true) {
				val lastVoter = lastVoter
				if (lastVoter != null) {
					Bukkit.getOfflinePlayer(lastVoter).run {
						deposit(15.0, TransactionContext(type = TransactionType.VOTE_REWARDS))
					}
					earnedMoney += 15.0
					if (earnedMoney % 180.0 == 165.0) {
						broadcastMessage(BroadcastType.VOTES_MESSAGE) {
							"§b$lastVoter§d já ganhou §2$earnedMoney sonecas§d apenas mantendo a tag de §c§lÚltimo Votador§d, roube a tag votando! §6/votar"
						}
					}
				}
				delay(5.seconds)
			}
		}

		scheduler().schedule(this) {
			while (true) {
				// One hour
				waitFor(3600 * 20)

				// Only broadcast if there aren't any events happening, this avoids a huge title showing up in the user's screen while they are
				// participating in an event
				while (DreamCore.INSTANCE.dreamEventManager.getRunningEvents().isNotEmpty())
					waitFor(20L)

				broadcastPleaseVoteMessage()
			}
		}
	}

	override fun softDisable() {
		super.softDisable()

		this.config.set("last-voter", lastVoter)
		this.config.set("earned-money", earnedMoney)
		this.saveConfig()
	}

	fun getVoteCount(player: Player) = getVoteCount(player.uniqueId)

	fun getVoteCount(uuid: UUID): Long {
		DreamUtils.assertAsyncThread(true)

		return transaction(Databases.databaseNetwork) {
			Votes.select {
				Votes.player eq uuid
			}.count()
		}
	}

	fun hasVotedThroughTheWeek(player: Player) = hasVotedThroughTheWeek(player.uniqueId)

	fun hasVotedThroughTheWeek(uuid: UUID): Boolean {
		// first we need to check if the user voted in the two websites during monday to friday
		// if he voted, we need to double his reward in cash in the weekend (saturday and sunday)
		DreamUtils.assertAsyncThread(true)

		// let's instantiate the variable that will store the amount of days the player voted
		var votedDays = 0

		// first, we need to instanciate the calendar in the correct time, in the beginning of the week
		val startOfTheWeek = LocalDate.now(TimeUtils.TIME_ZONE).with(DayOfWeek.MONDAY)

		// ok, now we need to list the votes from the player and filter them by the week
		val votes = transaction(Databases.databaseNetwork) {
			Votes.selectAll().where {
				Votes.player eq uuid and (Votes.votedAt greaterEq startOfTheWeek.atStartOfDay()
					.atZone(TimeUtils.TIME_ZONE)
					.toInstant()
					.toEpochMilli()
				)
			}.toList()
		}

		// let's pass through all the votes and check if it's a valid date
		for (vote in votes) {
			val voteDate = LocalDate.ofInstant(
				Instant.ofEpochMilli(vote[Votes.votedAt]),
				ZoneId.of("America/Sao_Paulo")
			) // convert it to timestamp

			val isValidDate = voteDate >= startOfTheWeek && voteDate < startOfTheWeek.plusDays(5)
			val isWeekend = voteDate.dayOfWeek == DayOfWeek.SATURDAY || voteDate.dayOfWeek == DayOfWeek.SUNDAY

			if (isValidDate && !isWeekend) {
				votedDays++
			}
		}

		// finally, check if the user voted all days of the week
		// it's two websites, so the user needs to vote 10 times
		return votedDays == 10
	}

	fun hasVotedToday(player: Player) = hasVotedToday(player.uniqueId)

	fun hasVotedToday(uuid: UUID): Boolean {
		DreamUtils.assertAsyncThread(true)

		val today = Calendar.getInstance()
		today.set(Calendar.HOUR_OF_DAY, 0)
		today.set(Calendar.MINUTE, 0)
		today.set(Calendar.SECOND, 0)
		today.set(Calendar.MILLISECOND, 0)

		return transaction(Databases.databaseNetwork) {
			Votes.select {
				Votes.votedAt greaterEq today.timeInMillis and (Votes.player eq uuid)
			}.count() != 0L
		}
	}

	fun giveVoteAward(username: String, serviceName: String, broadcast: Boolean = true) {
		scheduler().schedule(this, SynchronizationContext.ASYNC) {
			val uniqueId = DreamUtils.retrieveUserUniqueId(username.trim())
			giveVoteAward(uniqueId, serviceName, broadcast)
		}
	}

	fun giveVoteAward(uniqueId: UUID, serviceName: String, broadcast: Boolean = true) {
		scheduler().schedule(this, SynchronizationContext.ASYNC) {
			val voteCount = getVoteCount(uniqueId)
			val giveAwards = mutableListOf<VoteAward>()

			transaction(Databases.databaseNetwork) {
				val vote = Vote.new {
					this.player = uniqueId
					this.votedAt = System.currentTimeMillis()
					this.website = serviceName
				}

				VotesUserAvailableNotifications.insert {
					it[VotesUserAvailableNotifications.userId] = uniqueId
					it[VotesUserAvailableNotifications.serviceName] = serviceName
					it[VotesUserAvailableNotifications.notifyAt] = Instant.now().atZone(ZoneId.of("America/New_York"))
						.plusDays(1)
						.withHour(0)
						.withMinute(0)
						.withSecond(0)
						.withNano(0)
						.toInstant()
					it[VotesUserAvailableNotifications.botVote] = vote.id
					it[VotesUserAvailableNotifications.notified] = false
				}
			}

			giveAwards.addAll(alwaysAwards.filter {
				if (it.hasEqualsVoteCountCondition) {
					return@filter (it.requiredEqualsVoteCount.toLong() == voteCount)
				}
				true
			})

			// Adicionar award aleatório
			giveAwards.add(randomAwards.getRandom())

			val items = mutableListOf<ItemStack>()
			var money = 0.0

			giveAwards.forEach {
				money += it.money.toDouble()
				items.addAll(it.items)
			}

			val dayOfWeek = Calendar.getInstance().apply {
				timeInMillis = System.currentTimeMillis()
			}.get(Calendar.DAY_OF_WEEK)

			val hasVotedThroughTheWeek = hasVotedThroughTheWeek(uniqueId) && dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

			switchContext(SynchronizationContext.SYNC)
			earnedMoney = 0.0

			val player = Bukkit.getPlayer(uniqueId)

			DreamCorreios.getInstance().addItem(uniqueId, *items.toTypedArray())
			val offlinePlayer = Bukkit.getOfflinePlayer(uniqueId)

			// Depositar a grana
			if (money != 0.0)
				offlinePlayer.deposit(money, TransactionContext(type = TransactionType.VOTE_REWARDS))

			if (broadcast) {
				val lastVoter = offlinePlayer.name
				this@DreamVote.lastVoter = lastVoter

				if (lastVoter != null) {
					val prizes = "§9" + giveAwards.filter { !it.hidden }.joinToString("§e, §9", transform = { it.name })

					// Como o player pode estar (ou não!) online, nós iremos pegar o displayName apenas caso o player esteja online
					val player = getPlayerExact(lastVoter)
					val playerName = player?.displayName ?: lastVoter

					broadcastMessage(BroadcastType.VOTES_MESSAGE) {
						if (hasVotedThroughTheWeek) {
							"§6➠ §b$playerName §evotou no §4§lSparkly§b§lPower§e e ganhou $prizes§e §c§l(VOTOU DURANTE A SEMANA INTEIRA! +4 PESADELOS)§e! Vote você também! §6/votar"
						} else {
							"§6➠ §b$playerName §evotou no §4§lSparkly§b§lPower§e no §3$serviceName§e e ganhou $prizes§e! Vote você também! §6/votar"
						}
					}

					player?.sendTitle("§aParabéns!", "§eVocê ganhou $prizes", 10, 60, 10)
				}
			}

			switchContext(SynchronizationContext.ASYNC)

			val cash = if (hasVotedThroughTheWeek) 8L else 4L

			Cash.giveCash(uniqueId, cash, TransactionContext(type = TransactionType.VOTE_REWARDS))

			Webhooks.PANTUFA_INFO?.send("**$lastVoter** votou no $serviceName, agora **$lastVoter** possui ${voteCount + 1} votos. *Prêmios recebidos:* ${giveAwards.joinToString(", ", transform = { "`${it.name}`" })}")
		}
	}

	fun broadcastPleaseVoteMessage() {
		schedule {
			var sentToPlayers = 0

			server.onlinePlayers.forEach {
				switchContext(SynchronizationContext.ASYNC)
				if (hasVotedToday(it)) return@forEach
				switchContext(SynchronizationContext.SYNC)
				sentToPlayers++

				schedule {
					it.sendTitle("§eEntão...", "§aVocê tá afim de uns §3diamantes§a? §f锃", 20, 200, 20)
					waitFor(20 + 100 + 20)

					it.sendTitle("§eSim, §3diamantes§e de graça!", "§aE ainda poder conseguir §b§lVIP§a sem pagar §cnada§a? §f锇", 20, 200, 20)
					waitFor(20 + 100 + 20)

					it.sendTitle("§eEntão §dvote no servidor§e!", "§aSimples e fácil, §6/votar §f锈", 20, 200, 20)
					waitFor(20 + 100 + 20)

					it.sendTitle("§eE lembre-se...", "§aVotar ajuda o servidor e você pode votar todos os dias! §f镾", 20, 200, 20)
					waitFor(20 + 100 + 20)
				}
			}

			Bukkit.getOnlinePlayers().filter { it.hasPermission("sparklypower.soustaff") }.forEach {
				it.sendMessage("§aEnviando mendigação de votos para $sentToPlayers players!")
			}
		}
	}
}
