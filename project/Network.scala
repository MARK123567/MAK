sealed abstract class Network(val suffix: String) {
  val name = s"vsys-core${if (suffix == "mainnet") "" else "-" + suffix}"
  override val toString = suffix
}

object Network {
  def apply(v: Option[String]) = v match {
    case Some(Testnet.suffix) => Testnet
    case Some(Devnet.suffix) => Devnet
    case _ => Mainnet
  }
}

object Mainnet extends Network("mainnet")
object Testnet extends Network("testnet")
object Devnet extends Network("devnet")