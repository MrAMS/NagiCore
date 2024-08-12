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
    class WriteInfo extends Bundle{
        val addr    = UInt(addrBits.W)
        val size    = UInt(2.W)
        val wmask   = UInt((dataBits/8).W)
        val wdata   = UInt(dataBits.W)
    }
    val write_buff = Module(new RingBuff(()=>new WriteInfo, writeBuffLen, rchannel=1, debug_id=debug_id))
    write_buff.io.push := false.B
    write_buff.io.pop := false.B
    write_buff.io.wdata := DontCare
    write_buff.io.clear := false.B
    write_buff.io.popN := 0.U

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
        val waitWriteBuff   = Value(2.U)
        val waitReadReady   = Value(4.U)
        val waitRead        = Value(8.U)
    }
    val state = RegInit(State.idle)

    io.out.busy := state =/= State.idle // ...

    val ready_read = axi_r_agent.io.cmd.out.ready&&write_buff.io.empty&&axi_w_agent.io.cmd.out.ready

    switch(state){
        is(State.idle){
            when(io.in.req){
                when(io.in.bits.we){
                    // Write
                    when(write_buff.io.empty && axi_w_agent.io.cmd.out.ready){
                        axi_w_agent.io.cmd.in.req := true.B
                        axi_w_agent.io.cmd.in.addr := io.in.bits.addr
                        axi_w_agent.io.cmd.in.len := 0.U
                        axi_w_agent.io.cmd.in.size := io.in.bits.size
                        axi_w_agent.io.cmd.in.wdata(0) := io.in.bits.wdata
                        axi_w_agent.io.cmd.in.wmask(0) := io.in.bits.wmask
                    }.elsewhen(write_buff.io.full){
                        state := State.waitWriteBuff
                        io.out.busy := true.B

                        cmd_reg := io.in.bits
                    }.otherwise{
                        write_buff.io.push := true.B
                        write_buff.io.wdata := io.in.bits
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
                        cmd_reg := io.in.bits

                        state := State.waitReadReady
                    }
                    io.out.busy := true.B   
                }
            }
        }
        is(State.waitWriteBuff){
            when(!write_buff.io.full){
                write_buff.io.push := true.B
                write_buff.io.wdata := cmd_reg

                io.out.busy := false.B
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

    when(!write_buff.io.empty){
        when(axi_w_agent.io.cmd.out.ready){
            axi_w_agent.io.cmd.in.req := true.B
            axi_w_agent.io.cmd.in.addr := write_buff.io.rdatas(0).addr
            axi_w_agent.io.cmd.in.len := 0.U
            axi_w_agent.io.cmd.in.size := write_buff.io.rdatas(0).size
            axi_w_agent.io.cmd.in.wdata(0) := write_buff.io.rdatas(0).wdata
            axi_w_agent.io.cmd.in.wmask(0) := write_buff.io.rdatas(0).wmask

            write_buff.io.pop := true.B
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
