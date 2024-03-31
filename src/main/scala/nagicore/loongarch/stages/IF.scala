package nagicore.loongarch.stages

import chisel3._
import chisel3.util._

import nagicore.loongarch.Config
import nagicore.unit.DPIC_SRAM
import nagicore.loongarch.CtrlFlags
import nagicore.utils.onehot

class if2idBits extends Bundle with Config{
    val instr       = UInt(XLEN.W)
    val pc          = UInt(XLEN.W)
    
    val valid       = Bool()
}

class if2idIO extends Bundle{
    val bits = Output(new if2idBits)
    val stall = Input(Bool())
}

class IF extends Module with Config{
    val io = IO(new Bundle {
        val if2id = new if2idIO
        val ex2if = Flipped(new ex2ifIO)
    })
    val next_pc = Wire(UInt(XLEN.W))
    val pc = RegEnable(next_pc, PC_START, !io.if2id.stall)
    val pc4 = pc+4.U
    next_pc := Mux(io.ex2if.br_take, io.ex2if.br_pc,
                    pc4
                )
    
    io.if2id.bits.pc := pc

    val iram = Module(new DPIC_SRAM(XLEN, XLEN))
    iram.io.addr    := next_pc
    iram.io.clk     := clock
    iram.io.rst     := reset
    iram.io.en      := true.B
    iram.io.wmask   := 0.U
    iram.io.wdata   := DontCare
    io.if2id.bits.instr  := iram.io.rdata
    // iram is not valid until the second cycle
    val started = RegNext(RegNext(true.B, false.B), false.B)
    io.if2id.bits.valid  := started
}