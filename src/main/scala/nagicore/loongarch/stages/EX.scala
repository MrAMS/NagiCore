package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.loongarch.Config
import nagicore.utils.onehot
import nagicore.loongarch.CtrlFlags
import nagicore.unit.ALU
import nagicore.unit.BRU_SINGLE

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

    // pipeline registers
    val preg = RegEnable(io.id2ex.bits, !io.ex2mem.stall)

    // max kill 3 cycs
    val kill_nxt = RegEnable(0.U(2.W), !io.ex2mem.stall)
    val killed = kill_nxt =/= 0.U || !preg.valid

    val stall_nxt = RegEnable(0.U(2.W), !io.ex2mem.stall)
    val stalled = stall_nxt =/= 0.U

    io.ex2mem.bits.valid := !killed
    io.id2ex.stall := stalled || io.ex2mem.stall
    
    val bru = Module(new BRU_SINGLE(XLEN))
    bru.io.a := preg.ra_val
    bru.io.b := preg.rb_val
    bru.io.br_type := preg.br_type

    val take : Bool = bru.io.br_take && (!killed)
    io.ex2if.br_take := take

    val is_ld : Bool = !killed && preg.ld_type =/= CtrlFlags.ldType.x

                // when branch take or ld instr, we must kill next 2 instrs from IF, ID stages
    kill_nxt := Mux(take || is_ld, 2.U, 
                    Mux(kill_nxt===0.U, 0.U,
                        kill_nxt-1.U
                    )
                )
                // we must stall 2 cycs until ld instr get value from mem and reach wb stage
    stall_nxt := Mux(is_ld, 2.U,
                        Mux(stall_nxt===0.U, 0.U,
                            stall_nxt-1.U
                        )
                    )

    io.ex2if.br_pc := preg.imm + Mux(preg.brpcAdd_sel.asUInt.asBool, preg.ra_val, preg.pc)

    io.ex2mem.bits.instr := preg.instr

    val alu_a = onehot.Mux(preg.aluA_sel, Seq(
        CtrlFlags.aluASel.ra   -> preg.ra_val,
        CtrlFlags.aluASel.pc   -> preg.pc,
    ))
    val alu_b = onehot.Mux(preg.aluB_sel, Seq(
        CtrlFlags.aluBSel.rb   -> preg.rb_val,
        CtrlFlags.aluBSel.imm  -> preg.imm,
        CtrlFlags.aluBSel.num4 -> 4.U,
    ))
    val alu = Module(new ALU(XLEN))
    alu.io.a := alu_a
    alu.io.b := alu_b
    alu.io.op := preg.alu_op
    io.ex2mem.bits.alu_out := alu.io.out

    io.ex2mem.bits.rb_val := preg.rb_val
    
    io.ex2mem.bits.rc := preg.rc

    io.ex2mem.bits.ld_type := preg.ld_type

    io.ex2mem.bits.st_type := preg.st_type

    io.ex2id.bypass_rc := Mux(killed, 0.U, preg.rc)
    io.ex2id.bypass_val := alu.io.out
}