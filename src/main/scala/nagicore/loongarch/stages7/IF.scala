package nagicore.loongarch.stages7

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
    val instrs_buff = Module(new InstrsBuff(XLEN, XLEN, ICACHE_WORDS, INSTRS_BUFF_SIZE))
    instrs_buff.io.cache <> icache.io.master

    // pipeline registers
    val preg = RegEnable(io.preif2if.bits, !io.if2id.stall && !instrs_buff.io.out.busy)

    instrs_buff.io.in.fetch := preg.valid && !io.if2id.stall // TODO
    instrs_buff.io.in.new_trans := preg.jump && preg.valid && RegNext(!instrs_buff.io.out.busy)
    instrs_buff.io.in.trans_addr := preg.pc

    io.if2id.bits.instr := instrs_buff.io.out.instr
    io.if2id.bits.valid := !instrs_buff.io.out.busy && preg.valid
    io.if2id.bits.pc := preg.pc
    io.if2id.bits.pred_nxt_pc := preg.pred_nxt_pc

    io.preif2if.stall := instrs_buff.io.out.busy || io.if2id.stall

    // assert(instrs_buff.io.out.instr===0.U && (!instrs_buff.io.out.busy) && preg.valid)


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