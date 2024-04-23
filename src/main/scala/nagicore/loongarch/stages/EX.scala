package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.loongarch.Config
import nagicore.utils.Flags
import nagicore.loongarch.CtrlFlags
import nagicore.unit.ALU
import nagicore.unit.BRU_SINGLE
import org.json4s.scalap.scalasig.Flags

class ex2ifIO extends Bundle with Config{
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
        val ex2if = new ex2ifIO
        val id2ex = Flipped(new id2exIO)
        val ex2mem = new ex2memIO
        val ex2id = new ex2idIO
    })
    // stall signal from next stage
    val stall_nxt = io.ex2mem.stall
    
    val alu = Module(new ALU(XLEN))
    val busy = alu.io.busy

    // accept instrs from pre stage
    val accp_pre = !(stall_nxt || busy)
    // pipeline registers
    val preg = RegEnable(io.id2ex.bits, accp_pre)

    // kill following instrs, max 3 instrs
    val kill_nxt = RegEnable(0.U(2.W), accp_pre)
    // stall pre stages in force
    val stall_pre_counter = RegEnable(0.U(2.W), accp_pre)

    val valid_instr = kill_nxt === 0.U && preg.valid && !busy
    val is_ld : Bool = valid_instr && preg.ld_type =/= Flags.bp(CtrlFlags.ldType.x)

    io.ex2mem.bits.valid := valid_instr
    // must stall when ld comes immediately unlike kill
    io.id2ex.stall := stall_pre_counter =/= 0.U || is_ld || busy || stall_nxt
    
    val bru = Module(new BRU_SINGLE(XLEN))
    bru.io.a := preg.ra_val
    bru.io.b := preg.rb_val
    bru.io.br_type := preg.br_type

    val take : Bool = bru.io.br_take && valid_instr
    io.ex2if.br_take := take


                // when branch take or ld instr, we must kill next 2 instrs from IF, ID stages
    kill_nxt := Mux(take || is_ld, 2.U, 
                    Mux(kill_nxt===0.U, 0.U,
                        kill_nxt-1.U
                    )
                )
                // we must stall 2 cycs until ld instr get value from mem and reach wb stage
    stall_pre_counter := Mux(is_ld, 1.U,
                        Mux(stall_pre_counter===0.U, 0.U,
                            stall_pre_counter-1.U
                        )
                    )

    io.ex2if.br_pc := preg.imm + Mux(preg.brpcAdd_sel === Flags.bp(CtrlFlags.brpcAddSel.ra_val), preg.ra_val, preg.pc)

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
    alu.io.valid := preg.valid && RegNext(accp_pre)
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
}