package nagicore.loongarch.nscscc2024Dual.stages

import chisel3._
import chisel3.util._
import nagicore.utils.Flags
import nagicore.unit.ALU
import nagicore.unit.BRU_SINGLE
import nagicore.GlobalConfg
import nagicore.unit.BTBUpdateIO
import nagicore.loongarch.nscscc2024Dual.{Config, CtrlFlags}
import nagicore.unit.BR_TYPE
import nagicore.unit.BP_TYPE
import nagicore.unit.MULU_IMP
import nagicore.unit.DIVU_IMP

class ex2preifIO extends Bundle with Config{
    val bpu_update  = new BTBUpdateIO(BTB_ENTRYS, XLEN)
    val bpu_fail    = Bool()
    val br_real_pc  = UInt(XLEN.W)
}

class ex2isIO extends Bundle with Config{
    // effective signal
    val bypass_rc1  = Output(UInt(GPR_LEN.W))
    val bypass_val1 = Output(UInt(XLEN.W))
    val bypass_en1  = Output(Bool())
    
    val bypass_rc2  = Output(UInt(GPR_LEN.W))
    val bypass_val2 = Output(UInt(XLEN.W))
    val bypass_en2  = Output(Bool())

    val clear_is    = Output(Bool())
}

class ex2memBits extends Bundle with Config{
    val instr1      = UInt(XLEN.W)
    val instr2      = UInt(XLEN.W)

    val rc1         = UInt(GPR_LEN.W)
    val alu1_out    = UInt(XLEN.W)
    val rc2         = UInt(GPR_LEN.W)
    val alu2_out    = UInt(XLEN.W)

    val rb1_val     = UInt(XLEN.W)

    val ld_type     = CtrlFlags.ldType()
    val st_type     = CtrlFlags.stType()

    val pc1         = UInt(XLEN.W)
    val pc2         = UInt(XLEN.W)

    val valid1       = Bool()
    val valid2       = Bool()
}

class ex2memIO extends Bundle{
    val bits = Output(new ex2memBits)
    val stall = Input(Bool())
}

class EX extends Module with Config{
    val io = IO(new Bundle{
        val ex2preif = new ex2preifIO
        val is2ex = Flipped(new is2exIO)
        val ex2mem = new ex2memIO
        val ex2is = new ex2isIO
    })
    // stall signal from next stage
    val stall_nxt = io.ex2mem.stall

    val alu1 = Module(new ALU(XLEN, MULU_IMP.synthesizer_DSP, DIVU_IMP.none))
    val alu2 = Module(new ALU(XLEN, MULU_IMP.none, DIVU_IMP.none))
    val busy = alu1.io.busy

    // accept instrs from pre stage
    val ready_nxt = Wire(Bool())
    // pipeline registers
    val preg = RegEnable(io.is2ex.bits, ready_nxt)


    // 分支预测失败后，等待新的指令
    val wait_refill = RegInit(false.B)
    val br_killed1 = wait_refill && !preg.pc_refill1
    val br_killed2 = wait_refill && !preg.pc_refill2

    // stall pre stages in force
    val stall_pre_counter = RegInit(0.U(2.W))

    val valid_instr1 = !br_killed1 && preg.valid1 && !busy && stall_pre_counter === 0.U
    val valid_instr1_once = valid_instr1 && !stall_nxt


    val is_ld : Bool = valid_instr1 && !Flags.OHis(preg.ld_type, CtrlFlags.ldType.x)
    ready_nxt := !(stall_nxt || busy)

    // must stall when ld comes immediately unlike kill
    io.is2ex.stall := stall_pre_counter(1) =/= 0.U || is_ld || busy || stall_nxt

    val bru = Module(new BRU_SINGLE(XLEN))
    bru.io.a := preg.ra1_val
    bru.io.b := preg.rb1_val
    bru.io.br_type := preg.br_type

    val br_pc = preg.imm1 + Mux(Flags.OHis(preg.brpcAdd_sel, CtrlFlags.brpcAddSel.ra_val), preg.ra1_val, preg.pc1)

    // valid_instr && bru.io.br_take

    val br_pred_fail = Mux(preg.bpu_out.taken, !bru.io.br_take || preg.bpu_out.target =/= br_pc,
        bru.io.br_take) && valid_instr1_once

    io.ex2preif.bpu_fail := br_pred_fail
    io.ex2preif.br_real_pc := Mux(bru.io.br_take, br_pc, preg.pc1+4.U)

    io.ex2is.clear_is := br_pred_fail

    io.ex2preif.bpu_update.bp_type := RegNext(Mux(Flags.OHis(preg.br_type, BR_TYPE.ALWAYS),
        Flags.U(BP_TYPE.jump), Flags.U(BP_TYPE.cond)
    ))
    io.ex2preif.bpu_update.hit := RegNext(preg.bpu_out.hit)
    io.ex2preif.bpu_update.index := RegNext(preg.bpu_out.index)
    io.ex2preif.bpu_update.pc := RegNext(preg.pc1)
    io.ex2preif.bpu_update.target := RegNext(io.ex2preif.br_real_pc)
    io.ex2preif.bpu_update.taken := RegNext(bru.io.br_take)
    io.ex2preif.bpu_update.valid := RegNext(valid_instr1 && !Flags.OHis(preg.br_type, BR_TYPE.NEVER))

    val valid_instr2 = !br_killed2 && preg.valid2 && !br_pred_fail && !busy && stall_pre_counter === 0.U
    val valid_instr2_once = valid_instr2 && !stall_nxt

    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_PERF_BRU
        import nagicore.unit.BR_TYPE
        val dpic_perf_bru = Module(new DPIC_PERF_BRU)
        dpic_perf_bru.io.clk := clock
        dpic_perf_bru.io.rst := reset
        dpic_perf_bru.io.valid := !Flags.OHis(preg.br_type, BR_TYPE.NEVER) && valid_instr1
        dpic_perf_bru.io.fail := br_pred_fail
    }

    io.ex2mem.bits.valid1 := valid_instr1
    io.ex2mem.bits.valid2 := valid_instr2

    io.ex2mem.bits.pc1 := preg.pc1
    io.ex2mem.bits.pc2 := preg.pc2

    when(br_pred_fail){
        wait_refill := true.B
    }.elsewhen((preg.pc_refill1 && preg.valid1) || (preg.pc_refill2 && preg.valid2)){
        wait_refill := false.B
    }

    stall_pre_counter := Mux(!stall_nxt,
                            /* 当遇到加载指令时，应该请求上一级阻塞1个周期，并且无视接下来1个周期的指令(EX) */
                            Mux(is_ld, 1.U,
                                Mux(stall_pre_counter===0.U, 0.U,
                                    stall_pre_counter-1.U
                                )
                            ), stall_pre_counter)
    // stall_pre_counter := Mux(!stall_nxt,
    //                     /* 当遇到加载指令时，应该请求上一级阻塞1个周期，并且无视接下来2个周期的指令(EX, DMEM) */
    //                     Mux(is_ld,
    //                         Mux(preg.ld_type === Flags.bp(CtrlFlags.ldType.w),
    //                             1.U,
    //                             2.U
    //                         ),
    //                         Mux(stall_pre_counter===0.U, 0.U,
    //                             stall_pre_counter-1.U
    //                         )
    //                     ), stall_pre_counter)


    io.ex2mem.bits.instr1 := preg.instr1
    io.ex2mem.bits.instr2 := preg.instr2

    val alu1_a = Flags.onehotMux(preg.alu1A_sel, Seq(
        CtrlFlags.aluASel.ra   -> preg.ra1_val,
        CtrlFlags.aluASel.pc   -> preg.pc1,
    ))
    val alu1_b = Flags.onehotMux(preg.alu1B_sel, Seq(
        CtrlFlags.aluBSel.rb   -> preg.rb1_val,
        CtrlFlags.aluBSel.imm  -> preg.imm1,
        CtrlFlags.aluBSel.num4 -> 4.U,
    ))
    val alu2_a = Flags.onehotMux(preg.alu2A_sel, Seq(
        CtrlFlags.aluASel.ra   -> preg.ra2_val,
        CtrlFlags.aluASel.pc   -> preg.pc2,
    ))
    val alu2_b = Flags.onehotMux(preg.alu2B_sel, Seq(
        CtrlFlags.aluBSel.rb   -> preg.rb2_val,
        CtrlFlags.aluBSel.imm  -> preg.imm2,
        CtrlFlags.aluBSel.num4 -> 4.U,
    ))

    // must assert for only one cycle
    // alu.io.valid := kill_nxt === 0.U && preg.valid && RegNext(accp_pre)
    alu1.io.valid := !br_killed1 && stall_pre_counter === 0.U && preg.valid1 && RegNext(ready_nxt)
    alu1.io.a := alu1_a
    alu1.io.b := alu1_b
    alu1.io.op := preg.alu1_op

    alu2.io.valid := preg.valid2
    alu2.io.a := alu2_a
    alu2.io.b := alu2_b
    alu2.io.op := preg.alu2_op

    io.ex2mem.bits.alu1_out := alu1.io.out
    io.ex2mem.bits.rb1_val := preg.rb1_val
    io.ex2mem.bits.rc1 := preg.rc1

    io.ex2mem.bits.alu2_out := alu2.io.out
    io.ex2mem.bits.rc2 := preg.rc2

    io.ex2mem.bits.ld_type := preg.ld_type
    io.ex2mem.bits.st_type := preg.st_type
    io.ex2mem.bits.pc1 := preg.pc1

    io.ex2is.bypass_rc1 := preg.rc1
    io.ex2is.bypass_val1 := alu1.io.out
    io.ex2is.bypass_en1 := valid_instr1

    io.ex2is.bypass_rc2 := preg.rc2
    io.ex2is.bypass_val2 := alu2.io.out
    io.ex2is.bypass_en2 := valid_instr2

    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_PERF_PIPE
        val perf_pipe_ex = Module(new DPIC_PERF_PIPE())
        perf_pipe_ex.io.clk := clock
        perf_pipe_ex.io.rst := reset
        perf_pipe_ex.io.id := 1.U
        perf_pipe_ex.io.invalid := !io.ex2mem.bits.valid1
        perf_pipe_ex.io.stall := io.is2ex.stall
    }
}