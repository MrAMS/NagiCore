package nagicore.utils

import chisel3._
import chisel3.util._

object Flags{
    def castFlag2Bitpat(x : String) : BitPat = BitPat(s"b${x}")
    def castFlags2Bitpat(x : Iterable[String]) : BitPat = BitPat(s"b${x.reduce(_ ++ _)}")
    // def castFlagsBitpatPairs2(x : Iterable[(BitPat, Iterable[String])]) = x.map(x => x._1 -> BitPat(s"b${x._2.reduce(_ ++ _)}"))
    def onehotMux[T <: Data](signal: UInt, cases: Iterable[(String, T)]) = {
        def findFirstOne(str: String): Option[Int] = {
            str.indexOf("1") match {
                case -1 => None
                case index => Some(index)
            }
        }
        // check one-hot
        assert(cases.map(x => x._1.count(_ == '1')==1).reduce(_ && _))
        // check no duplicate
        assert(cases.map(x=>x._1).toSet.size == cases.size)
        chisel3.util.Mux1H(cases.map(x => signal(findFirstOne(x._1).get) -> x._2))
    }
    def MuxCase[T <: Data](signal: UInt, cases: Iterable[(String, T)]) = {
        // check no duplicate
        assert(cases.map(x=>x._1).toSet.size == cases.size)
        chisel3.util.Mux1H(cases.map(x => (signal === BitPat(s"b${x._1}")) -> x._2))
    }
}
