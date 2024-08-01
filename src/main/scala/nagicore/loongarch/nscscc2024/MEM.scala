package nagicore.loongarch.nscscc2024

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4IO
import nagicore.loongarch.CtrlFlags
import nagicore.unit.cache.CacheWT
import nagicore.utils.Flags
import nagicore.GlobalConfg
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

    val dcache = Module(new CacheWT(XLEN, XLEN, DCACHE_WAYS, DCACHE_LINES, DCACHE_WORDS, DCACHE_WBUFF_LEN, ()=> new dcachePipeT, debug_id=1))

    io.ex2mem.stall := dcache.io.cmd.front.stall || io.mem2wb.stall

    dcache.io.axi <> io.dmem


    val ex_out = io.ex2mem.bits
    val addr = ex_out.alu_out

    dcache.io.cmd.front.bits.addr := addr
    dcache.io.cmd.front.bits.uncache := (addr(31, 28) === "hb".U)
    // dcache.io.cmd.front.bits.uncache := true.B
    dcache.io.cmd.front.bits.we := ex_out.st_type =/= Flags.bp(CtrlFlags.stType.x)
    dcache.io.cmd.front.bits.wdata := Flags.onehotMux(ex_out.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> Fill(XLEN/8, ex_out.rb_val(7, 0)),
        CtrlFlags.stType.h  -> Fill(XLEN/16, ex_out.rb_val(15, 0)),
        CtrlFlags.stType.w  -> ex_out.rb_val(31, 0),
    ))
    dcache.io.cmd.front.bits.size := Flags.onehotMux(ex_out.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> 0.U,
        CtrlFlags.stType.h  -> 1.U,
        CtrlFlags.stType.w  -> 2.U,
    )) | Flags.onehotMux(ex_out.ld_type, Seq(
        CtrlFlags.ldType.x  -> 0.U,
        CtrlFlags.ldType.b  -> 0.U,
        CtrlFlags.ldType.bu -> 0.U,
        CtrlFlags.ldType.h  -> 1.U,
        CtrlFlags.ldType.hu -> 1.U,
        CtrlFlags.ldType.w  -> 2.U,
    ))
    dcache.io.cmd.front.bits.wmask := Flags.onehotMux(ex_out.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> ("b1".U<<addr(1, 0)),
        CtrlFlags.stType.h  -> ("b11".U<<(addr(1)##0.U(1.W))),
        CtrlFlags.stType.w  -> "b1111".U,
    ))
    // 不走Cache的指令
    val no_ldst = (ex_out.ld_type === Flags.bp(CtrlFlags.ldType.x) && ex_out.st_type === Flags.bp(CtrlFlags.stType.x))
    dcache.io.cmd.front.bits.valid := !no_ldst && ex_out.valid

    dcache.io.cmd.front.bits.pipedata.alu_out := ex_out.alu_out
    dcache.io.cmd.front.bits.pipedata.instr := ex_out.instr
    dcache.io.cmd.front.bits.pipedata.ld_type := ex_out.ld_type
    dcache.io.cmd.front.bits.pipedata.no_ldst := no_ldst
    dcache.io.cmd.front.bits.pipedata.pc := ex_out.pc
    dcache.io.cmd.front.bits.pipedata.rc := ex_out.rc
    dcache.io.cmd.front.bits.pipedata.valid := ex_out.valid

    dcache.io.cmd.back.stall := io.mem2wb.stall

    io.mem2wb.bits.alu_out := dcache.io.cmd.back.bits.pipedata.alu_out
    io.mem2wb.bits.instr := dcache.io.cmd.back.bits.pipedata.instr
    io.mem2wb.bits.ld_type := dcache.io.cmd.back.bits.pipedata.ld_type
    io.mem2wb.bits.pc := dcache.io.cmd.back.bits.pipedata.pc
    io.mem2wb.bits.rc := dcache.io.cmd.back.bits.pipedata.rc
    io.mem2wb.bits.rdata := dcache.io.cmd.back.bits.rdata
    io.mem2wb.bits.valid := dcache.io.cmd.back.bits.pipedata.valid && 
        // 不走Cache的指令不需要关心Cache的valid输出
        Mux(dcache.io.cmd.back.bits.pipedata.no_ldst, true.B, dcache.io.cmd.back.bits.valid)

    io.mem2id.bypass_rc := Mux(dcache.io.cmd.back.bits.pipedata.valid, dcache.io.cmd.back.bits.pipedata.rc, 0.U)
    io.mem2id.bypass_val := dcache.io.cmd.back.bits.pipedata.alu_out

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
        dpic_trace_mem_w.io.valid := dcache.io.cmd.back.bits.valid && dcache.io.cmd.back.bits.wmask.orR
        dpic_trace_mem_w.io.addr := dcache.io.cmd.back.bits.addr
        dpic_trace_mem_w.io.size := dcache.io.cmd.back.bits.size
        dpic_trace_mem_w.io.data := dcache.io.cmd.back.bits.wdata
        dpic_trace_mem_w.io.wmask := dcache.io.cmd.back.bits.wmask

        import nagicore.unit.DPIC_PERF_PIPE
        val perf_pipe_dcache = Module(new DPIC_PERF_PIPE())
        perf_pipe_dcache.io.clk := clock
        perf_pipe_dcache.io.rst := reset
        perf_pipe_dcache.io.id := 2.U
        perf_pipe_dcache.io.invalid := !io.mem2wb.bits.valid
        perf_pipe_dcache.io.stall := io.ex2mem.stall
    }
}