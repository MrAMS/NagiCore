package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.loongarch.Config
import nagicore.utils.Flags
import nagicore.loongarch.CtrlFlags
import nagicore.unit.ALU
import nagicore.unit.BRU_SINGLE
import org.json4s.scalap.scalasig.Flags
import nagicore.GlobalConfg

class ex2preifIO extends Bundle with Config{
    val br_pc       = Output(UInt(XLEN.W))
    // effective signal
    val br_take     = Output(Bool())
}

class ex2idIO extends Bundle with Config{
    // effective signal
    val bypass_rc   = Output(UInt(GPR_LEN.W))
    val bypass_val  = Output(UInt(XLEN.W))
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
    
    val alu = Module(new ALU(XLEN))
    val busy = alu.io.busy

    // accept instrs from pre stage
    val accp_pre = Wire(Bool())
    // pipeline registers
    val preg = RegEnable(io.id2ex.bits, accp_pre)

    /* kill following *valid instrs*, max 3 instrs */
    val kill_nxt = RegInit(0.U(3.W))
    // stall pre stages in force
    val stall_pre_counter = RegInit(0.U(2.W))

    val valid_instr = kill_nxt === 0.U && preg.valid && !busy && stall_pre_counter === 0.U
    val is_ld : Bool = valid_instr && preg.ld_type =/= Flags.bp(CtrlFlags.ldType.x)
    accp_pre := !(stall_nxt || busy)

    // must stall when ld comes immediately unlike kill
    io.id2ex.stall := stall_pre_counter(1) =/= 0.U || is_ld || busy || stall_nxt
    
    val bru = Module(new BRU_SINGLE(XLEN))
    bru.io.a := preg.ra_val
    bru.io.b := preg.rb_val
    bru.io.br_type := preg.br_type

    val br_pred_fail : Bool = bru.io.br_take && valid_instr
    io.ex2preif.br_take := br_pred_fail

    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_PERF_BRU
        import nagicore.unit.BR_TYPE
        val dpic_perf_bru = Module(new DPIC_PERF_BRU)
        dpic_perf_bru.io.clk := clock
        dpic_perf_bru.io.rst := reset
        dpic_perf_bru.io.valid := preg.br_type =/= Flags.bp(BR_TYPE.NEVER) && valid_instr
        dpic_perf_bru.io.fail := br_pred_fail
    }

    io.ex2mem.bits.valid := valid_instr
                    
    kill_nxt := Mux(!stall_nxt && !busy && (kill_nxt === 0.U || io.id2ex.bits.valid),
                    /* 当分支预测失败时，应该无视接下来4条有效指令(IF,IMEM1,IMEM2,ID) */
                    Mux(br_pred_fail, 3.U,
                        // Mux(is_ld, 3.U,
                            Mux(kill_nxt===0.U, 0.U,
                                kill_nxt-1.U
                            )
                        // )
                    ), kill_nxt)
                // we must stall 2 cycs until ld instr get value from mem and reach wb stage
    stall_pre_counter := Mux(!stall_nxt,
                            /* 当遇到加载指令时，应该请求上一级阻塞2个周期，并且无视接下来3个周期的指令(EX, DMEM1, DMEM2) */
                            Mux(is_ld, 3.U,
                                Mux(stall_pre_counter===0.U, 0.U,
                                    stall_pre_counter-1.U
                                )
                            ), stall_pre_counter)

    io.ex2preif.br_pc := preg.imm + Mux(preg.brpcAdd_sel === Flags.bp(CtrlFlags.brpcAddSel.ra_val), preg.ra_val, preg.pc)

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
    alu.io.valid := kill_nxt === 0.U && preg.valid && RegNext(accp_pre)
    alu.io.a := alu_a
    alu.io.b := alu_b
    alu.io.op := preg.alu_op
    io.ex2mem.bits.alu_out := alu.io.out

    io.ex2mem.bits.rb_val := preg.rb_val
    
    io.ex2mem.bits.rc := preg.rc

    io.ex2mem.bits.ld_type := preg.ld_type

    io.ex2mem.bits.st_type := preg.st_type

    io.ex2mem.bits.pc := preg.pc

    io.ex2id.bypass_rc := Mux(valid_instr, preg.rc, 0.U)
    io.ex2id.bypass_val := alu.io.out

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