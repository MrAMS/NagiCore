package nagicore.unit

import chisel3._
import chisel3.util._

import nagicore.utils.onehot

object BR_TYPE extends ChiselEnum {
    val NEVER =     Value(1.U)
    val EQ =    Value(2.U)
    val NE =    Value(4.U)
    val LT =    Value(8.U)
    val LTU =   Value(16.U)
    val GE =    Value(32.U)
    val GEU =   Value(64.U)
    val ALWAYS =  Value(128.U)
}

class BRU_WITH_ALU_IO(dataBits: Int) extends Bundle{
    val alu_out = Input(UInt(dataBits.W))
    val br_type = Input(BR_TYPE())
    val br_take = Output(Bool())
}

class BRU_WITH_ALU(dataBits: Int) extends Module{
    val io = IO(new BRU_WITH_ALU_IO(dataBits))

    val eq = io.alu_out === 0.U

    import BR_TYPE._

    
    io.br_take := onehot.Mux(io.br_type, Seq(
        NEVER       -> false.B,
        EQ      -> eq,
        NE      -> !eq,
        LT      -> io.alu_out(0),
        LTU     -> io.alu_out(0),
        GE      -> !io.alu_out(0),
        GEU     -> !io.alu_out(0),
        ALWAYS    -> true.B,
    ))

    // io.br_take := MuxLookup(io.br_type, false.B)(Seq(
    //     EQ  -> eq,
    //     NE  -> !eq,
    //     LT  -> io.alu_out(0),
    //     LTU -> io.alu_out(0),
    //     GE  -> !io.alu_out(0),
    //     GEU -> !io.alu_out(0),
    // ))
}

class BRU_SINGLE(dataBits: Int) extends Module{
    val io = IO(new Bundle{
        val a       = Input(UInt(dataBits.W))
        val b       = Input(UInt(dataBits.W))
        val br_type = Input(BR_TYPE())
        val br_take = Output(Bool())
    })
    
    val mins    = (0.U ## io.a) + (1.U ## ~io.b) + 1.U;
    val isLT    = Mux(io.a(dataBits-1)^io.b(dataBits-1), io.a(dataBits-1), mins(dataBits))
    val isLTU   = mins(dataBits)
    val eq      = mins === 0.U

    import BR_TYPE._

    io.br_take := onehot.Mux(io.br_type, Seq(
        NEVER       -> false.B,
        EQ      -> eq,
        NE      -> !eq,
        LT      -> isLT,
        LTU     -> isLTU,
        GE      -> !isLT,
        GEU     -> !isLTU,
        ALWAYS    -> true.B,
    ))
}