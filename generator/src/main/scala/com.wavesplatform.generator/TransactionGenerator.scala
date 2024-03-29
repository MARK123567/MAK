package com.wavesplatform.generator

import java.security.SecureRandom

import org.slf4j.LoggerFactory
import vsys.account.{Alias, PrivateKeyAccount}
import vsys.blockchain.transaction.TransactionParser.TransactionType
import vsys.blockchain.transaction.assets.exchange.{AssetPair, ExchangeTransaction, Order}
import vsys.blockchain.transaction.assets.{BurnTransaction, IssueTransaction, ReissueTransaction, TransferTransaction}
import vsys.blockchain.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import vsys.blockchain.transaction.{CreateAliasTransaction, PaymentTransaction, Transaction, ValidationError}
import vsys.utils.{LoggerFacade, VSYSSecureRandom}

import scala.concurrent.duration._

object TransactionGenerator {
  private val r = new SecureRandom()
  private val log = LoggerFacade(LoggerFactory.getLogger(getClass))
  private def randomFrom[T](c: Seq[T]): Option[T] = if (c.nonEmpty) Some(c(r.nextInt(c.size))) else None

  private def logOption[T <: Transaction](txE: Either[ValidationError, T])(implicit m: Manifest[T]): Option[T] = {
    txE match {
      case Left(e) =>
        log.warn(s"${m.runtimeClass.getName}: ${e.toString}")
        None
      case Right(tx) => Some(tx)
    }
  }

  private val aliasAlphabet = "-.0123456789@_abcdefghijklmnopqrstuvwxyz"

  private def generateAlias(len: Int): String = VSYSSecureRandom.shuffle(aliasAlphabet).take(len)

  def gen(probabilities: Map[TransactionType.Value, Float],
          accounts: Seq[PrivateKeyAccount],
          n: Int): Seq[Transaction] = {
    val issueTransactionSender = randomFrom(accounts).get
    val tradeAssetIssue = IssueTransaction.create(issueTransactionSender, "TRADE".getBytes,
      "VSYS DEX is the best exchange ever".getBytes, 100000000, 2, reissuable = false,
      100000000L + r.nextInt(100000000), System.currentTimeMillis()).right.get

    val tradeAssetDistribution = {
      tradeAssetIssue +: accounts.map(acc => {
        TransferTransaction.create(Some(tradeAssetIssue.id), issueTransactionSender, acc, 5, System.currentTimeMillis(), None, 100000, Array.fill(r.nextInt(100))(r.nextInt().toByte)).right.get
      })
    }

    val typeGen = new DistributedRandomGenerator(probabilities)

    val generated = (0 until (n * 1.2).toInt).foldLeft((
      Seq.empty[Transaction],
      Seq.empty[IssueTransaction],
      Seq.empty[IssueTransaction],
      Seq.empty[LeaseTransaction],
      Seq.empty[CreateAliasTransaction]
    )) {
      case ((allTxsWithValid, validIssueTxs, reissuableIssueTxs, activeLeaseTransactions, aliases), _) =>
        def moreThatStandartFee = 100000L + r.nextInt(100000)

        val txType = typeGen.getRandom

        def ts = System.currentTimeMillis()

        val tx = txType match {
          case TransactionType.PaymentTransaction =>
            val sender = randomFrom(accounts).get
            val recipient = accounts(new SecureRandom().nextInt(accounts.size))
            Some(PaymentTransaction.create(sender, recipient, r.nextInt(500000), moreThatStandartFee, ts).right.get)
          case TransactionType.IssueTransaction =>
            val sender = randomFrom(accounts).get
            val name = new Array[Byte](10)
            val description = new Array[Byte](10)
            r.nextBytes(name)
            r.nextBytes(description)
            val reissuable = r.nextBoolean()
            val amount = 100000000L + new SecureRandom().nextInt(Int.MaxValue)
            logOption(IssueTransaction.create(sender, name, description, amount, new SecureRandom().nextInt(9).toByte, reissuable, 100000000L + r.nextInt(100000000), ts))
          case TransactionType.TransferTransaction =>
            val useAlias = r.nextBoolean()
            val recipient = if (useAlias && aliases.nonEmpty) randomFrom(aliases).map(_.alias).get else randomFrom(accounts).get.toAddress
            val sendAsset = r.nextBoolean()
            val senderAndAssetOpt = if (sendAsset) {
              val asset = randomFrom(validIssueTxs)
              asset.map(issue => {
                val pk = accounts.find(_ == issue.sender).get
                (pk, Some(issue.id))
              })
            } else Some(randomFrom(accounts).get, None)
            senderAndAssetOpt.flatMap { case (sender, asset) =>
              logOption(TransferTransaction.create(asset, sender, recipient, r.nextInt(500000), ts, None, moreThatStandartFee,
                Array.fill(r.nextInt(100))(r.nextInt().toByte)))
            }
          case TransactionType.ReissueTransaction =>
            val reissuable = r.nextBoolean()
            randomFrom(reissuableIssueTxs).flatMap(assetTx => {
              val sender = accounts.find(_.address == assetTx.sender.address).get
              logOption(ReissueTransaction.create(sender, assetTx.id, new SecureRandom().nextInt(Int.MaxValue), reissuable, moreThatStandartFee, ts))
            })
          case TransactionType.BurnTransaction =>
            randomFrom(validIssueTxs).flatMap(assetTx => {
              val sender = accounts.find(_.address == assetTx.sender.address).get
              logOption(BurnTransaction.create(sender, assetTx.id, new SecureRandom().nextInt(1000), moreThatStandartFee, ts))
            })
          case TransactionType.ExchangeTransaction =>
            val matcher = randomFrom(accounts).get
            val seller = randomFrom(accounts).get
            val pair = AssetPair(None, Some(tradeAssetIssue.id))
            val sellOrder = Order.sell(seller, matcher, pair, 100000000, 1, ts, ts + 30.days.toMillis, moreThatStandartFee * 3)
            val buyer = randomFrom(accounts).get
            val buyOrder = Order.buy(buyer, matcher, pair, 100000000, 1, ts, ts + 1.day.toMillis, moreThatStandartFee * 3)
            logOption(ExchangeTransaction.create(matcher, buyOrder, sellOrder, 100000000, 1, 300000, 300000, moreThatStandartFee * 3, ts))
          case TransactionType.LeaseTransaction =>
            val sender = randomFrom(accounts).get
            val useAlias = r.nextBoolean()
            val recipientOpt = if (useAlias && aliases.nonEmpty) randomFrom(aliases.filter(_.sender != sender)).map(_.alias) else randomFrom(accounts.filter(_ != sender).map(_.toAddress))
            recipientOpt.flatMap(recipient =>
              logOption(LeaseTransaction.create(sender, 1, moreThatStandartFee * 3, 100, ts, recipient)))
          case TransactionType.LeaseCancelTransaction =>
            randomFrom(activeLeaseTransactions).flatMap(lease => {
              val sender = accounts.find(_.address == lease.sender.address).get
              logOption(LeaseCancelTransaction.create(sender, lease.id, moreThatStandartFee * 3, ts))
            })
          case TransactionType.CreateAliasTransaction =>
            val sender = randomFrom(accounts).get
            val aliasString = generateAlias(15)
            logOption(CreateAliasTransaction.create(sender, Alias.buildWithCurrentNetworkByte(aliasString).right.get, 100000, ts))
        }
        (tx.map(tx => allTxsWithValid :+ tx).getOrElse(allTxsWithValid),
          tx match {
            case Some(tx: IssueTransaction) => validIssueTxs :+ tx
            case _ => validIssueTxs
          },
          tx match {
            case Some(tx: IssueTransaction) if tx.reissuable => reissuableIssueTxs :+ tx
            case Some(tx: ReissueTransaction) if !tx.reissuable => reissuableIssueTxs.filter(_.id != tx.id)
            case _ => reissuableIssueTxs
          },
          tx match {
            case Some(tx: LeaseTransaction) => activeLeaseTransactions :+ tx
            case Some(tx: LeaseCancelTransaction) => activeLeaseTransactions.filter(_.id != tx.leaseId)
            case _ => activeLeaseTransactions
          },
          tx match {
            case Some(tx: CreateAliasTransaction) => aliases :+ tx
            case _ => aliases
          }
        )
    }

    tradeAssetDistribution ++ generated._1.take(n - tradeAssetDistribution.size)
  }
}