package nagicore.loongarch.nscscc2024.stages

import chisel3._
import chisel3.util._
import nagicore.utils.Flags
import nagicore.unit.ALU
import nagicore.unit.BRU_SINGLE
import nagicore.GlobalConfg
import nagicore.unit.BTBUpdateIO
import nagicore.loongarch.nscscc2024.{Config, CtrlFlags}
import nagicore.unit.BR_TYPE
import nagicore.unit.BP_TYPE
import nagicore.unit.MULU_IMP
import nagicore.unit.DIVU_IMP

class ex2preifIO extends Bundle with Config{
    val bpu_update  = new BTBUpdateIO(BTB_ENTRYS, XLEN)
    val bpu_fail    = Bool()
    val br_real_pc  = UInt(XLEN.W)
}

class ex2idIO extends Bundle with Config{
    // effective signal
    val bypass_rc   = Output(UInt(GPR_LEN.W))
    val bypass_val  = Output(UInt(XLEN.W))
    val bypass_en   = Output(Bool())
}

class ex2memBits extends Bundle with Config{
    val instr       = UInt(XLEN.W)
    val alu_out     = UInt(XLEN.W)
    val rb_val      = UInt(XLEN.W)
    val rc          = UInt(GPR_LEN.W)
    val ld_type     = CtrlFlags.ldType()
    val st_type     = CtrlFlags.stType()
    val pc          = UInt(XLEN.W)

    val valid       = Bool()
}

class ex2memIO extends Bundle{
    val bits = Output(new ex2memBits)
    val stall = Input(Bool())
}

class EX extends Module with Config{
    val io = IO(new Bundle{
        val ex2preif = new ex2preifIO
        val id2ex = Flipped(new id2exIO)
        val ex2mem = new ex2memIO
        val ex2id = new ex2idIO
    })
    // stall signal from next stage
    val stall_nxt = io.ex2mem.stall
    
    val alu = Module(new ALU(XLEN, MULU_IMP.synthesizer_DSP, DIVU_IMP.none))
    val busy = alu.io.busy

    // accept instrs from pre stage
    val accp_pre = Wire(Bool())
    // pipeline registers
    val preg = RegEnable(io.id2ex.bits, accp_pre)

    /* kill following *valid instrs*, max 3 instrs */
    // val kill_nxt = RegInit(0.U(3.W))

    // 分支预测失败后，等待新的指令
    val wait_refill = RegInit(false.B)
    val refill_ready = !wait_refill || preg.pc_refill

    // stall pre stages in force
    val stall_pre_counter = RegInit(0.U(2.W))

    val valid_instr = refill_ready && preg.valid && !busy && stall_pre_counter === 0.U
    val valid_once_instr = valid_instr && !stall_nxt
    val is_ld : Bool = valid_instr && !Flags.OHis(preg.ld_type, CtrlFlags.ldType.x)
    accp_pre := !(stall_nxt || busy)

    // must stall when ld comes immediately unlike kill
    io.id2ex.stall := stall_pre_counter(1) =/= 0.U || is_ld || busy || stall_nxt
    
    val bru = Module(new BRU_SINGLE(XLEN))
    bru.io.a := preg.ra_val
    bru.io.b := preg.rb_val
    bru.io.br_type := preg.br_type

    val br_pc = preg.imm + Mux(Flags.OHis(preg.brpcAdd_sel, CtrlFlags.brpcAddSel.ra_val), preg.ra_val, preg.pc)

    // valid_instr && bru.io.br_take

    val br_pred_fail = Mux(preg.bpu_out.taken, !bru.io.br_take || preg.bpu_out.target =/= br_pc,
        bru.io.br_take) && valid_once_instr
    
    io.ex2preif.bpu_fail := br_pred_fail
    io.ex2preif.br_real_pc := Mux(bru.io.br_take, br_pc, preg.pc+4.U)

    io.ex2preif.bpu_update.bp_type := RegNext(Mux(Flags.OHis(preg.br_type, BR_TYPE.ALWAYS),
        Flags.U(BP_TYPE.jump), Flags.U(BP_TYPE.cond)
    ))
    io.ex2preif.bpu_update.hit := RegNext(preg.bpu_out.hit)
    io.ex2preif.bpu_update.index := RegNext(preg.bpu_out.index)
    io.ex2preif.bpu_update.pc := RegNext(preg.pc)
    io.ex2preif.bpu_update.target := RegNext(io.ex2preif.br_real_pc)
    io.ex2preif.bpu_update.taken := RegNext(bru.io.br_take)
    io.ex2preif.bpu_update.valid := RegNext(valid_once_instr && !Flags.OHis(preg.br_type, BR_TYPE.NEVER))

    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_PERF_BRU
        import nagicore.unit.BR_TYPE
        val dpic_perf_bru = Module(new DPIC_PERF_BRU)
        dpic_perf_bru.io.clk := clock
        dpic_perf_bru.io.rst := reset
        dpic_perf_bru.io.valid := !Flags.OHis(preg.br_type, BR_TYPE.NEVER) && valid_once_instr
        dpic_perf_bru.io.fail := br_pred_fail
    }

    io.ex2mem.bits.valid := valid_instr

    when(br_pred_fail){
        wait_refill := true.B
    }.elsewhen(preg.pc_refill){
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


    io.ex2mem.bits.instr := preg.instr

    val alu_a = Flags.onehotMux(preg.aluA_sel, Seq(
        CtrlFlags.aluASel.ra   -> preg.ra_val,
        CtrlFlags.aluASel.pc   -> preg.pc,
    ))
    val alu_b = Flags.onehotMux(preg.aluB_sel, Seq(
        CtrlFlags.aluBSel.rb   -> preg.rb_val,
        CtrlFlags.aluBSel.imm  -> preg.imm,
        CtrlFlags.aluBSel.num4 -> 4.U,
    ))

    // must assert for only one cycle
    // alu.io.valid := kill_nxt === 0.U && preg.valid && RegNext(accp_pre)
    alu.io.valid := refill_ready && stall_pre_counter === 0.U && preg.valid && RegNext(accp_pre)
    alu.io.a := alu_a
    alu.io.b := alu_b
    alu.io.op := preg.alu_op
    io.ex2mem.bits.alu_out := alu.io.out

    io.ex2mem.bits.rb_val := preg.rb_val
    
    io.ex2mem.bits.rc := preg.rc

    io.ex2mem.bits.ld_type := preg.ld_type

    io.ex2mem.bits.st_type := preg.st_type

    io.ex2mem.bits.pc := preg.pc

    io.ex2id.bypass_rc := preg.rc
    io.ex2id.bypass_val := alu.io.out
    io.ex2id.bypass_en := valid_instr

    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_PERF_PIPE
        val perf_pipe_ex = Module(new DPIC_PERF_PIPE())
        perf_pipe_ex.io.clk := clock
        perf_pipe_ex.io.rst := reset
        perf_pipe_ex.io.id := 1.U
        perf_pipe_ex.io.invalid := !io.ex2mem.bits.valid
        perf_pipe_ex.io.stall := io.id2ex.stall
    }
}