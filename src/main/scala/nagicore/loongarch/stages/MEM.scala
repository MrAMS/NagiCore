package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4IO
import nagicore.loongarch.Config
import nagicore.loongarch.CtrlFlags
import nagicore.unit.{CachePiped}
import nagicore.utils.Flags

class mem2wbBits extends Bundle with Config{
    val instr       = UInt(XLEN.W)
    val alu_out     = UInt(XLEN.W)
    val rc          = UInt(GPR_LEN.W)
    val ld_type     = CtrlFlags.ldType()
    val rdata       = UInt(XLEN.W)
    val pc          = UInt(XLEN.W)
    val nocache     = Bool()

    val valid       = Bool()
}

class mem2wbIO extends Bundle with Config{
    val bits = Output(new mem2wbBits)
    val stall = Input(Bool())
}


class mem2idIO extends Bundle with Config{
    // effective signal
    val bypass1_rc   = Output(UInt(GPR_LEN.W))
    val bypass1_val  = Output(UInt(XLEN.W))
    val bypass2_rc   = Output(UInt(GPR_LEN.W))
    val bypass2_val  = Output(UInt(XLEN.W))
}

class MEM extends Module with Config{
    val io = IO(new Bundle {
        val ex2mem = Flipped(new ex2memIO())
        val mem2wb = new mem2wbIO()
        val mem2id = new mem2idIO()
        val dsram = new AXI4IO(XLEN, XLEN)
    })

    /**
     * MEM
     * -----Cache----
     * DMEM1 -> DMEM2
     */

    val dcache = Module(new CachePiped(XLEN, XLEN, DCACHE_WAYS, DCACHE_SETS, DCACHE_LINE, () => new mem2wbBits(), 1))
    dcache.io.axi <> io.dsram
    
    // pipeline registers
    // val preg = RegEnable(io.ex2mem.bits, !dcache.io.master.front.stall)

    val addr = io.ex2mem.bits.alu_out
    dcache.io.master.front.bits.addr := addr
    dcache.io.master.front.bits.uncache := (addr(31, 28) === "hb".U)
    dcache.io.master.front.bits.wdata := Flags.onehotMux(io.ex2mem.bits.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> Fill(XLEN/8, io.ex2mem.bits.rb_val(7, 0)),
        CtrlFlags.stType.h  -> Fill(XLEN/16, io.ex2mem.bits.rb_val(15, 0)),
        CtrlFlags.stType.w  -> io.ex2mem.bits.rb_val(31, 0),
    ))
    dcache.io.master.front.bits.size := Flags.onehotMux(io.ex2mem.bits.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> 0.U,
        CtrlFlags.stType.h  -> 1.U,
        CtrlFlags.stType.w  -> 2.U,
    )) | Flags.onehotMux(io.ex2mem.bits.ld_type, Seq(
        CtrlFlags.ldType.x  -> 0.U,
        CtrlFlags.ldType.b  -> 0.U,
        CtrlFlags.ldType.bu -> 0.U,
        CtrlFlags.ldType.h  -> 1.U,
        CtrlFlags.ldType.hu -> 1.U,
        CtrlFlags.ldType.w  -> 2.U,
    ))
    dcache.io.master.front.bits.wmask := Flags.onehotMux(io.ex2mem.bits.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> ("b1".U<<addr(1, 0)),
        CtrlFlags.stType.h  -> ("b11".U<<(addr(1)##0.U(1.W))),
        CtrlFlags.stType.w  -> "b1111".U,
    ))
    // 不走Cache的指令
    val nocache = (io.ex2mem.bits.ld_type === Flags.bp(CtrlFlags.ldType.x) && io.ex2mem.bits.st_type === Flags.bp(CtrlFlags.stType.x))
    dcache.io.master.front.bits.valid := !nocache && io.ex2mem.bits.valid
    dcache.io.master.front.bits.pipedata.instr := io.ex2mem.bits.instr
    dcache.io.master.front.bits.pipedata.alu_out := io.ex2mem.bits.alu_out
    dcache.io.master.front.bits.pipedata.rc := io.ex2mem.bits.rc
    dcache.io.master.front.bits.pipedata.ld_type := io.ex2mem.bits.ld_type
    dcache.io.master.front.bits.pipedata.pc := io.ex2mem.bits.pc
    dcache.io.master.front.bits.pipedata.valid := io.ex2mem.bits.valid
    dcache.io.master.front.bits.pipedata.rdata := DontCare
    dcache.io.master.front.bits.pipedata.nocache := nocache

    dcache.io.master.back.stall := io.mem2wb.stall

    // assert((io.ex2mem.bits.st_type===Flags.bp(CtrlFlags.stType.h) && addr(0) === 0.U) || io.ex2mem.bits.st_type=/=Flags.bp(CtrlFlags.stType.h))
    // assert((io.ex2mem.bits.st_type===Flags.bp(CtrlFlags.stType.w) && addr(1, 0) === 0.U) || io.ex2mem.bits.st_type=/=Flags.bp(CtrlFlags.stType.w))

    io.ex2mem.stall := dcache.io.master.front.stall

    io.mem2wb.bits <> dcache.io.master.back.bits.pipedata_s2
    io.mem2wb.bits.rdata := dcache.io.master.back.bits.rdata
    io.mem2wb.bits.valid := dcache.io.master.back.bits.pipedata_s2.valid &&
        // 不走Cache的指令不需要关心Cache的valid输出
        Mux(dcache.io.master.back.bits.pipedata_s2.nocache, true.B, dcache.io.master.back.bits.valid)


    io.mem2id.bypass1_rc := Mux(dcache.io.master.back.bits.pipedata_s1.valid, dcache.io.master.back.bits.pipedata_s1.rc, 0.U)
    io.mem2id.bypass1_val := dcache.io.master.back.bits.pipedata_s1.alu_out

    io.mem2id.bypass2_rc := Mux(dcache.io.master.back.bits.pipedata_s2.valid, dcache.io.master.back.bits.pipedata_s2.rc, 0.U)
    io.mem2id.bypass2_val := dcache.io.master.back.bits.pipedata_s2.alu_out
    
    if(DPIC_TRACE){
        import nagicore.unit.DPIC_TRACE_MEM
        val dpic_trace_mem_w = Module(new DPIC_TRACE_MEM(XLEN, XLEN))
        dpic_trace_mem_w.io.clk := clock
        dpic_trace_mem_w.io.rst := reset
        dpic_trace_mem_w.io.valid := dcache.io.master.back.bits.valid && dcache.io.master.back.bits.wmask.orR
        dpic_trace_mem_w.io.addr := dcache.io.master.back.bits.addr
        dpic_trace_mem_w.io.size := dcache.io.master.back.bits.size
        dpic_trace_mem_w.io.data := dcache.io.master.back.bits.wdata
        dpic_trace_mem_w.io.wmask :=dcache.io.master.back.bits.wmask

        import nagicore.unit.DPIC_PERF_PIPE
        val perf_pipe_dcache = Module(new DPIC_PERF_PIPE())
        perf_pipe_dcache.io.clk := clock
        perf_pipe_dcache.io.rst := reset
        perf_pipe_dcache.io.id := 2.U
        perf_pipe_dcache.io.invalid := !io.mem2wb.bits.valid
        perf_pipe_dcache.io.stall := io.ex2mem.stall
    }
}