package vsys.settings

import vsys.blockchain.state.ByteStr

import scala.concurrent.duration._

case class GenesisTransactionSettings(recipient: String, amount: Long, slotId: Int)

case class GenesisSettings(
  blockTimestamp: Long,
  timestamp: Long,
  initialBalance: Long,
  signature: Option[ByteStr],
  transactions: Seq[GenesisTransactionSettings],
  initialMintTime: Long,
  averageBlockDelay: FiniteDuration)

object GenesisSettings {
  val MAINNET = GenesisSettings(1701572400006000000L, 1701572400006000000L, 38000000000000000L,
    ByteStr.decodeBase58("5xJdBtq6u2us5WmKnF3GiqtVBvmy2chfhgSFgw6NbXq9kqzAutnGBkizjujiwYbfomYxAzoV5nJLvuYgPhJm2pVB").toOption,
    List(
        GenesisTransactionSettings("MKPCjpfpnGgXEh7QyQLpqdxWATqfvnDKpnc",36000000000000000L,-1),
        GenesisTransactionSettings("MKPmjtmtWND9DJkZh3mDTMqTTe3naUhnHbR",1000000000000000L,0),
        GenesisTransactionSettings("MKBTHCUg19EGZnqCXEv7B4TdsA1UNu3MZHq",1000000000000000L,4)),
    1701572400000000000L, 60.seconds)

  val TESTNET = GenesisSettings(1535356447650226656L, 1535356447650226656L, Constants.UnitsInVsys * Constants.TotalVsys,
    ByteStr.decodeBase58("5n4ewwZh9F4MMpSvtdxLCu5MUKnhEyUth2w3zEfpuiX3vwS1STNCdi51fmowJuLT1CfFg1DuodSvxwBZDANvGNej").toOption,
    List(
      GenesisTransactionSettings("ATxpELPa3yhE5h4XELxtPrW9TfXPrmYE7ze", (Constants.UnitsInVsys * Constants.TotalVsys * 0.30).toLong, 0),
      GenesisTransactionSettings("ATtRykARbyJS1RwNsA6Rn1Um3S7FuVSovHK", (Constants.UnitsInVsys * Constants.TotalVsys * 0.20).toLong, 1),
      GenesisTransactionSettings("ATtchuwHVQmNTsRA8ba19juGK9m1gNsUS1V", (Constants.UnitsInVsys * Constants.TotalVsys * 0.15).toLong, 2),
      GenesisTransactionSettings("AU4AoB2WzeXiJvgDhCZmr6B7uDqAzGymG3L", (Constants.UnitsInVsys * Constants.TotalVsys * 0.05).toLong, 3),
      GenesisTransactionSettings("AUBHchRBY4mVNktgCgJdGNcYbwvmzPKgBgN", (Constants.UnitsInVsys * Constants.TotalVsys * 0.06).toLong, 4),
      GenesisTransactionSettings("AU6qstXoazCHDK5dmuCqEnnTWgTqRugHwzm", (Constants.UnitsInVsys * Constants.TotalVsys * 0.06).toLong, 5),
      GenesisTransactionSettings("AU9HYFXuPZPbFVw8vmp7mFmHb7qiaMmgEYE", (Constants.UnitsInVsys * Constants.TotalVsys * 0.06).toLong, 6),
      GenesisTransactionSettings("AUBLPMpHVV74fHQD8D6KosA76nusw4FqRr1", (Constants.UnitsInVsys * Constants.TotalVsys * 0.06).toLong, 7),
      GenesisTransactionSettings("AUBbpPbymsrM8QiXqS3NU7CrD1vy1EyonCa", (Constants.UnitsInVsys * Constants.TotalVsys * 0.04).toLong, 8),
      GenesisTransactionSettings("AU7nJLcT1mThXGTT1KDkoAtfPzc82Sgay1V", (Constants.UnitsInVsys * Constants.TotalVsys * 0.02).toLong, 9)),
    1535356440000000000L, 60.seconds)
}
