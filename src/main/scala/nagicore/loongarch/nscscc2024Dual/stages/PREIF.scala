package nagicore.loongarch.nscscc2024Dual.stages

import chisel3._
import chisel3.util._
import nagicore.unit.BTB
import nagicore.unit.BTBPredOutIO
import nagicore.loongarch.nscscc2024Dual.{Config, CtrlFlags}
import nagicore.GlobalConfg


class preif2ifBits extends Bundle with Config{
    val pc          = UInt(XLEN.W)
    val pc_refill   = Bool()
    val bpu_out     = new BTBPredOutIO(BTB_ENTRYS, XLEN)
    
    val valid       = Bool()
}

class preif2ifIO extends Bundle{
    val bits = Output(new preif2ifBits)
    val stall = Input(Bool())
}

class PREIF extends Module with Config{
    val io = IO(new Bundle {
        val preif2if = new preif2ifIO
        val ex2preif = Flipped(new ex2preifIO)
    })

    val nxt_pc = Wire(UInt(XLEN.W))
    if(GlobalConfg.SIM){
        dontTouch(nxt_pc)
    }
    val pc = RegEnable(nxt_pc, PC_START, !io.preif2if.stall || io.ex2preif.bpu_fail)
    val pc4 = pc+4.U

    val bpu = Module(new BTB(BTB_ENTRYS, XLEN, XLEN/2))
    bpu.io.pred.in.pc := pc
    bpu.io.update := io.ex2preif.bpu_update

    nxt_pc := Mux(io.ex2preif.bpu_fail, io.ex2preif.br_real_pc,
        Mux(bpu.io.pred.out.taken, bpu.io.pred.out.target,
            pc4
        )
    )
    io.preif2if.bits.pc := pc
    io.preif2if.bits.bpu_out := bpu.io.pred.out
    io.preif2if.bits.pc_refill := RegEnable(io.ex2preif.bpu_fail, !io.preif2if.stall || io.ex2preif.bpu_fail)
    io.preif2if.bits.valid := !reset.asBool
}
