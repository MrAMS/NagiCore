package nagicore.loongarch.nscscc2024Dual.stages

import chisel3._
import chisel3.util._
import nagicore.unit.GPR
import nagicore.unit.ALU_OP
import nagicore.unit.BR_TYPE
import nagicore.unit.BTBPredOutIO
import nagicore.loongarch.nscscc2024Dual.{Config, CtrlFlags, Decoder}
import nagicore.GlobalConfg


class id2isBits extends Bundle with Config{
    val instr       = UInt(XLEN.W)
    val pc          = UInt(XLEN.W)
    val ra          = UInt(GPR_LEN.W)
    val aluA_sel    = CtrlFlags.aluASel()
    val rb          = UInt(GPR_LEN.W)
    val aluB_sel    = CtrlFlags.aluBSel()
    val alu_op      = ALU_OP()
    val rc          = UInt(GPR_LEN.W)
    val imm         = UInt(XLEN.W)
    val br_type     = BR_TYPE()
    val brpcAdd_sel = CtrlFlags.brpcAddSel()
    val ld_type     = CtrlFlags.ldType()
    val st_type     = CtrlFlags.stType()
    val instr_type  = CtrlFlags.InstrType()
    val pc_refill   = Bool()
    val bpu_out     = new BTBPredOutIO(BTB_ENTRYS, XLEN)

    val valid       = Bool()
}

class id2isIO extends Bundle{
    val bits = Output(new id2isBits)
    val stall = Input(Bool())
}

class ID extends Module with Config{
    val io = IO(new Bundle{
        val if2id = Flipped(new if2idIO)
        val id2is = new id2isIO
    })

    // pipeline registers
    val preg = RegEnable(io.if2id.bits, !io.id2is.stall)

    io.id2is.bits.valid := preg.valid
    io.if2id.stall := io.id2is.stall

    val decoder = Module(new Decoder(XLEN, GPR_LEN))
    decoder.io.instr := preg.instr

    io.id2is.bits.instr    := preg.instr
    io.id2is.bits.pc       := preg.pc

    // val gpr     = Module(new GPR(XLEN, GPR_NUM, 2))
    // gpr.io.wen := io.mem2id.gpr_wen
    // gpr.io.waddr := io.mem2id.gpr_wid
    // gpr.io.wdata := io.mem2id.gpr_wdata

    // if(GlobalConfg.SIM){
    //     import nagicore.unit.DPIC_UPDATE_GPR
    //     val dpic_update_gpr = Module(new DPIC_UPDATE_GPR(XLEN, GPR_NUM))
    //     dpic_update_gpr.io.clk := clock
    //     dpic_update_gpr.io.rst := reset
    //     dpic_update_gpr.io.id := gpr.io.waddr
    //     dpic_update_gpr.io.wen := gpr.io.wen
    //     dpic_update_gpr.io.wdata := gpr.io.wdata
    // }

    // def bypass_unit(rx: UInt, gpr_rdata: UInt):UInt = {
    //     Mux(rx === 0.U, 0.U,
    //         Mux(io.ex2id.bypass_rc === rx && io.ex2id.bypass_en, io.ex2id.bypass_val,
    //             Mux(io.mem2id.bypass_rc === rx && io.mem2id.bypass_en, io.mem2id.bypass_val,
    //                 gpr_rdata
    //             )
    //         )
    //     )
    // }

    // val ra = decoder.io.ra
    // gpr.io.raddr(0) := ra
    // // bypass
    io.id2is.bits.ra := decoder.io.ra
    io.id2is.bits.aluA_sel := decoder.io.aluA_sel

    // val rb = decoder.io.rb
    // gpr.io.raddr(1) := rb
    // // bypass
    // io.id2is.bits.rb_val := bypass_unit(rb, gpr.io.rdata(1))

    io.id2is.bits.rb := decoder.io.rb
    io.id2is.bits.aluB_sel := decoder.io.aluB_sel
    io.id2is.bits.alu_op := decoder.io.alu_op
    io.id2is.bits.rc := decoder.io.rc
    io.id2is.bits.imm := decoder.io.imm
    io.id2is.bits.br_type := decoder.io.br_type
    io.id2is.bits.brpcAdd_sel := decoder.io.brpcAdd_sel
    io.id2is.bits.ld_type := decoder.io.ld_type
    io.id2is.bits.st_type := decoder.io.st_type
    io.id2is.bits.instr_type := decoder.io.instr_type
    io.id2is.bits.bpu_out := preg.bpu_out
    io.id2is.bits.pc_refill := preg.pc_refill
}