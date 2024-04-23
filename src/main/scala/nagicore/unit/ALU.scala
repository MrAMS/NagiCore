package nagicore.unit

import chisel3._
import chisel3.util._

import nagicore.utils.Flags

object ALU_OP{
    // val X       = Value(0.U)
    // val ADD     = Value(1.U)
    // val SUB     = Value(2.U)
    // val AND     = Value(4.U)
    // val OR      = Value(8.U)
    // val XOR     = Value(16.U)
    // val LT      = Value(32.U)
    // val LTU     = Value(64.U)
    // val SL      = Value(128.U)
    // val SR      = Value(256.U)
    // val SRA     = Value(512.U)
    // val COPY_A  = Value(1024.U)
    // val COPY_B  = Value(2048.U)
    // val NOR     = Value(4096.U)
    // val MUL     = Value(8192.U)
    // val X, ADD, SUB, AND, OR, XOR, LT, LTU, SL, SR, SRA, COPY_A, COPY_B, NOR,
    // MUL, MULH, MULHU, DIV, DIVU, MOD, MODU = Value
    val X       = "00000"
    val ADD     = "00001"
    val SUB     = "00010"
    val AND     = "00011"
    val OR      = "00100"
    val XOR     = "00101"
    val LT      = "00110"
    val LTU     = "00111"
    val SL      = "01000"
    val SR      = "01001"
    val SRA     = "01010"
    val COPY_A  = "01011"
    val COPY_B  = "01100"
    val NOR     = "01101"
    val MUL     = "01110"
    val MULH    = "01111"
    val MULHU   = "10000"
    val DIV     = "10001"
    val DIVU    = "10010"
    val MOD     = "10011"
    val MODU    = "10100"
    def apply() = UInt(X.length().W)
}

class ALUIO(dataBits: Int) extends Bundle{
    val a       = Input(UInt(dataBits.W))
    val b       = Input(UInt(dataBits.W))
    val op      = Input(ALU_OP())
    val sum     = Output(UInt(dataBits.W))
    val out     = Output(UInt(dataBits.W))
    val valid   = Input(Bool())
    val busy    = Output(Bool())
}

class ALU(dataBits: Int) extends Module {
    val io      = IO(new ALUIO(dataBits))

    val shamt   = io.b(4, 0).asUInt
    val sum     = io.a + io.b;
    val mins    = (0.U ## io.a) + (1.U ## ~io.b) + 1.U;
    val isLT    = Mux(io.a(dataBits-1)^io.b(dataBits-1), io.a(dataBits-1), mins(dataBits))
    val isLTU   = mins(dataBits)
    val isEQ    = mins(dataBits-1, 0) === 0.U
    val or      = io.a | io.b

    io.sum := sum
    import ALU_OP._

    val mulu = Module(new MULU(dataBits))
    mulu.io.a := io.a
    mulu.io.b := io.b
    mulu.io.op := io.op(1, 0)
    mulu.io.vaild := io.valid && Flags.CasesMux(io.op, Seq(
        MUL     -> true.B,
        MULH    -> true.B,
        MULHU   -> true.B,
    ), false.B)

    val divu = Module(new DIVU(dataBits))
    divu.io.a := io.a
    divu.io.b := io.b
    divu.io.signed := io.op(0)
    divu.io.valid := io.valid && Flags.CasesMux(io.op, Seq(
        DIV     -> true.B,
        DIVU    -> true.B,
        MOD     -> true.B,
        MODU    -> true.B,
    ), false.B)

    // must assert when mul or div type comes immediately or can not stall instrs from pre stage
    io.busy := mulu.io.busy || divu.io.busy || mulu.io.vaild || divu.io.valid

    io.out := Flags.CasesMux(io.op, Seq(
        ADD     -> sum,
        SUB     -> mins(dataBits-1, 0),
        SL      -> (io.a << shamt),
        SR      -> (io.a >> shamt),
        SRA     -> (io.a.asSInt >> shamt.asUInt).asUInt,
        AND     -> (io.a & io.b),
        OR      -> or,
        XOR     -> (io.a ^ io.b),
        LT      -> isLT,
        LTU     -> isLTU,
        COPY_A  -> io.a,
        COPY_B  -> io.b,
        NOR     -> (~or),

        MUL     -> mulu.io.out,
        MULH    -> mulu.io.out,
        MULHU   -> mulu.io.out,
        DIV     -> divu.io.quo,
        DIVU    -> divu.io.quo,
        MOD     -> divu.io.rem,
        MODU    -> divu.io.rem,
        // for test
        // MUL     -> (io.a.asSInt * io.b.asSInt)(31, 0).asUInt,
        // MULH    -> (io.a.asSInt * io.b.asSInt)(63, 32).asUInt,
        // MULHU   -> (io.a * io.b)(63, 32),
        // DIV     -> (io.a.asSInt / io.b.asSInt)(31, 0).asUInt,
        // DIVU    -> (io.a / io.b)(31, 0),
        // MOD     -> (io.a.asSInt % io.b.asSInt)(31, 0).asUInt,
        // MODU    -> (io.a % io.b)(31, 0),
    ), 0.U)
}