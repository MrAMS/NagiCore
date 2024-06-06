package nagicore.bus

import chisel3._
import chisel3.util._

class AXI4AIO(addrBits: Int) extends Bundle{
    val id = Output(UInt(4.W))
    val addr = Output(UInt(addrBits.W))
    val len = Output(UInt(8.W))
    val size = Output(UInt(3.W))
    val burst = Output(UInt(2.W))
}

class AXI4WIO(dataBits: Int) extends Bundle{
    val data = Output(UInt(dataBits.W))
    val strb = Output(UInt((dataBits/8).W))
    val last = Output(Bool())
}

class AXI4BIO extends Bundle{
    val id = Input(UInt(4.W))
    val resp = Input(UInt(2.W))
}

class AXI4RIO(dataBits: Int) extends Bundle{
    val id = Input(UInt(4.W))
    val resp = Input(UInt(2.W))
    val data = Input(UInt(dataBits.W))
    val last = Input(Bool())
}

class AXI4IO(addrBits: Int, dataBits: Int) extends Bundle{
    val ar = Decoupled(new AXI4AIO(addrBits))
    val r  = Flipped(Decoupled(new AXI4RIO(dataBits)))
    val aw = Decoupled(new AXI4AIO(addrBits))
    val w  = Decoupled(new AXI4WIO(dataBits))
    val b  = Flipped(Decoupled(new AXI4BIO))
}

/**
  * AXI4写通道突发代理
  * 把要写入的所有数据一次性传入，无需管理后续传输的握手
  * 将军，下达命令吧！
  *
  * @param addrBits
  * @param dataBits
  * @param maxBeatsLen
  */
class AXI4WriteAgent(addrBits: Int, dataBits: Int, maxBeatsLen: Int) extends Module{
    val io = IO(new Bundle {
        val cmd = new Bundle {
            val in = Input(new Bundle {
                val req     = Bool()
                val addr    = UInt(addrBits.W)
                // Burst Length: len+1
                val len     = UInt(8.W)
                // Each Transfer Size: 2^size bytes
                val size    = UInt(3.W)
                val wdata   = Vec(maxBeatsLen, UInt(dataBits.W))
                val wmask   = Vec(maxBeatsLen, UInt(log2Up(dataBits).W))
            })
            val out = new Bundle {
                val ready   = Output(Bool())
                val resp    = Output(UInt(2.W))
            }
        }
        val axi = new Bundle {
            val aw = Decoupled(new AXI4AIO(addrBits))
            val w  = Decoupled(new AXI4WIO(dataBits))
            val b  = Flipped(Decoupled(new AXI4BIO))
        }
    })

    object State extends ChiselEnum {
        val idle, write = Value
    }

    val state = RegInit(State.idle)
    val axi_aw_count = RegInit(0.U(1.W))
    val axi_w_count = RegInit(0.U(log2Up(maxBeatsLen+1).W))
    val cmd_buf = Reg(chiselTypeOf(io.cmd.in))

    io.cmd.out.ready := state === State.idle
    io.cmd.out.resp := RegEnable(io.axi.b.bits.resp, io.axi.b.fire)

    io.axi.aw.valid := false.B
    io.axi.aw.bits.addr := cmd_buf.addr
    io.axi.aw.bits.id := 0.U
    // Burst_Length = AxLEN[7:0] + 1
    io.axi.aw.bits.len := cmd_buf.len
    // Burst size. This signal indicates the size of each transfer in the burst.
    io.axi.aw.bits.size := cmd_buf.size
    // The burst type: INCR
    io.axi.aw.bits.burst := 1.U

    io.axi.w.valid := false.B
    io.axi.w.bits.data := cmd_buf.wdata(axi_w_count)
    io.axi.w.bits.strb := cmd_buf.wmask(axi_w_count)
    io.axi.w.bits.last := axi_w_count === cmd_buf.len

    io.axi.b.ready := false.B


    switch(state){
        is(State.idle){
            when(io.cmd.in.req){
                cmd_buf := io.cmd.in
                axi_aw_count := 0.U
                axi_w_count := 0.U
                state := State.write
            }
        }
        is(State.write){
            io.axi.aw.valid := axi_aw_count =/= 1.U
            when(io.axi.aw.fire){
                axi_aw_count := axi_aw_count + 1.U
            }
            io.axi.w.valid := true.B
            when(io.axi.w.fire){
                axi_w_count := axi_w_count + 1.U
            }
            io.axi.b.ready := true.B
            when(io.axi.b.fire){
                assert(io.axi.b.bits.resp === 0.U)
                state := State.idle
            }
        }
    }
    
}

class AXI4ReadAgent(addrBits: Int, dataBits: Int, maxBeatsLen: Int) extends Module{
    val io = IO(new Bundle {
        val cmd = new Bundle {
            val in = Input(new Bundle {
                val req     = Bool()
                val addr    = UInt(addrBits.W)
                // Burst Length: len+1
                val len     = UInt(8.W)
                // Each Transfer Size: 2^size bytes
                val size    = UInt(3.W)
            })
            val out = new Bundle {
                val ready   = Output(Bool())
                val rdata   = Output(UInt(dataBits.W))
                val resp    = Output(UInt(2.W))
                val order   = Output(UInt(log2Up(maxBeatsLen).W))
                val last    = Output(Bool())
            }
        }
        val axi = new Bundle {
            val ar = Decoupled(new AXI4AIO(addrBits))
            val r  = Flipped(Decoupled(new AXI4RIO(dataBits)))
        }
    })
    object State extends ChiselEnum {
        val idle, ar, r = Value
    }
    val state = RegInit(State.idle)
    val axi_r_count = RegInit(0.U(log2Up(maxBeatsLen+1).W))
    val cmd_buf = Reg(chiselTypeOf(io.cmd.in))


    io.axi.ar.valid := state === State.ar
    io.axi.ar.bits.burst := 1.U
    io.axi.ar.bits.id := 0.U
    io.axi.ar.bits.addr := cmd_buf.addr
    io.axi.ar.bits.len := cmd_buf.len
    io.axi.ar.bits.size := cmd_buf.size
    
    io.axi.r.ready := false.B

    io.cmd.out.order := axi_r_count
    io.cmd.out.rdata := io.axi.r.bits.data
    io.cmd.out.ready := io.axi.r.fire || state === State.idle
    io.cmd.out.resp  := io.axi.r.bits.resp
    io.cmd.out.last  := io.axi.r.bits.last

    switch(state){
        is(State.idle){
            when(io.cmd.in.req){
                cmd_buf := io.cmd.in
//                axi_r_count := ~(0.U(log2Up(maxBeatsLen+1).W))
                axi_r_count := 0.U
                state := State.ar
            }
        }
        is(State.ar){
            when(io.axi.ar.fire){
                state := State.r
            }
        }
        is(State.r){
            io.axi.r.ready := true.B
            when(io.axi.r.fire){
                axi_r_count := axi_r_count + 1.U
                when(axi_r_count === cmd_buf.len){
                    assert(io.axi.r.bits.last)
                    assert(io.axi.r.bits.resp === 0.U)
                    state := State.idle
                }
            }
        }
    }

}


/**
 * WIP
 * @param addrBits
 * @param dataBits
 */
class AXI4Slave(addrBits: Int, dataBits: Int) extends Module{
    val io = IO(Flipped(new AXI4IO(addrBits, dataBits)))
    val sram = Module(new SyncRam(dataBits, 1 << addrBits))
    sram.io.en := true.B
    sram.io.we := false.B
    

    val raddr = Reg(UInt(addrBits.W))
    val rid   = Reg(UInt(4.W))
    val rlen  = Reg(UInt(8.W))
    when(io.ar.fire){
        raddr := io.ar.bits.addr
        rid := io.ar.bits.id
        rlen := io.ar.bits.len
    }
    when(io.r.fire){
        raddr := raddr + 1.U
        rlen := rlen - 1.U
    }
    io.ar.ready := rlen === 0.U
    io.r.ready := rlen =/= 0.U
    io.r.bits.last := rlen === 1.U
    io.r.bits.resp := 0.U
    io.r.bits.data := sram.io.dout

    val ws_idle :: ws_w :: ws_b :: nulls = Enum(3)
    val ws = RegInit(ws_idle)
    val waddr = Reg(UInt(addrBits.W))
    val wid   = Reg(UInt(4.W))
    val wlen  = Reg(UInt(8.W))
    when(io.aw.fire){
        waddr := io.aw.bits.addr
        wid := io.aw.bits.id
        wlen := io.aw.bits.len
    }
    when(io.w.fire){
        waddr := waddr + 1.U
        sram.io.din := io.w.bits.data
        sram.io.we := true.B
        wlen := wlen - 1.U
        when(io.w.bits.last){
            ws := ws_b
        }.otherwise{
            ws := ws_w
        }
    }
    when(io.b.fire){
        ws := ws_idle
    }
    io.aw.ready := wlen === 0.U
    io.w.ready := wlen =/= 0.U
    io.w.bits.last := wlen === 1.U
    io.b.valid := ws === ws_b
    io.b.bits.resp := 0.U

}
