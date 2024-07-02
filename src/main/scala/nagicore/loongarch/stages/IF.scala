package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4IO
import nagicore.loongarch.Config
import nagicore.unit.{CachePiped, InstrsBuff, InstrsBuffCacheBundle}
import nagicore.loongarch.CtrlFlags
import nagicore.GlobalConfg

class if2idBits extends Bundle with Config{
    val pc          = UInt(XLEN.W)
    val pred_nxt_pc = UInt(XLEN.W)
    val instr       = UInt(XLEN.W)
    
    val valid       = Bool()
}

class if2idIO extends Bundle{
    val bits = Output(new if2idBits)
    val stall = Input(Bool())
}

class IF extends Module with Config{
    val io = IO(new Bundle {
        val preif2if = Flipped(new preif2ifIO)
        val if2id = new if2idIO
        val isram = new AXI4IO(XLEN, XLEN)
    })
    // 2-stages cache
    val icache = Module(new CachePiped(XLEN, XLEN, ICACHE_WAYS, ICACHE_LINES, ICACHE_WORDS, () => new InstrsBuffCacheBundle, 0))
    icache.io.axi <> io.isram
    val instrs_buff = Module(new InstrsBuff(XLEN, XLEN, ICACHE_WORDS, 4))
    instrs_buff.io.cache <> icache.io.master

    // pipeline registers
    val preg = RegEnable(io.preif2if.bits, !io.if2id.stall && !instrs_buff.io.out.busy)

    // val hav_fetch = RegInit(false.B)
    // when(!io.if2id.stall && !instrs_buff.io.out.busy){
    //     hav_fetch := false.B
    // }.elsewhen(!instrs_buff.io.out.busy){
    //     hav_fetch := true.B
    // }

    instrs_buff.io.in.fetch := preg.valid && !io.if2id.stall // TODO
    instrs_buff.io.in.new_trans := preg.jump && preg.valid && RegNext(!instrs_buff.io.out.busy)
    instrs_buff.io.in.trans_addr := preg.pc

    // instrs_buff.io.front.bits.fetch := io.preif2if.bits.valid
    // instrs_buff.io.front.bits.prefetch := io.preif2if.bits.valid
    // instrs_buff.io.front.bits.new_trans := io.preif2if.bits.jump && io.preif2if.bits.valid
    // instrs_buff.io.front.bits.trans_addr := io.preif2if.bits.pc

    io.if2id.bits.instr := instrs_buff.io.out.instr
    io.if2id.bits.valid := !instrs_buff.io.out.busy && preg.valid
    io.if2id.bits.pc := preg.pc
    io.if2id.bits.pred_nxt_pc := preg.pred_nxt_pc

    // instrs_buff.io.front.bits.fetch := io.preif2if.bits.valid
    // instrs_buff.io.front.bits.prefetch := io.preif2if.bits.valid
    // instrs_buff.io.front.bits.new_trans := io.preif2if.bits.jump && io.preif2if.bits.valid
    // instrs_buff.io.front.bits.trans_addr := io.preif2if.bits.pc

    // instrs_buff.io.back.stall := io.if2id.stall
    // io.if2id.bits.instr := instrs_buff.io.back.bits.instr
    // io.if2id.bits.valid := instrs_buff.io.back.bits.valid
    // io.if2id.bits.pc := io.preif2if.bits.pc
    // io.if2id.bits.pred_nxt_pc := io.preif2if.bits.pred_nxt_pc

    io.preif2if.stall := instrs_buff.io.out.busy || io.if2id.stall


    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_PERF_PIPE
        val perf_pipe_icache = Module(new DPIC_PERF_PIPE())
        perf_pipe_icache.io.clk := clock
        perf_pipe_icache.io.rst := reset
        perf_pipe_icache.io.id := 0.U
        perf_pipe_icache.io.invalid := !io.if2id.bits.valid
        perf_pipe_icache.io.stall := instrs_buff.io.out.busy
    }
}