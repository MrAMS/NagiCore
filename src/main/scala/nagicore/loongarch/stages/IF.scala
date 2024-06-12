package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4IO
import nagicore.loongarch.Config
import nagicore.unit.{CachePiped, DPIC_SRAM}
import nagicore.loongarch.CtrlFlags

class if2idBits extends Bundle with Config{
    val pc          = UInt(XLEN.W)
    val pred_nxt_pc = UInt(XLEN.W)
    val instr       = UInt(XLEN.W)
    
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
        val isram = new AXI4IO(XLEN, XLEN)
    })
    // 2-stages cache
    val icache = Module(new CachePiped(XLEN, XLEN, 2, 512, 4, () => new if2idBits()))
    icache.io.axi <> io.isram

    val pred_nxt_pc = Wire(UInt(XLEN.W))
    val pc = RegEnable(pred_nxt_pc, PC_START, !icache.io.master.front.stall)
    val pc4 = pc+4.U
    pred_nxt_pc := Mux(io.ex2if.br_take, io.ex2if.br_pc,
                    pc4
                )

    /**
     *        -----Cache-----
     * pre -> IMEM1 -> IMEM2
     */

    icache.io.master.front.bits.addr := pc
    icache.io.master.front.bits.uncache := false.B
    icache.io.master.front.bits.wdata := DontCare
    icache.io.master.front.bits.size := 2.U
    icache.io.master.front.bits.wmask := 0.U
    icache.io.master.front.bits.valid := true.B
    icache.io.master.front.bits.pipedata.pc := pc
    icache.io.master.front.bits.pipedata.pred_nxt_pc := pred_nxt_pc
    icache.io.master.front.bits.pipedata.instr := DontCare
    icache.io.master.front.bits.pipedata.valid := DontCare

    icache.io.master.back.stall := io.if2id.stall
    io.if2id.bits <> icache.io.master.back.bits.pipedata_s2
    io.if2id.bits.instr := icache.io.master.back.bits.rdata
    io.if2id.bits.valid := icache.io.master.back.bits.valid
}