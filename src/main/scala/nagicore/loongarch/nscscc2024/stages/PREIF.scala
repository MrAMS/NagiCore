package nagicore.loongarch.nscscc2024.stages

import chisel3._
import chisel3.util._
import nagicore.unit.BTB
import nagicore.unit.BTBPredOutIO
import nagicore.loongarch.nscscc2024.{Config, CtrlFlags}
import nagicore.GlobalConfg


class preif2ifBits extends Bundle with Config{
    val pc          = UInt(XLEN.W)
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
    val pc = RegEnable(nxt_pc, PC_START, !io.preif2if.stall)
    val pc4 = pc+4.U
    // 当流水线阻塞但分支预测又失败的时候，需要先暂存，等阻塞解除后再修改PC，不能直接覆盖，否则会少一个周期的气泡
    val bpu_fail_when_stall = RegInit(false.B)
    val bpu_fail_pc_when_stall = Reg(UInt(XLEN.W))
    when(io.preif2if.stall && io.ex2preif.bpu_fail){
        bpu_fail_when_stall := true.B
        bpu_fail_pc_when_stall := io.ex2preif.br_real_pc
    }
    when(!io.preif2if.stall){
        bpu_fail_when_stall := false.B
    }
    
    // val bpu_fail = RegEnable(io.ex2preif.bpu_fail || bpu_fail_when_stall, true.B, !io.preif2if.stall)

    val bpu = Module(new BTB(BTB_ENTRYS, XLEN, XLEN/2))
    bpu.io.pred.in.pc := pc
    bpu.io.update := io.ex2preif.bpu_update

    nxt_pc := Mux(bpu_fail_when_stall, bpu_fail_pc_when_stall,
                    Mux(io.ex2preif.bpu_fail, io.ex2preif.br_real_pc,
                        Mux(bpu.io.pred.out.taken, bpu.io.pred.out.target,
                            pc4
                        )
                    )
                )
    io.preif2if.bits.pc := pc
    io.preif2if.bits.bpu_out := bpu.io.pred.out
    io.preif2if.bits.valid := !reset.asBool
}
