package nagicore.loongarch.nscscc2024.stages

import chisel3._
import chisel3.util._
import nagicore.utils.Flags
import nagicore.GlobalConfg
import nagicore.loongarch.nscscc2024.{Config, CtrlFlags}

class wb2idIO extends Bundle with Config{
    val wb_data     = Output(UInt(XLEN.W))
    val gpr_id      = Output(UInt(GPR_LEN.W))
    val bypass_rc   = Output(UInt(GPR_LEN.W))
    val bypass_val  = Output(UInt(XLEN.W))
}

class WB extends Module with Config{
    val io = IO(new Bundle{
        val mem2wb      = Flipped(new mem2wbIO)
        val wb2id       = new wb2idIO
        val stall_all   = Input(Bool())
    })

    io.mem2wb.stall := io.stall_all

    // pipeline registers
    val preg = RegEnable(io.mem2wb.bits, true.B)

    val wb_data = Mux(Flags.OHis(preg.ld_type, CtrlFlags.ldType.x), preg.alu_out, preg.rdata)
    io.wb2id.gpr_id := Mux(preg.valid, preg.rc, 0.U)
    io.wb2id.wb_data := wb_data

    io.wb2id.bypass_rc := io.wb2id.gpr_id
    io.wb2id.bypass_val := io.wb2id.wb_data

    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_UPDATE_PC
        val dpic_update_pc = Module(new DPIC_UPDATE_PC(XLEN))
        dpic_update_pc.io.clk := clock
        dpic_update_pc.io.rst := reset
        dpic_update_pc.io.pc := preg.pc
        dpic_update_pc.io.wen := preg.valid

        import nagicore.unit.DPIC_UPDATE_GPR
        val dpic_update_gpr = Module(new DPIC_UPDATE_GPR(XLEN, GPR_NUM))
        dpic_update_gpr.io.clk := clock
        dpic_update_gpr.io.rst := reset
        dpic_update_gpr.io.id := io.wb2id.gpr_id
        dpic_update_gpr.io.wdata := io.wb2id.wb_data

        import nagicore.unit.DPIC_TRACE_MEM
        val dpic_trace_mem_r = Module(new DPIC_TRACE_MEM(XLEN, XLEN))
        dpic_trace_mem_r.io.clk := clock
        dpic_trace_mem_r.io.rst := reset
        dpic_trace_mem_r.io.valid := preg.valid && preg.ld_type =/= Flags.bp(CtrlFlags.ldType.x)
        dpic_trace_mem_r.io.addr := preg.alu_out
        dpic_trace_mem_r.io.size := Flags.onehotMux(preg.ld_type, Seq(
            CtrlFlags.ldType.x  -> 0.U,
            CtrlFlags.ldType.b  -> 0.U,
            CtrlFlags.ldType.bu -> 0.U,
            CtrlFlags.ldType.h  -> 1.U,
            CtrlFlags.ldType.hu -> 1.U,
            CtrlFlags.ldType.w  -> 2.U,
        ))
        dpic_trace_mem_r.io.data := preg.rdata
        dpic_trace_mem_r.io.wmask := 0.U
    }
}