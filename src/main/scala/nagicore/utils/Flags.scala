package nagicore.utils

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

object Flags{
    def bp(x : String) : BitPat = BitPat(s"b${x}")
    def castFlags2Bitpat(x : Iterable[String]) : BitPat = BitPat(s"b${x.reduce(_ ++ _)}")
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
        chisel3.util.Mux1H(cases.map(x => signal(x._1.length-1 - findFirstOne(x._1).get) -> x._2))
    }
    def MuxCase[T <: Data](signal: UInt, cases: Iterable[(String, T)]) = {
        // check no duplicate
        assert(cases.map(x=>x._1).toSet.size == cases.size)
        chisel3.util.Mux1H(cases.map(x => (signal === BitPat(s"b${x._1}")) -> x._2))
    }
    /**
      * 译码器, 使用decoder进行真值表优化
      *
      * @param flag_name 控制信号名称
      * @param input 输入信号
      * @param decode_map 译码表, 格式为 (BitPat, Map[控制信号名, 控制信号值])
      * @param default_map 默认译码表, 格式为 Map[控制信号名, 控制信号值]
      * @return
      */
    def decode_flag(flag_name: String, input: UInt, decode_map: Seq[(BitPat, Map[String, String])], default_map: Map[String, String]) = {
        decoder(EspressoMinimizer, input, TruthTable(
            decode_map.map(x=> x._1 -> BitPat(s"b${x._2.get(flag_name).get}")),
            BitPat(s"b${default_map.get(flag_name).get}")
        ))
    }
}
