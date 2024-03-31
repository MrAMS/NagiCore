package nagicore.loongarch

import chisel3._
import chisel3.util._


object CtrlFlags{
    // trait FlagsEnum {
    //     def value: String
    // }
    // object aluASel{
    //     sealed trait T extends FlagsEnum
    //     case object ra extends T{
    //         def value = "01"
    //     }
    //     case object pc extends T{
    //         def value = "10"
    //     }
    // }
    object aluASel{
        val ra      = "01"
        val pc      = "10"
        def apply() = UInt(2.W)
    }
    object aluBSel{
        val rb      = "001"
        val imm     = "010"
        val num4    = "100"
        def apply() = UInt(3.W)
    }
    object brpcAddSel{
        val pc      = "01"
        val ra_val  = "10"
        def apply() = UInt(2.W)
    }
    object ldType{
        val x       = "000001"
        val b       = "000010"
        val h       = "000100"
        val w       = "001000"
        val bu      = "010000"
        val hu      = "100000"
        def apply() = UInt(6.W)
    }
    object stType{
        val x       = "0001"
        val b       = "0010"
        val h       = "0100"
        val w       = "1000"
        def apply() = UInt(4.W)
    }
}