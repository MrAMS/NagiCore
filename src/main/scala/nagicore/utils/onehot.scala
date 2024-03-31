package nagicore.utils

import chisel3._
import chisel3.util._
import scala.annotation.tailrec
import chisel3.util.experimental.decode._

object onehot {
    def gen(id: Int) : UInt = (1.U<<id)
    
    @tailrec
    private def findFirstOnePos(value: Int, position: Int = 0): Int = {
        if ((value & 1) == 1) position
        else findFirstOnePos(value >>> 1, position + 1)
    }

    def Mux[C <: Data, T <: Data](signal: C, s: Seq[(C, T)], check_onehot_full: Boolean = true) = {
        var s_onehot: Seq[(Bool, T)] = Seq()
        var hav = new Array[Boolean](signal.getWidth)
        for ((enum, data) <- s){
            val pos = findFirstOnePos(enum.litValue.toInt)
            assert((enum.asUInt^(1.U<<pos))===0.U)
            s_onehot = s_onehot :+ (signal.asUInt(pos).asBool -> data)
            assert(hav(pos)==false)
            hav(pos) = true
        }
        if(check_onehot_full)
            for(i <- 0 until hav.length)
                assert(hav(i)==true)
        Mux1H(s_onehot)
    }
    def Decoder[T <: Data](in: UInt, default: BitPat, mapping: Iterable[(BitPat, BitPat)]): UInt = 
        decoder(QMCMinimizer, in, TruthTable(mapping, default))

}