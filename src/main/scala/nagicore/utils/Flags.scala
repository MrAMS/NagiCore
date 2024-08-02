package nagicore.utils

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

object Flags{
    def bp(x : String) : BitPat = BitPat(s"b${x}")
    def U(x: String):UInt = s"b$x".U
    def castFlags2Bitpat(x : Iterable[String]) : BitPat = BitPat(s"b${x.reduce(_ ++ _)}")
    def onehotMux[T <: Data](input: UInt, cases: Iterable[(String, T)]) = {
        // check one-hot
        assert(cases.map(x => x._1.count(_ == '1')==1).reduce(_ && _))
        // check no duplicate
        assert(cases.map(x=>x._1).toSet.size == cases.size)
        chisel3.util.Mux1H(cases.map(x => input(x._1.length-1 - findFirstOne(x._1).get) -> x._2))
    }
    /**
      * One-hot Flag Check
      *
      * @param input
      * @param expect
      * @return
      */
    def OHis[T <: Data](input: UInt, expect: String): Bool = {
        // check one-hot
        assert(expect.count(_ == '1')==1)
        input(expect.length-1-findFirstOne(expect).get).asBool
    }
    def CasesMux[T <: Data](input: UInt, cases: Iterable[(String, T)], default: T) : T = {
        // check no duplicate
        assert(cases.map(x=>x._1).toSet.size == cases.size)
        // chisel3.util.Mux1H(cases.map(x => (input === BitPat(s"b${x._1}")) -> x._2))
        // decoder(EspressoMinimizer, input, TruthTable(
        //     cases.map(x => bp(x._1) -> BitPat(x._2.asUInt)),
        //     BitPat(s"b0")
        // ))
        MuxCase(default, cases.map(x => (input === BitPat(s"b${x._1}")) -> x._2).toSeq)
    }
    def ifEqu[T <: Data](input: UInt, target: String, true_res: T, false_res: T) : T = {
        Mux(input === BitPat(s"b${target}"), true_res, false_res)
    }
    /**
      * 译码器, 使用decoder进行真值表优化
      *
      * @param flag_name 控制信号名称
      * @param input 输入信号
      * @param decode_map 译码表, 格式为 (BitPat, Map[控制信号名, 控制信号值])
      * @param default_map 默认译码表, 格式为 Map[控制信号名, 控制信号值]
      * @return 在input输入下，flag_name对应的控制信号值
      */
    def decode_flag(flag_name: String, input: UInt, decode_map: Seq[(BitPat, Map[String, String])], default_map: Map[String, String]) = {
        decoder(EspressoMinimizer, input, TruthTable(
            decode_map.map(x=> x._1 -> BitPat(s"b${x._2.get(flag_name).get}")),
            BitPat(s"b${default_map.get(flag_name).get}")
        ))
    }
    private def findFirstOne(str: String): Option[Int] = {
        str.indexOf("1") match {
            case -1 => None
            case index => Some(index)
        }
    }
}
