package nagicore.unit.cache

import chisel3._
import chisel3.util._

import nagicore.bus._
import chisel3.util.random.LFSR
import nagicore.utils.isPowerOf2
import nagicore.GlobalConfg
import nagicore.unit.RingBuff
/**
  * 为uncache设计的cache(x)，读写均直达
  * 有个写缓存队列(write buffer)，每次写的时候，直接一拍写到缓存队列里面，不阻塞前面的流水线，后台调AXI4自己慢慢写去
  * 
  * @note busy拉高表示请求阻塞前级流水
  *
  * @param addrBits
  * @param dataBits
  * @param writeBuffLen 写缓存队列大小
  */
class UnCache(addrBits:Int, dataBits: Int, writeBuffLen: Int, debug_id: Int=0) extends Module{
    require(isPowerOf2(writeBuffLen))
    val io = IO(new Bundle{
        val axi = new AXI4IO(addrBits, dataBits)
        val in = Input(new Bundle {
            val req     = Bool()
            val bits    = new Bundle {
                val addr    = UInt(addrBits.W)
                val we      = Bool()
                val wmask   = UInt((dataBits/8).W)
                val size    = UInt(2.W)
                val wdata   = UInt(dataBits.W)
            }
        })
        val out = Output(new Bundle {
            val busy    = Bool()
            val rdata   = UInt(dataBits.W)
        })
    })


    val axi_w_agent = Module(new AXI4WriteAgent(addrBits, dataBits, 1))
    axi_w_agent.io.axi.aw <> io.axi.aw
    axi_w_agent.io.axi.w <> io.axi.w
    axi_w_agent.io.axi.b <> io.axi.b
    axi_w_agent.io.cmd.in <> DontCare
    axi_w_agent.io.cmd.in.req := false.B

    val axi_r_agent = Module(new AXI4ReadAgent(addrBits, dataBits, 1))
    axi_r_agent.io.axi.ar <> io.axi.ar
    axi_r_agent.io.axi.r <> io.axi.r
    axi_r_agent.io.cmd.in <> DontCare
    axi_r_agent.io.cmd.in.req := false.B
    
    val cmd_reg = Reg(io.in.bits.cloneType)

    val rdata_reg = Reg(UInt(dataBits.W))
    io.out.rdata := rdata_reg


    object State extends ChiselEnum {
        val idle            = Value(1.U)
        val waitWriteReady  = Value(2.U)
        val waitWrite       = Value(4.U)
        val waitReadReady   = Value(8.U)
        val waitRead        = Value(16.U)
    }
    val state = RegInit(State.idle)

    io.out.busy := state =/= State.idle // ...

    val ready_write = axi_w_agent.io.cmd.out.ready
    val ready_read = axi_r_agent.io.cmd.out.ready && axi_w_agent.io.cmd.out.ready

    switch(state){
        is(State.idle){
            when(io.in.req){
                cmd_reg := io.in.bits
                when(io.in.bits.we){
                    // Write
                    when(ready_write){
                        axi_w_agent.io.cmd.in.req := true.B
                        axi_w_agent.io.cmd.in.addr := io.in.bits.addr
                        axi_w_agent.io.cmd.in.len := 0.U
                        axi_w_agent.io.cmd.in.size := io.in.bits.size
                        axi_w_agent.io.cmd.in.wdata(0) := io.in.bits.wdata
                        axi_w_agent.io.cmd.in.wmask(0) := io.in.bits.wmask

                        state := State.idle
                    }.otherwise{
                        state := State.waitWriteReady
                        io.out.busy := true.B
                    }
                }.otherwise{
                    // Read
                    when(ready_read){
                        axi_r_agent.io.cmd.in.req := true.B
                        axi_r_agent.io.cmd.in.addr := io.in.bits.addr
                        axi_r_agent.io.cmd.in.len := 0.U
                        axi_r_agent.io.cmd.in.size := log2Up(dataBits).U

                        state := State.waitRead
                    }.otherwise{
                        state := State.waitReadReady
                    }
                    io.out.busy := true.B 
                }
            }
        }
        is(State.waitWriteReady){
            when(ready_write){
                axi_w_agent.io.cmd.in.req := true.B
                axi_w_agent.io.cmd.in.addr := cmd_reg.addr
                axi_w_agent.io.cmd.in.len := 0.U
                axi_w_agent.io.cmd.in.size := cmd_reg.size
                axi_w_agent.io.cmd.in.wdata(0) := cmd_reg.wdata
                axi_w_agent.io.cmd.in.wmask(0) := cmd_reg.wmask

                state := State.idle
            }
        }
        is(State.waitReadReady){
            when(ready_read){
                axi_r_agent.io.cmd.in.req := true.B
                axi_r_agent.io.cmd.in.addr := cmd_reg.addr
                axi_r_agent.io.cmd.in.len := 0.U
                axi_r_agent.io.cmd.in.size := log2Up(dataBits).U

                state := State.waitRead
            }
        }
        is(State.waitRead){
            when(axi_r_agent.io.cmd.out.ready){
                rdata_reg := axi_r_agent.io.cmd.out.rdata
                assert(axi_r_agent.io.cmd.out.resp === 0.U)
                state := State.idle
            }
        }
    }

    if(GlobalConfg.SIM){
        import  nagicore.unit.DPIC_PERF_CACHE
        val dpic_perf_cache = Module(new DPIC_PERF_CACHE)
        dpic_perf_cache.io.clk := clock
        dpic_perf_cache.io.rst := reset
        dpic_perf_cache.io.valid := io.in.req
        dpic_perf_cache.io.id := debug_id.U
        dpic_perf_cache.io.access_type := Cat(0.U, !io.out.busy)
    }

}
