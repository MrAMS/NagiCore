package nagicore.loongarch.nscscc2024Dual.stages

import chisel3._
import chisel3.util._
import nagicore.unit.GPR
import nagicore.unit.ALU_OP
import nagicore.unit.BR_TYPE
import nagicore.unit.BTBPredOutIO
import nagicore.loongarch.nscscc2024Dual.{Config, CtrlFlags, Decoder}
import nagicore.GlobalConfg
import nagicore.unit.RingBuff
import nagicore.utils.Flags


class is2exBits extends Bundle with Config{
    val instr1      = UInt(XLEN.W)
    val pc1         = UInt(XLEN.W)
    val ra1_val     = UInt(XLEN.W)
    val alu1A_sel   = CtrlFlags.aluASel()
    val rb1_val     = UInt(XLEN.W)
    val alu1B_sel   = CtrlFlags.aluBSel()
    val alu1_op     = ALU_OP()
    val rc1         = UInt(GPR_LEN.W)
    val imm1        = UInt(XLEN.W)
    val pc_refill1  = Bool()

    val br_type     = BR_TYPE()
    val brpcAdd_sel = CtrlFlags.brpcAddSel()
    val ld_type     = CtrlFlags.ldType()
    val st_type     = CtrlFlags.stType()
    val bpu_out     = new BTBPredOutIO(BTB_ENTRYS, XLEN)

    val valid1       = Bool()

    val instr2      = UInt(XLEN.W)
    val pc2         = UInt(XLEN.W)
    val ra2_val     = UInt(XLEN.W)
    val alu2A_sel   = CtrlFlags.aluASel()
    val rb2_val     = UInt(XLEN.W)
    val alu2B_sel   = CtrlFlags.aluBSel()
    val alu2_op     = ALU_OP()
    val rc2         = UInt(GPR_LEN.W)
    val imm2        = UInt(XLEN.W)
    val pc_refill2  = Bool()

    val valid2       = Bool()
}

class is2exIO extends Bundle{
    val bits = Output(new is2exBits)
    val stall = Input(Bool())
}

class IS extends Module with Config{
    val io = IO(new Bundle{
        val id2is = Flipped(new id2isIO)
        val is2ex = new is2exIO

        val ex2is = Flipped(new ex2isIO)
        val mem2is = Flipped(new mem2isIO)
    })

    val issue_buffer = Module(new RingBuff(()=>new id2isBits, 8, rchannel=2, debug_id=0))

    issue_buffer.io.push := io.id2is.bits.valid
    issue_buffer.io.wdata := io.id2is.bits
    issue_buffer.io.clear := io.ex2is.clear_is

    val is1 = issue_buffer.io.rdatas(0)
    val is2 = issue_buffer.io.rdatas(1)
    val data_hazard = (is1.rc === is2.ra || is1.rc === is2.rb) && is1.rc =/= 0.U
    // 只双发is2是ALU类，且无数据冒险的指令
    val issue_double =
        Flags.is(is2.instr_type, CtrlFlags.InstrType.alu) &&
        issue_buffer.io.rvalids(1) &&
        !data_hazard

    issue_buffer.io.pop := !io.is2ex.stall && !issue_buffer.io.empty
    issue_buffer.io.popN := issue_double

//    io.id2is.stall := io.is2ex.stall
    io.id2is.stall := issue_buffer.io.full

    val gpr     = Module(new GPR(XLEN, GPR_NUM, 4, 2))
    gpr.io.wen(0) := io.mem2is.gpr_wen1 && (!io.mem2is.gpr_wen2 || io.mem2is.gpr_wid2 =/= io.mem2is.gpr_wid1)
    gpr.io.waddr(0) := io.mem2is.gpr_wid1
    gpr.io.wdata(0) := io.mem2is.gpr_wdata1

    gpr.io.wen(1) := io.mem2is.gpr_wen2
    gpr.io.waddr(1) := io.mem2is.gpr_wid2
    gpr.io.wdata(1) := io.mem2is.gpr_wdata2

    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_UPDATE_GPR2
        val dpic_update_gpr = Module(new DPIC_UPDATE_GPR2(XLEN, GPR_NUM))
        dpic_update_gpr.io.clk := clock
        dpic_update_gpr.io.rst := reset

        dpic_update_gpr.io.id1 := gpr.io.waddr(0)
        dpic_update_gpr.io.wen1 := gpr.io.wen(0)
        dpic_update_gpr.io.wdata1 := gpr.io.wdata(0)

        dpic_update_gpr.io.id2 := gpr.io.waddr(1)
        dpic_update_gpr.io.wen2 := gpr.io.wen(1)
        dpic_update_gpr.io.wdata2 := gpr.io.wdata(1)
    }

    def bypass_unit(rx: UInt, gpr_rdata: UInt):UInt = {
        Mux(rx === 0.U, 0.U,
            Mux(io.ex2is.bypass_rc2 === rx && io.ex2is.bypass_en2, io.ex2is.bypass_val2,
                Mux(io.ex2is.bypass_rc1 === rx && io.ex2is.bypass_en1, io.ex2is.bypass_val1,
                    Mux(io.mem2is.bypass_rc2 === rx && io.mem2is.bypass_en2, io.mem2is.bypass_val2,
                        Mux(io.mem2is.bypass_rc1 === rx && io.mem2is.bypass_en1, io.mem2is.bypass_val1,
                            gpr_rdata
                        )
                    )
                )
            )
        )
    }

    gpr.io.raddr(0) := is1.ra
    io.is2ex.bits.ra1_val := bypass_unit(is1.ra, gpr.io.rdata(0))
    io.is2ex.bits.alu1A_sel := is1.aluA_sel
    gpr.io.raddr(1) := is1.rb
    io.is2ex.bits.rb1_val := bypass_unit(is1.rb, gpr.io.rdata(1))
    io.is2ex.bits.alu1B_sel := is1.aluB_sel

    gpr.io.raddr(2) := is2.ra
    io.is2ex.bits.ra2_val := bypass_unit(is2.ra, gpr.io.rdata(2))
    io.is2ex.bits.alu2A_sel := is2.aluA_sel
    gpr.io.raddr(3) := is2.rb
    io.is2ex.bits.rb2_val := bypass_unit(is2.rb, gpr.io.rdata(3))
    io.is2ex.bits.alu2B_sel := is2.aluB_sel

    io.is2ex.bits.instr1 := is1.instr
    io.is2ex.bits.instr2 := is2.instr

    io.is2ex.bits.pc1 := is1.pc
    io.is2ex.bits.pc2 := is2.pc

    io.is2ex.bits.alu1_op := is1.alu_op
    io.is2ex.bits.alu2_op := is2.alu_op

    io.is2ex.bits.rc1 := is1.rc
    io.is2ex.bits.rc2 := is2.rc

    io.is2ex.bits.imm1 := is1.imm
    io.is2ex.bits.imm2 := is2.imm

    io.is2ex.bits.br_type := is1.br_type
    io.is2ex.bits.brpcAdd_sel := is1.brpcAdd_sel
    io.is2ex.bits.ld_type := is1.ld_type
    io.is2ex.bits.st_type := is1.st_type
    io.is2ex.bits.bpu_out := is1.bpu_out

    io.is2ex.bits.pc_refill1 := is1.pc_refill
    io.is2ex.bits.pc_refill2 := is2.pc_refill

    io.is2ex.bits.valid1 := issue_buffer.io.rvalids(0)
    io.is2ex.bits.valid2 := issue_buffer.io.rvalids(1) && issue_double
}