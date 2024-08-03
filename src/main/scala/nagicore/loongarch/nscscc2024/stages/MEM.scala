package nagicore.loongarch.nscscc2024.stages

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4IO
import nagicore.unit.cache.CacheMini
import nagicore.utils.Flags
import nagicore.GlobalConfg
import nagicore.unit.cache.CacheReplaceType
import nagicore.loongarch.nscscc2024.{Config, CtrlFlags}
import nagicore.unit.cache.UnCache

class mem2wbBits extends Bundle with Config{
    val instr       = UInt(XLEN.W)
    val alu_out     = UInt(XLEN.W)
    val rc          = UInt(GPR_LEN.W)
    val ld_type     = CtrlFlags.ldType()
    val rdata       = UInt(XLEN.W)
    val pc          = UInt(XLEN.W)

    val valid       = Bool()
}

class mem2wbIO extends Bundle with Config{
    val bits = Output(new mem2wbBits)
    val stall = Input(Bool())
}


class mem2idIO extends Bundle with Config{
    // effective signal
    val bypass_rc   = Output(UInt(GPR_LEN.W))
    val bypass_val  = Output(UInt(XLEN.W))
}

class MEM extends Module with Config{
    val io = IO(new Bundle {
        val ex2mem = Flipped(new ex2memIO())
        val mem2wb = new mem2wbIO()
        val mem2id = new mem2idIO()
        val dmem = new AXI4IO(XLEN, XLEN)
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
    val preg = RegEnable(io.ex2mem.bits, !dcache.io.out.busy && !io.mem2wb.stall)
    io.ex2mem.stall := dcache.io.out.busy || io.mem2wb.stall

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
    dcache.io.in.req := preg.valid && !nolr && RegNext(!dcache.io.out.busy) && !io.mem2wb.stall

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

    io.mem2wb.bits.alu_out := preg.alu_out
    io.mem2wb.bits.instr := preg.instr
    io.mem2wb.bits.ld_type := preg.ld_type
    io.mem2wb.bits.pc := preg.pc
    io.mem2wb.bits.rc := preg.rc
    io.mem2wb.bits.rdata := rdata_mem
    io.mem2wb.bits.valid := preg.valid && !dcache.io.out.busy

    io.mem2id.bypass_rc := Mux(preg.valid, preg.rc, 0.U)
    io.mem2id.bypass_val := Mux(Flags.OHis(preg.ld_type, CtrlFlags.ldType.x), preg.alu_out, rdata_mem)

    // when(nolr){
    //     io.mem2id.bypass_rc := Mux(preg.valid, preg.rc, 0.U)
    //     io.mem2id.bypass_val := preg.alu_out
    // }.otherwise{
    //     io.mem2id.bypass_rc := Mux(preg.valid && preg.ld_type === Flags.bp(CtrlFlags.ldType.w), preg.rc, 0.U)
    //     io.mem2id.bypass_val := dcache.io.out.rdata
    // }
    
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
        perf_pipe_dcache.io.invalid := !io.mem2wb.bits.valid
        perf_pipe_dcache.io.stall := io.ex2mem.stall
    }
}