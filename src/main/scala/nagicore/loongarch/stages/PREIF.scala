package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.loongarch.Config

class preif2ifBits extends Bundle with Config{
    val pc          = UInt(XLEN.W)
    val pred_nxt_pc = UInt(XLEN.W)
    val jump        = Bool()
    
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

    val pred_nxt_pc = Wire(UInt(XLEN.W))
    val pc = RegEnable(pred_nxt_pc, PC_START, !io.preif2if.stall)
    // val pc = RegEnable(pred_nxt_pc, PC_START-4.U, !io.preif2if.stall)
    val pc4 = pc+4.U
    // 当流水线阻塞但分支预测又失败的时候，需要先暂存，等阻塞解除后再修改PC，不能直接覆盖，否则会少一个周期的气泡
    val br_take_when_stall = RegInit(false.B)
    val br_pc_when_stall = RegInit(PC_START)
    when(io.preif2if.stall && io.ex2preif.br_take && !br_take_when_stall){
        br_take_when_stall := true.B
        br_pc_when_stall := io.ex2preif.br_pc
    }.elsewhen(!io.preif2if.stall){
        br_take_when_stall := false.B
    }
    
    val br_take = RegEnable(io.ex2preif.br_take || br_take_when_stall, true.B, !io.preif2if.stall)

    pred_nxt_pc := Mux(br_take_when_stall, br_pc_when_stall,
                        Mux(io.ex2preif.br_take, io.ex2preif.br_pc,
                            pc4
                        )
                    )
    // pc := pred_nxt_pc
    io.preif2if.bits.pc := pc
    io.preif2if.bits.pred_nxt_pc := pred_nxt_pc
    io.preif2if.bits.jump := br_take
    io.preif2if.bits.valid := !reset.asBool

    // io.preif2if.bits.pc := pc
    // io.preif2if.bits.pred_nxt_pc := pred_nxt_pc
    // io.preif2if.bits.jump := RegNext(
    //     ((br_take_when_stall || io.ex2preif.br_take) && !io.preif2if.stall)
    //     || RegNext(false.B, true.B))
    // io.preif2if.bits.valid := RegNext(!reset.asBool)
}
