package nagicore.loongarch.nscscc2024Dual.stages

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4IO
import nagicore.unit.cache.CacheMini
import nagicore.utils.Flags
import nagicore.GlobalConfg
import nagicore.unit.cache.CacheReplaceType
import nagicore.loongarch.nscscc2024Dual.{Config, CtrlFlags}
import nagicore.unit.cache.UnCache

class mem2idIO extends Bundle with Config{
    // effective signal
    val bypass_rc   = Output(UInt(GPR_LEN.W))
    val bypass_val  = Output(UInt(XLEN.W))
    val bypass_en   = Output(Bool())

    val gpr_wid     = Output(UInt(GPR_LEN.W))
    val gpr_wdata   = Output(UInt(XLEN.W))
    val gpr_wen     = Output(Bool())
}

class MEM extends Module with Config{
    val io = IO(new Bundle {
        val ex2mem = Flipped(new ex2memIO())
        val mem2id = new mem2idIO()
        val dmem = new AXI4IO(XLEN, XLEN)
        val stall_all = Input(Bool())
    })

    class dcachePipeT extends Bundle {
        val instr       = UInt(XLEN.W)
        val alu_out     = UInt(XLEN.W)
        val rc          = UInt(GPR_LEN.W)
        val ld_type     = CtrlFlags.ldType()
        val pc          = UInt(XLEN.W)
        val no_ldst     = Bool()

        val valid       = Bool()
    }
    
    // val dcache = Module(new CacheMini(XLEN, XLEN, 8, 8, 1))
    val dcache = Module(new UnCache(XLEN, XLEN, 8, 1))

    // pipeline registers
    val preg = RegEnable(io.ex2mem.bits, !dcache.io.out.busy && !io.stall_all)
    io.ex2mem.stall := dcache.io.out.busy || io.stall_all

    dcache.io.axi <> io.dmem

    val addr = preg.alu_out

    dcache.io.in.bits.addr := addr
    // dcache.io.in.bits.uncache := addr(31, 28) === "hb".U
    dcache.io.in.bits.we := !Flags.OHis(preg.st_type, CtrlFlags.stType.x)
    dcache.io.in.bits.wdata := Flags.onehotMux(preg.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> Fill(XLEN/8, preg.rb_val(7, 0)),
        CtrlFlags.stType.h  -> Fill(XLEN/16, preg.rb_val(15, 0)),
        CtrlFlags.stType.w  -> preg.rb_val(31, 0),
    ))
    dcache.io.in.bits.size := Flags.onehotMux(preg.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> 0.U,
        CtrlFlags.stType.h  -> 1.U,
        CtrlFlags.stType.w  -> 2.U,
    )) | Flags.onehotMux(preg.ld_type, Seq(
        CtrlFlags.ldType.x  -> 0.U,
        CtrlFlags.ldType.b  -> 0.U,
        CtrlFlags.ldType.bu -> 0.U,
        CtrlFlags.ldType.h  -> 1.U,
        CtrlFlags.ldType.hu -> 1.U,
        CtrlFlags.ldType.w  -> 2.U,
    ))
    dcache.io.in.bits.wmask := Flags.onehotMux(preg.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> ("b1".U<<addr(1, 0)),
        CtrlFlags.stType.h  -> ("b11".U<<(addr(1)##0.U(1.W))),
        CtrlFlags.stType.w  -> "b1111".U,
    ))
    // 不走Cache的指令
    val nolr = Flags.OHis(preg.ld_type, CtrlFlags.ldType.x) && Flags.OHis(preg.st_type, CtrlFlags.stType.x)
    dcache.io.in.req := preg.valid && !nolr && RegNext(!dcache.io.out.busy) && !io.stall_all

    val rdata_raw = dcache.io.out.rdata
    val wordData = if(XLEN == 64) Mux(addr(2), rdata_raw(63, 32), rdata_raw(31, 0))
                    else rdata_raw(31, 0)
    val halfData = Mux(addr(1), wordData(31, 16), wordData(15, 0))
    val byteData = Mux(addr(0), halfData(15, 8), halfData(7, 0))

    val rdata_mem = Flags.onehotMux(preg.ld_type, Seq(
        CtrlFlags.ldType.x  -> (0.U).zext,
        CtrlFlags.ldType.b  -> byteData.asSInt,
        CtrlFlags.ldType.bu -> byteData.zext,
        CtrlFlags.ldType.h  -> halfData.asSInt,
        CtrlFlags.ldType.hu -> halfData.zext,
        CtrlFlags.ldType.w  -> wordData.zext,
    )).asUInt

    val mem_valid = preg.valid && !dcache.io.out.busy

    io.mem2id.bypass_rc := preg.rc
    io.mem2id.bypass_en := mem_valid
    val wb_data = Mux(Flags.OHis(preg.ld_type, CtrlFlags.ldType.x), preg.alu_out, rdata_mem)
    io.mem2id.bypass_val := wb_data

    // when(nolr){
    //     io.mem2id.bypass_rc := Mux(preg.valid, preg.rc, 0.U)
    //     io.mem2id.bypass_val := preg.alu_out
    // }.otherwise{
    //     io.mem2id.bypass_rc := Mux(preg.valid && preg.ld_type === Flags.bp(CtrlFlags.ldType.w), preg.rc, 0.U)
    //     io.mem2id.bypass_val := dcache.io.out.rdata
    // }

    io.mem2id.gpr_wid := preg.rc
    io.mem2id.gpr_wdata := wb_data
    io.mem2id.gpr_wen := mem_valid
    
    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_TRACE_MEM
        val dpic_trace_mem_w = Module(new DPIC_TRACE_MEM(XLEN, XLEN))
        dpic_trace_mem_w.io.clk := clock
        dpic_trace_mem_w.io.rst := reset
        dpic_trace_mem_w.io.valid := dcache.io.in.req && dcache.io.in.bits.wmask.orR
        dpic_trace_mem_w.io.addr := dcache.io.in.bits.addr
        dpic_trace_mem_w.io.size := dcache.io.in.bits.size
        dpic_trace_mem_w.io.data := dcache.io.in.bits.wdata
        dpic_trace_mem_w.io.wmask := dcache.io.in.bits.wmask

        import nagicore.unit.DPIC_PERF_PIPE
        val perf_pipe_dcache = Module(new DPIC_PERF_PIPE())
        perf_pipe_dcache.io.clk := clock
        perf_pipe_dcache.io.rst := reset
        perf_pipe_dcache.io.id := 2.U
        perf_pipe_dcache.io.invalid := !mem_valid
        perf_pipe_dcache.io.stall := io.ex2mem.stall

        import nagicore.unit.DPIC_UPDATE_PC
        val dpic_update_pc = Module(new DPIC_UPDATE_PC(XLEN))
        dpic_update_pc.io.clk := clock
        dpic_update_pc.io.rst := reset
        dpic_update_pc.io.pc := preg.pc
        dpic_update_pc.io.wen := mem_valid

        import nagicore.unit.DPIC_TRACE_MEM
        val dpic_trace_mem_r = Module(new DPIC_TRACE_MEM(XLEN, XLEN))
        dpic_trace_mem_r.io.clk := clock
        dpic_trace_mem_r.io.rst := reset
        dpic_trace_mem_r.io.valid := mem_valid && preg.ld_type =/= Flags.bp(CtrlFlags.ldType.x)
        dpic_trace_mem_r.io.addr := preg.alu_out
        dpic_trace_mem_r.io.size := Flags.onehotMux(preg.ld_type, Seq(
            CtrlFlags.ldType.x  -> 0.U,
            CtrlFlags.ldType.b  -> 0.U,
            CtrlFlags.ldType.bu -> 0.U,
            CtrlFlags.ldType.h  -> 1.U,
            CtrlFlags.ldType.hu -> 1.U,
            CtrlFlags.ldType.w  -> 2.U,
        ))
        dpic_trace_mem_r.io.data := rdata_mem
        dpic_trace_mem_r.io.wmask := 0.U


    }
}