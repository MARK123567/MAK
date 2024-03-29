package vsys.blockchain.state

import java.util.concurrent.locks.ReentrantReadWriteLock

import cats._
import vsys.blockchain.BlockchainUpdater
import vsys.blockchain.block.Block
import vsys.blockchain.history.HistoryWriter
import vsys.blockchain.state.BlockchainUpdaterImpl._
import vsys.blockchain.state.StateWriterImpl._
import vsys.blockchain.state.diffs.BlockDiffer
import vsys.blockchain.state.reader.CompositeStateReader.composite
import vsys.blockchain.state.reader.StateReader
import vsys.blockchain.transaction.ValidationError.GenericError
import vsys.blockchain.transaction._
import vsys.settings.FunctionalitySettings
import vsys.utils.ScorexLogging

class BlockchainUpdaterImpl private(persisted: StateWriter with StateReader,
                                    settings: FunctionalitySettings,
                                    minimumInMemoryDiffSize: Int,
                                    historyWriter: HistoryWriter,
                                    val synchronizationToken: ReentrantReadWriteLock) extends BlockchainUpdater with ScorexLogging {

  private val topMemoryDiff = Synchronized(Monoid[BlockDiff].empty)
  private val bottomMemoryDiff = Synchronized(Monoid[BlockDiff].empty)

  private def unsafeDiffByRange(state: StateReader, from: Int, to: Int): BlockDiff = {
    val blocks = measureLog(s"Reading blocks from $from up to $to") {
      Range(from, to).map(historyWriter.blockBytes).par.map(b => Block.parseBytes(b.get).get).seq
    }
    measureLog(s"Building diff from $from up to $to") {
      BlockDiffer.unsafeDiffMany(settings, state, historyWriter.blockAt(from - 1).map(_.timestamp))(blocks)
    }
  }

  private def logHeights(prefix: String): Unit = read { implicit l =>
    log.info(s"$prefix, total blocks: ${historyWriter.height()}, persisted: ${persisted.height}, " +
      s"topMemDiff: ${topMemoryDiff().heightDiff}, bottomMemDiff: ${bottomMemoryDiff().heightDiff}")
  }

  def currentPersistedBlocksState: StateReader = read { implicit l =>
    composite(composite(persisted, () => bottomMemoryDiff()), () => topMemoryDiff())
  }

  private def updatePersistedAndInMemory(): Unit = write { implicit l =>
    logHeights("State rebuild started")
    val persistFrom = persisted.height + 1
    val persistUpTo = historyWriter.height - minimumInMemoryDiffSize + 1

    ranges(persistFrom, persistUpTo, minimumInMemoryDiffSize).foreach { case (head, last) =>
      val diffToBePersisted = unsafeDiffByRange(persisted, head, last)
      persisted.applyBlockDiff(diffToBePersisted)
    }

    bottomMemoryDiff.set(unsafeDiffByRange(persisted, persisted.height + 1, historyWriter.height() + 1))
    topMemoryDiff.set(BlockDiff.empty)
    logHeights("State rebuild finished")
  }

  override def processBlock(block: Block): Either[ValidationError, Unit] = write { implicit l =>
    if (topMemoryDiff().heightDiff >= minimumInMemoryDiffSize) {
      persisted.applyBlockDiff(bottomMemoryDiff())
      bottomMemoryDiff.set(topMemoryDiff())
      topMemoryDiff.set(BlockDiff.empty)
    }
    historyWriter.appendBlock(block)(BlockDiffer.fromBlock(settings, currentPersistedBlocksState, historyWriter.lastBlock.map(_.timestamp))(block)).map { newBlockDiff =>
      topMemoryDiff.set(Monoid.combine(topMemoryDiff(), newBlockDiff))
    }.map(_ => log.trace(s"Block ${block.uniqueId} appended. New height: ${historyWriter.height()}, new score: ${historyWriter.score()}"))
  }

  override def removeAfter(blockId: ByteStr): Either[ValidationError, Seq[Transaction]] = write { implicit l =>
    historyWriter.heightOf(blockId) match {
      case Some(height) if height == historyWriter.height() =>
        log.trace("No rollback necessary")
        Right(Seq.empty)
      case Some(height) =>
        logHeights(s"Rollback to h=$height started")
        val discardedTransactions = Seq.newBuilder[Transaction]
        while (historyWriter.height > height) {
          val transactions = historyWriter.discardBlock()
          log.trace(s"Collecting ${transactions.size} discarded transactions: $transactions")
          discardedTransactions ++= transactions
        }
        if (height < persisted.height) {
          log.info(s"Rollback to h=$height requested. Persisted height=${persisted.height}, will drop state and reapply blockchain now")
          persisted.clear()
          updatePersistedAndInMemory()
        } else {
          if (currentPersistedBlocksState.height != height) {
            val persistedPlusBottomHeight = persisted.height + bottomMemoryDiff().heightDiff
            if (height > persistedPlusBottomHeight) {
              val newTopDiff = unsafeDiffByRange(composite(persisted, () => bottomMemoryDiff()), persistedPlusBottomHeight + 1, height + 1)
              topMemoryDiff.set(newTopDiff)
            } else {
              topMemoryDiff.set(BlockDiff.empty)
              if (height < persistedPlusBottomHeight)
                bottomMemoryDiff.set(unsafeDiffByRange(persisted, persisted.height + 1, height + 1))
            }
          }
        }
        logHeights(s"Rollback to h=$height completed:")
        log.info(s"persisted.height=${persisted.height} history.height=${historyWriter.height}")
        Right(discardedTransactions.result())
      case None =>
        log.warn(s"removeAfter nonexistent block $blockId")
        Left(GenericError(s"Failed to rollback to nonexistent block $blockId"))
    }
  }
}

object BlockchainUpdaterImpl {
  def apply(
               persistedState: StateWriter with StateReader,
               history: HistoryWriter,
               functionalitySettings: FunctionalitySettings,
               minimumInMemoryDiffSize: Int,
               synchronizationToken: ReentrantReadWriteLock): BlockchainUpdaterImpl = {
    val blockchainUpdater =
      new BlockchainUpdaterImpl(persistedState, functionalitySettings, minimumInMemoryDiffSize, history, synchronizationToken)
    blockchainUpdater.logHeights("Constructing BlockchainUpdaterImpl")
    blockchainUpdater.updatePersistedAndInMemory()
    blockchainUpdater
  }

  def ranges(from: Int, to: Int, by: Int): Stream[(Int, Int)] =
    if (from + by < to)
      (from, from + by) #:: ranges(from + by, to, by)
    else
      (from, to) #:: Stream.empty[(Int, Int)]
}