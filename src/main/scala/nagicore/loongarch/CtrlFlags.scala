package nagicore.loongarch

import chisel3._
import chisel3.util._

object CtrlFlags{
    object nextPc extends ChiselEnum{
        val pc4     = Value((1<<0).U)
        val pc      = Value((1<<1).U)
        val br_pc   = Value((1<<2).U)
    }
    object aluASel extends ChiselEnum{
        val ra      = Value((1<<0).U)
        val pc      = Value((1<<1).U)
        // val csr_val = Value((1<<2).U)
    }
    object aluBSel extends ChiselEnum{
        val rb      = Value((1<<0).U)
        val imm     = Value((1<<1).U)
        val num4    = Value((1<<2).U)
    }
    object brpcAddSel extends ChiselEnum{
        val pc      = Value(false.B)
        val ra_val  = Value(true.B)
    }
    object ldType extends ChiselEnum{
        val x       = Value((1<<0).U)
        val b       = Value((1<<1).U)
        val h       = Value((1<<2).U)
        val w       = Value((1<<3).U)
        val bu      = Value((1<<4).U)
        val hu      = Value((1<<5).U)
    }
    object stType extends ChiselEnum{
        val x       = Value((1<<0).U)
        val b       = Value((1<<1).U)
        val h       = Value((1<<2).U)
        val w       = Value((1<<3).U)
    }
}