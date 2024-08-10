package nagicore.loongarch.nscscc2024Dual.stages

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4IO
//import nagicore.unit.{InstrsBuff, InstrsBuffCacheBundle}
import nagicore.unit.cache.Cache
import nagicore.GlobalConfg
import nagicore.unit.cache.CacheReplaceType
import nagicore.unit.BTBPredOutIO
import nagicore.loongarch.nscscc2024Dual.{Config, CtrlFlags}
import nagicore.bus.RamType


class if2idBits extends Bundle with Config{
    val pc          = UInt(XLEN.W)
    val bpu_out     = new BTBPredOutIO(BTB_ENTRYS, XLEN)
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
    // 2-stages 1cyc cache
    val icache = Module(new Cache(XLEN, XLEN, ICACHE_WAYS, ICACHE_LINES, ICACHE_WORDS, () => new preif2ifBits(), CacheReplaceType.LRU, 
        dataRamType = RamType.RAM_1CYC,
        tagVRamType = RamType.RAM_1CYC,
        debug_id = 0))
    icache.io.axi <> io.isram

    icache.io.master.front.bits.addr := io.preif2if.bits.pc
    icache.io.master.front.bits.size := 2.U
    icache.io.master.front.bits.uncache := false.B
    icache.io.master.front.bits.wmask := 0.U
    icache.io.master.front.bits.valid := io.preif2if.bits.valid
    icache.io.master.front.bits.wdata := DontCare
    icache.io.master.front.bits.pipedata := io.preif2if.bits
    icache.io.master.back.stall := io.if2id.stall


    io.if2id.bits.instr := icache.io.master.back.bits.rdata
    io.if2id.bits.valid := icache.io.master.back.bits.valid
    io.if2id.bits.pc := icache.io.master.back.bits.pipedata.pc
    io.if2id.bits.bpu_out := icache.io.master.back.bits.pipedata.bpu_out

    io.preif2if.stall := icache.io.master.front.stall

    if(GlobalConfg.SIM){
        import nagicore.unit.DPIC_PERF_PIPE
        val perf_pipe_if = Module(new DPIC_PERF_PIPE())
        perf_pipe_if.io.clk := clock
        perf_pipe_if.io.rst := reset
        perf_pipe_if.io.id := 0.U
        perf_pipe_if.io.invalid := !io.if2id.bits.valid
        perf_pipe_if.io.stall := io.preif2if.stall
    }
}