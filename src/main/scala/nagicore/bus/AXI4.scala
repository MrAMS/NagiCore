package nagicore.bus

import chisel3._
import chisel3.util._

class AXI4AIO(addrBits: Int, idBits: Int=8) extends Bundle{
    val id = Output(UInt(idBits.W))
    val addr = Output(UInt(addrBits.W))
    val len = Output(UInt(8.W))
    val size = Output(UInt(3.W))
    val burst = Output(UInt(2.W))

    // val valid = Output(Bool())
    // val ready = Input(Bool())
    // def fire = valid && ready
}

class AXI4WIO(dataBits: Int) extends Bundle{
    val data = Output(UInt(dataBits.W))
    val strb = Output(UInt((dataBits/8).W))
    val last = Output(Bool())

    // val valid = Output(Bool())
    // val ready = Input(Bool())
    // def fire = valid && ready
}

class AXI4BIO(idBits: Int=8) extends Bundle{
    val id = Output(UInt(idBits.W))
    val resp = Output(UInt(2.W))

    // val valid = Input(Bool())
    // val ready = Output(Bool())
    // def fire = valid && ready
}

class AXI4RIO(dataBits: Int, idBits: Int=8) extends Bundle{
    val id = Output(UInt(idBits.W))
    val resp = Output(UInt(2.W))
    val data = Output(UInt(dataBits.W))
    val last = Output(Bool())

    // val valid = Input(Bool())
    // val ready = Output(Bool())
    // def fire = valid && ready
}

class AXI4IO(addrBits: Int, dataBits: Int, idBits: Int=8) extends Bundle{
    val ar = Decoupled(new AXI4AIO(addrBits, idBits))
    val r  = Flipped(Decoupled(new AXI4RIO(dataBits, idBits)))
    val aw = Decoupled(new AXI4AIO(addrBits, idBits))
    val w  = Decoupled(new AXI4WIO(dataBits))
    val b  = Flipped(Decoupled(new AXI4BIO(idBits)))
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
class AXI4WriteAgent(addrBits: Int, dataBits: Int, maxBeatsLen: Int, idBits: Int=8) extends Module{
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
            val aw = Decoupled(new AXI4AIO(addrBits, idBits))
            val w  = Decoupled(new AXI4WIO(dataBits))
            val b  = Flipped(Decoupled(new AXI4BIO(idBits)))
        }
    })

    val axi_aw_count = RegInit(0.U(1.W))
    val axi_w_count = RegInit(0.U(log2Up(maxBeatsLen).W))
    val cmd_buf = Reg(chiselTypeOf(io.cmd.in))

    val wait_aw = RegInit(false.B)
    val wait_w = RegInit(false.B)
    val wait_b = RegInit(false.B)

    io.cmd.out.ready := !(wait_aw || wait_w || wait_b)
    io.cmd.out.resp := RegEnable(io.axi.b.bits.resp, io.axi.b.fire)

    when(io.cmd.in.req){
        wait_aw := true.B
        wait_w := true.B
        wait_b := true.B
        cmd_buf := io.cmd.in
        assert(io.cmd.out.ready)
    }

    when(io.axi.aw.fire){
        wait_aw := false.B
    }

    io.axi.aw.valid := wait_aw || io.cmd.in.req
    io.axi.aw.bits.addr := Mux(wait_aw || wait_w || wait_b, cmd_buf.addr, io.cmd.in.addr)
    io.axi.aw.bits.id := DontCare
    // Burst_Length = AxLEN[7:0] + 1
    io.axi.aw.bits.len := Mux(wait_aw || wait_w || wait_b, cmd_buf.len, io.cmd.in.len)
    // Burst size. This signal indicates the size of each transfer in the burst.
    io.axi.aw.bits.size := Mux(wait_aw || wait_w || wait_b, cmd_buf.size, io.cmd.in.size)
    // The burst type: INCR
    io.axi.aw.bits.burst := 1.U

    when(io.axi.w.fire){
        axi_w_count := axi_w_count + 1.U
        when(io.axi.w.bits.last){
            wait_w := false.B
        }
    }

    io.axi.w.valid := wait_w || io.cmd.in.req
    val w_index = if(maxBeatsLen==1) 0.U else axi_w_count
    io.axi.w.bits.data := Mux(wait_w, cmd_buf.wdata(w_index), io.cmd.in.wdata(w_index))
    io.axi.w.bits.strb := Mux(wait_w, cmd_buf.wmask(w_index), io.cmd.in.wmask(w_index))
    io.axi.w.bits.last := axi_w_count === Mux(wait_w, cmd_buf.len, io.cmd.in.len)

    when(io.axi.b.fire){
        assert(io.axi.b.bits.resp === 0.U)
        axi_aw_count := 0.U
        axi_w_count := 0.U
        wait_b := false.B
        assert(wait_b)
    }

    io.axi.b.ready := !wait_aw && !wait_w
    
}

class AXI4ReadAgent(addrBits: Int, dataBits: Int, maxBeatsLen: Int, idBits: Int=8) extends Module{
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
            val ar = Decoupled(new AXI4AIO(addrBits, idBits))
            val r  = Flipped(Decoupled(new AXI4RIO(dataBits, idBits)))
        }
    })

    val axi_r_count = RegInit(0.U(log2Up(maxBeatsLen).W))
    val wait_ar = RegInit(false.B)
    val wait_r = RegInit(false.B)
    val cmd_buf = Reg(chiselTypeOf(io.cmd.in))

    when(io.cmd.in.req){
        wait_ar := true.B
        cmd_buf := io.cmd.in
        axi_r_count := 0.U
        assert(io.cmd.out.ready)
    }

    when(io.axi.ar.fire){
        wait_ar := false.B
        wait_r := true.B
    }

    io.axi.ar.valid := io.cmd.in.req || wait_ar
    io.axi.ar.bits.burst := 1.U
    io.axi.ar.bits.id := DontCare
    io.axi.ar.bits.addr := Mux(wait_ar || wait_r, cmd_buf.addr, io.cmd.in.addr)
    io.axi.ar.bits.len := Mux(wait_ar || wait_r, cmd_buf.len, io.cmd.in.len)
    io.axi.ar.bits.size := Mux(wait_ar || wait_r, cmd_buf.size, io.cmd.in.size)

    when(io.axi.r.fire){
        axi_r_count := axi_r_count + 1.U
        when(io.axi.r.bits.last){
            assert(io.axi.r.bits.last)
            assert(io.axi.r.bits.resp === 0.U)
            wait_r := false.B
        }
    }
    
    io.axi.r.ready := wait_r

    io.cmd.out.order := axi_r_count
    io.cmd.out.rdata := io.axi.r.bits.data
    io.cmd.out.ready := !(wait_ar || wait_r) || io.axi.r.fire
    io.cmd.out.resp  := io.axi.r.bits.resp
    io.cmd.out.last  := io.axi.r.bits.last


}
// class AXI4WriteAgent(addrBits: Int, dataBits: Int, maxBeatsLen: Int, idBits: Int=8) extends Module{
//     val io = IO(new Bundle {
//         val cmd = new Bundle {
//             val in = Input(new Bundle {
//                 val req     = Bool()
//                 val addr    = UInt(addrBits.W)
//                 // Burst Length: len+1
//                 val len     = UInt(8.W)
//                 // Each Transfer Size: 2^size bytes
//                 val size    = UInt(3.W)
//                 val wdata   = Vec(maxBeatsLen, UInt(dataBits.W))
//                 val wmask   = Vec(maxBeatsLen, UInt(log2Up(dataBits).W))
//             })
//             val out = new Bundle {
//                 val ready   = Output(Bool())
//                 val resp    = Output(UInt(2.W))
//             }
//         }
//         val axi = new Bundle {
//             val aw = Decoupled(new AXI4AIO(addrBits, idBits))
//             val w  = Decoupled(new AXI4WIO(dataBits))
//             val b  = Flipped(Decoupled(new AXI4BIO(idBits)))
//         }
//     })

//     object State extends ChiselEnum {
//         val idle, write, resp = Value
//     }

//     val state = RegInit(State.idle)
//     val axi_aw_count = RegInit(0.U(1.W))
//     val axi_w_count = RegInit(0.U(log2Up(maxBeatsLen).W))
//     val cmd_buf = Reg(chiselTypeOf(io.cmd.in))

//     io.cmd.out.ready := state === State.idle
//     io.cmd.out.resp := RegEnable(io.axi.b.bits.resp, io.axi.b.fire)

//     io.axi.aw.valid := false.B
//     io.axi.aw.bits.addr := cmd_buf.addr
//     io.axi.aw.bits.id := DontCare
//     // Burst_Length = AxLEN[7:0] + 1
//     io.axi.aw.bits.len := cmd_buf.len
//     // Burst size. This signal indicates the size of each transfer in the burst.
//     io.axi.aw.bits.size := cmd_buf.size
//     // The burst type: INCR
//     io.axi.aw.bits.burst := 1.U

//     io.axi.w.valid := false.B
//     io.axi.w.bits.data := cmd_buf.wdata(if(maxBeatsLen==1) 0.U else axi_w_count)
//     io.axi.w.bits.strb := cmd_buf.wmask(if(maxBeatsLen==1) 0.U else axi_w_count)
//     io.axi.w.bits.last := axi_w_count === cmd_buf.len

//     io.axi.b.ready := false.B


//     switch(state){
//         is(State.idle){
//             when(io.cmd.in.req){
//                 cmd_buf := io.cmd.in
//                 axi_aw_count := 0.U
//                 axi_w_count := 0.U
//                 state := State.write
//             }
//         }
//         is(State.write){
//             io.axi.aw.valid := axi_aw_count =/= 1.U
//             when(io.axi.aw.fire){
//                 axi_aw_count := axi_aw_count + 1.U
//             }
//             io.axi.w.valid := true.B
//             when(io.axi.w.fire){
//                 axi_w_count := axi_w_count + 1.U
//                 when(io.axi.w.bits.last){
//                     state := State.resp
//                 }
//             }
//         }
//         is(State.resp){
//             io.axi.b.ready := true.B
//             when(io.axi.b.fire){
//                 assert(io.axi.b.bits.resp === 0.U)
//                 state := State.idle
//             }
//         }
//     }
    
// }

// class AXI4ReadAgent(addrBits: Int, dataBits: Int, maxBeatsLen: Int, idBits: Int=8) extends Module{
//     val io = IO(new Bundle {
//         val cmd = new Bundle {
//             val in = Input(new Bundle {
//                 val req     = Bool()
//                 val addr    = UInt(addrBits.W)
//                 // Burst Length: len+1
//                 val len     = UInt(8.W)
//                 // Each Transfer Size: 2^size bytes
//                 val size    = UInt(3.W)
//             })
//             val out = new Bundle {
//                 val ready   = Output(Bool())
//                 val rdata   = Output(UInt(dataBits.W))
//                 val resp    = Output(UInt(2.W))
//                 val order   = Output(UInt(log2Up(maxBeatsLen).W))
//                 val last    = Output(Bool())
//             }
//         }
//         val axi = new Bundle {
//             val ar = Decoupled(new AXI4AIO(addrBits, idBits))
//             val r  = Flipped(Decoupled(new AXI4RIO(dataBits, idBits)))
//         }
//     })
//     object State extends ChiselEnum {
//         val idle, ar, r = Value
//     }
//     val state = RegInit(State.idle)
//     val axi_r_count = RegInit(0.U(log2Up(maxBeatsLen).W))
//     val cmd_buf = Reg(chiselTypeOf(io.cmd.in))


//     io.axi.ar.valid := state === State.ar
//     io.axi.ar.bits.burst := 1.U
//     io.axi.ar.bits.id := DontCare
//     io.axi.ar.bits.addr := cmd_buf.addr
//     io.axi.ar.bits.len := cmd_buf.len
//     io.axi.ar.bits.size := cmd_buf.size
    
//     io.axi.r.ready := false.B

//     io.cmd.out.order := axi_r_count
//     io.cmd.out.rdata := io.axi.r.bits.data
//     io.cmd.out.ready := io.axi.r.fire || state === State.idle
//     io.cmd.out.resp  := io.axi.r.bits.resp
//     io.cmd.out.last  := io.axi.r.bits.last

//     switch(state){
//         is(State.idle){
//             when(io.cmd.in.req){
//                 cmd_buf := io.cmd.in
// //                axi_r_count := ~(0.U(log2Up(maxBeatsLen+1).W))
//                 axi_r_count := 0.U
//                 state := State.ar
//             }
//         }
//         is(State.ar){
//             when(io.axi.ar.fire){
//                 state := State.r
//             }
//         }
//         is(State.r){
//             io.axi.r.ready := true.B
//             when(io.axi.r.fire){
//                 axi_r_count := axi_r_count + 1.U
//                 when(io.axi.r.bits.last){
//                     assert(io.axi.r.bits.last)
//                     assert(io.axi.r.bits.resp === 0.U)
//                     state := State.idle
//                 }
//             }
//         }
//     }

// }


/**
 * AXI4 同步RAM从设备，第一个周期输入地址，第二个周期获得数据
 * 注意：突发模式下，假设了主设备能够连续握手
 * @param addrBits 地址宽度
 * @param dataBits 数据宽度
 */
class AXI4SRAM(addrBits: Int, dataBits: Int, depth: Long, width: Int, idBits: Int=8) extends Module{
    val io = IO(new Bundle{
        val axi = Flipped(new AXI4IO(addrBits, dataBits, idBits))
        val sram = Flipped(new RamIO(dataBits, depth))
    })
    val raddr = Reg(UInt(addrBits.W))
    val rid   = Reg(UInt(idBits.W))
    val rlen  = Reg(UInt(8.W))

    val rs_idle :: rs_r :: Nil = Enum(2)
    val rs = RegInit(rs_idle)


    when(io.axi.ar.fire){
        raddr := io.axi.ar.bits.addr + (dataBits/8).U
        rid := io.axi.ar.bits.id
        rlen := io.axi.ar.bits.len
        rs := rs_r;
    }
    // val access_in_advence = !io.axi.r.valid && rs =/= rs_idle
    // when(access_in_advence){
    //     raddr := raddr + (dataBits/8).U
    // }

    when(io.axi.r.fire){
        raddr := raddr + (dataBits/8).U
        when(rlen === 0.U){
            rs := rs_idle
        }otherwise{
            rlen := rlen - 1.U
        }
    }
    io.axi.ar.ready := rs === rs_idle
    io.axi.r.valid := rs === rs_r
    io.axi.r.bits.id := rid
    io.axi.r.bits.last := rlen === 0.U
    io.axi.r.bits.resp := 0.U
    io.axi.r.bits.data := io.sram.dout

    val ws_idle :: ws_w :: ws_b :: Nil = Enum(3)
    val ws = RegInit(ws_idle)
    val waddr = Reg(UInt(addrBits.W))
    val wid   = Reg(UInt(idBits.W))
    val wlen  = Reg(UInt(8.W))
    when(io.axi.aw.fire){
        waddr := io.axi.aw.bits.addr
        wid := io.axi.aw.bits.id
        wlen := io.axi.aw.bits.len
        ws := ws_w
    }
    when(io.axi.w.fire){
        waddr := waddr + (dataBits/8).U
        when(io.axi.w.bits.last){
            ws := ws_b
        }.otherwise{
            // ws := ws_w
            wlen := wlen - 1.U
        }
    }
    when(io.axi.b.fire){
        ws := ws_idle
    }
    io.axi.aw.ready := ws === ws_idle
    io.axi.w.ready := ws === ws_w
    io.axi.b.bits.id := wid
    io.axi.b.valid := ws === ws_b
    io.axi.b.bits.resp := 0.U

    val sram_addr = Mux(io.axi.w.fire, waddr, Mux(!io.axi.ar.fire, raddr, io.axi.ar.bits.addr))(addrBits-1, log2Ceil(width/8))
    io.sram.addr := sram_addr
    val sram_read_req = io.axi.ar.fire || rlen =/= 0.U
    io.sram.we := io.axi.w.fire
    io.sram.re := sram_read_req
    io.sram.din := io.axi.w.bits.data
    io.sram.wmask := io.axi.w.bits.strb
    io.sram.en := sram_read_req || io.axi.w.fire
}

class AXI4SRAM_MultiCycs(addrBits: Int, dataBits: Int, idBits: Int, depth: Long, width: Int, rExtCycs: Int=0, wExtCycs: Int=0) extends Module{
    val io = IO(new Bundle{
        val axi = Flipped(new AXI4IO(addrBits, dataBits, idBits))
        val sram = Flipped(new RamIO(dataBits, depth))
    })

    val raddr = Reg(UInt(addrBits.W))
    val rid   = Reg(UInt(idBits.W))
    val rlen  = Reg(UInt(8.W))

    val rs_idle :: rs_r :: Nil = Enum(2)
    val rs = RegInit(rs_idle)
    val rrcycs = RegInit(rExtCycs.U)
    when(rs === rs_r){
        when(io.axi.r.fire){
            rrcycs := rExtCycs.U
        }.otherwise{
            rrcycs := Mux(rrcycs === 0.U, 0.U, rrcycs - 1.U)
        }
    }

    when(io.axi.ar.fire){
        raddr := io.axi.ar.bits.addr
        rid := io.axi.ar.bits.id
        rlen := io.axi.ar.bits.len
        rs := rs_r
    }

    when(io.axi.r.fire){
        raddr := raddr + (dataBits/8).U
        when(rlen === 0.U){
            rs := rs_idle
        }otherwise{
            rlen := rlen - 1.U
        }
    }
    io.axi.ar.ready := rs === rs_idle
    io.axi.r.valid := rs === rs_r && rrcycs === 0.U
    io.axi.r.bits.id := rid
    io.axi.r.bits.last := rlen === 0.U
    io.axi.r.bits.resp := 0.U
    io.axi.r.bits.data := io.sram.dout

    val ws_idle :: ws_w :: ws_b :: Nil = Enum(3)
    val ws = RegInit(ws_idle)
    val waddr = Reg(UInt(addrBits.W))
    val wid   = Reg(UInt(idBits.W))
    val wlen  = Reg(UInt(8.W))
    val wwcycs = RegInit(wExtCycs.U)
    when(ws === ws_w){
        when(io.axi.w.fire){
            wwcycs := wExtCycs.U
        }.otherwise{
            wwcycs := Mux(wwcycs === 0.U, 0.U, wwcycs - 1.U)
        }
    }

    when(io.axi.aw.fire){
        waddr := io.axi.aw.bits.addr
        wid := io.axi.aw.bits.id
        wlen := io.axi.aw.bits.len
        ws := ws_w
        assert(io.axi.w.valid)
    }
    when(io.axi.w.fire){
        waddr := waddr + (dataBits/8).U
        when(io.axi.w.bits.last){
            ws := ws_b
        }.otherwise{
            // ws := ws_w
            wlen := wlen - 1.U
        }
    }
    when(io.axi.b.fire){
        ws := ws_idle
    }
    io.axi.aw.ready := ws === ws_idle
    io.axi.w.ready := ws === ws_w && wwcycs === 0.U
    io.axi.b.bits.id := wid
    io.axi.b.valid := ws === ws_b
    io.axi.b.bits.resp := 0.U

    // io.sram.we := ws === ws_w || io.axi.aw.fire
    io.sram.we := ws === ws_w

    // io.sram.re := rs === rs_r || io.axi.ar.fire
    io.sram.re := rs === rs_r

    // io.sram.din := Mux(io.axi.ar.fire, io.axi.w.bits.data, RegNext(io.axi.w.bits.data))
    io.sram.din := io.axi.w.bits.data

    // io.sram.wmask := Mux(io.axi.ar.fire, io.axi.w.bits.strb, RegNext(io.axi.w.bits.strb))
    io.sram.wmask := io.axi.w.bits.strb

    val sram_addr = Mux(ws === ws_w, waddr, raddr)
    // val sram_addr = Mux1H(Seq(
    //     (io.axi.aw.fire) -> io.axi.aw.bits.addr,
    //     (ws === ws_w) -> waddr,
    //     (io.axi.ar.fire) -> io.axi.ar.bits.addr,
    //     (rs === rs_r) -> raddr
    // ))
    io.sram.addr := sram_addr(addrBits-1, log2Ceil(width/8))
    
    // io.sram.en := rs === rs_r || ws === ws_w || io.axi.ar.fire || io.axi.aw.fire
    io.sram.en := rs === rs_r || ws === ws_w
}

// class AXI4Dummy(addrBits: Int, dataBits: Int, idBits: Int=8) extends Module{
//     val io = IO(new Bundle{
//         val axi = Flipped(new AXI4IO(addrBits, dataBits, idBits))
//     })

//     class DPIC_SRAM extends BlackBox(Map("ADDR_WIDTH" -> addrBits, "DATA_WIDTH" -> dataBits)) with HasBlackBoxResource{
//         val io = IO(new Bundle {
//             val clk     = Input(Clock())
//             val rst     = Input(Bool())
//             val en      = Input(Bool())
//             val addr    = Input(UInt(addrBits.W))
//             val wmask   = Input(UInt((dataBits/8).W))
//             val size    = Input(UInt(2.W))
//             val wdata   = Input(UInt(dataBits.W))
//             val rdata   = Output(UInt(dataBits.W))
//         })
//         addResource("/sv/DPIC_SRAM.sv")
//         addResource("/sv/DPIC_TYPES_DEFINE.sv")
//     }

//     val dpic = Module(new DPIC_SRAM)

//     val rs_idle :: rs_r :: Nil = Enum(2)
//     val rs = RegInit(rs_idle)
//     val rid = Reg(UInt(idBits.W))
//     val raddr = Reg(UInt(addrBits.W)) 

//     io.axi.ar.ready := true.B
//     when(io.axi.ar.fire){
//         rs := rs_r
//         rid := io.axi.ar.bits.id
//         raddr := io.axi.ar.bits.addr
//         dpic.io.addr := io.axi.ar.bits.addr
//         dpic.io.en := true.B
//         dpic.io.wmask := 0.U
//     }
//     io.axi.r.valid := rs === rs_r
//     io.axi.r.bits.data := dpic.io.rdata
//     io.axi.r.bits.last := io.axi.r.fire
//     io.axi.r.bits.id := rid
//     io.axi.r.bits.resp := 0.U
//     when(io.axi.r.fire){
//         rs := rs_idle
//     }

//     val ws_idle :: ws_w :: ws_b :: Nil = Enum(3)
//     val ws = RegInit(ws_idle)
//     val wid = Reg(UInt(idBits.W))
//     val waddr = Reg(UInt(addrBits.W))
//     val wdata = Reg(UInt(dataBits.W))
//     val wstrb = Reg(UInt((dataBits/8).W))
//     val wsize = Reg(UInt(io.axi.aw.bits.size.getWidth.W))

//     io.axi.aw.ready := true.B
//     when(io.axi.aw.fire){
//         ws := ws_w
//         waddr := io.axi.aw.bits.addr
//         wid := io.axi.aw.bits.id
//         wsize := io.axi.aw.bits.size
//         ws := ws_w
//     }

//     io.axi.w.ready := ws === ws_w
//     when(io.axi.w.fire){
//         ws := ws_b
//         dpic.io.addr := waddr
//         dpic.io.wdata := io.axi.w.bits.data
//         dpic.io.wmask := Mux(io.axi.r.fire, 0.U, io.axi.w.bits.strb)
//         dpic.io.size := wsize
//         dpic.io.en := true.B
//     }

//     when(io.axi.b.fire){
//         ws := ws_idle
//     }

//     io.axi.b.valid := ws === ws_b
//     io.axi.b.bits.resp := 0.U
//     io.axi.b.bits.id := wid

//     dpic.io.clk := clock
//     dpic.io.rst := reset
//     dpic.io.addr := 0.U
//     dpic.io.wmask := 0.U
//     dpic.io.size := 0.U
//     dpic.io.wdata := 0.U
//     dpic.io.en := false.B
// }

// ref: https://github.com/BUAA-CI-LAB/eulacore
class AXI4XBar1toN(addrBits: Int, dataBits: Int, idBits: Int, addressSpace: List[(Long, Long, Boolean)]) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new AXI4IO(addrBits, dataBits))
    val out = Vec(addressSpace.length, new AXI4IO(addrBits, dataBits))
  })

  val s_idle :: s_resp :: s_error :: Nil = Enum(3)
  val r_state = RegInit(s_idle)
  val w_state = RegInit(s_idle)

  val raddr = io.in.ar.bits.addr
  val routSelVec = VecInit(
    addressSpace.map(range =>
      (raddr >= range._1.U && raddr < (range._1 + range._2).U)
    )
  )
  val routSelIdx = PriorityEncoder(routSelVec)
  val routSel = io.out(routSelIdx)
  val routSelIdxResp =
    RegEnable(routSelIdx, routSel.ar.fire && (r_state === s_idle))
  val routSelResp = io.out(routSelIdxResp)
  val rreqInvalidAddr = io.in.ar.valid && !routSelVec.asUInt.orR

  // bind out.req channel
  (io.out zip routSelVec).map {
    case (o, v) => {
      o.ar.bits := io.in.ar.bits
      o.ar.valid := v && (io.in.ar.valid && (r_state === s_idle))
      o.r.ready := v
    }
  }
  for (i <- 0 until addressSpace.length) {
    when(routSelIdx === i.U) {
        if(addressSpace(i)._3){
            io.out(i).ar.bits.addr := io.in.ar.bits.addr - addressSpace(i)._1.U
        }else{
            io.out(i).ar.bits.addr := io.in.ar.bits.addr
        }
    }
  }

  switch(r_state) {
    is(s_idle) {
      when(routSel.ar.fire) { r_state := s_resp }
      when(rreqInvalidAddr) { r_state := s_error }
    }
    is(s_resp) { when(routSelResp.r.bits.last) { r_state := s_idle } }
    is(s_error) { when(io.in.r.fire) { r_state := s_idle } }
  }

  io.in.r.bits <> routSelResp.r.bits
  io.in.r.valid := routSelResp.r.valid || r_state === s_error
  routSelResp.r.ready := io.in.r.ready
  io.in.ar.ready := (routSel.ar.ready && r_state === s_idle) || rreqInvalidAddr

  val waddr = io.in.aw.bits.addr
  val woutSelVec = VecInit(
    addressSpace.map(range =>
      (waddr >= range._1.U && waddr < (range._1 + range._2).U)
    )
  )
  val woutSelIdx = PriorityEncoder(woutSelVec)
  val woutSel = io.out(woutSelIdx)
  val woutSelIdxResp =
    RegEnable(woutSelIdx, woutSel.aw.fire && (w_state === s_idle))
  val woutSelResp = io.out(woutSelIdxResp)
  val wreqInvalidAddr = io.in.aw.valid && !woutSelVec.asUInt.orR

  (io.out zip woutSelVec).map {
    case (o, v) => {
      o.aw.bits := io.in.aw.bits
      o.aw.valid := v && (io.in.aw.valid && (w_state === s_idle))
      o.w.bits := io.in.w.bits
      o.w.valid := v && io.in.w.valid
      o.b.ready := v
    }
  }
  for (i <- 0 until addressSpace.length) {
    when(woutSelIdx === i.U) {
        if(addressSpace(i)._3){
            io.out(i).aw.bits.addr := io.in.aw.bits.addr - addressSpace(i)._1.U
        }else{
            io.out(i).aw.bits.addr := io.in.aw.bits.addr
        }
    }
  }

  switch(w_state) {
    is(s_idle) {
      when(woutSel.aw.fire) { w_state := s_resp }
      when(wreqInvalidAddr) { w_state := s_error }
    }
    is(s_resp) { when(woutSelResp.b.fire) { w_state := s_idle } }
    is(s_error) { when(io.in.b.fire) { w_state := s_idle } }
  }

  io.in.b.bits <> woutSelResp.b.bits
  io.in.b.valid := woutSelResp.b.valid || w_state === s_error
  woutSelResp.b.ready := io.in.b.ready
  io.in.aw.ready := (woutSel.aw.ready && w_state === s_idle) || wreqInvalidAddr
  io.in.w.ready := woutSel.w.ready
}

// ref: https://github.com/BUAA-CI-LAB/eulacore
class AXI4XBarNto1(n: Int, addrBits: Int, dataBits: Int, idBits: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new AXI4IO(addrBits, dataBits, idBits)))
    val out = new AXI4IO(addrBits, dataBits, idBits)
  })
  val s_idle :: s_readResp :: s_writeResp :: Nil = Enum(3)
  val r_state = RegInit(s_idle)
  val inputArb_r = Module(new Arbiter(new AXI4AIO(addrBits, idBits), n))
  (inputArb_r.io.in zip io.in.map(_.ar)).map { case (arb, in) => arb <> in }
  val thisReq_r = inputArb_r.io.out
  val inflightSrc_r = Reg(UInt(log2Ceil(n).W))

  io.out.ar.bits := Mux(
    r_state === s_idle,
    thisReq_r.bits,
    io.in(inflightSrc_r).ar.bits
  )

  io.out.ar.valid := thisReq_r.valid && (r_state === s_idle)
  io.in.map(_.ar.ready := false.B)
  thisReq_r.ready := io.out.ar.ready && (r_state === s_idle)

  io.in.map(_.r.bits := io.out.r.bits)
  io.in.map(_.r.valid := false.B)
  (io.in(inflightSrc_r).r, io.out.r) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  switch(r_state) {
    is(s_idle) {
      when(thisReq_r.fire) {
        inflightSrc_r := inputArb_r.io.chosen
        io.in(inputArb_r.io.chosen).ar.ready := true.B
        when(thisReq_r.valid) { r_state := s_readResp }
      }
    }
    is(s_readResp) {
      when(io.out.r.fire && io.out.r.bits.last) { r_state := s_idle }
    }
  }

  val w_state = RegInit(s_idle)
  val inputArb_w = Module(new Arbiter(new AXI4AIO(addrBits, idBits), n))
  (inputArb_w.io.in zip io.in.map(_.aw)).map { case (arb, in) => arb <> in }
  val thisReq_w = inputArb_w.io.out
  val inflightSrc_w = Reg(UInt(log2Ceil(n).W))

  io.out.aw.bits := Mux(
    w_state === s_idle,
    thisReq_w.bits,
    io.in(inflightSrc_w).aw.bits
  )

  io.out.aw.valid := thisReq_w.valid && (w_state === s_idle)
  io.in.map(_.aw.ready := false.B)
  thisReq_w.ready := io.out.aw.ready && (w_state === s_idle)

  io.out.w.valid := io.in(inflightSrc_w).w.valid
  io.out.w.bits := io.in(inflightSrc_w).w.bits
  io.in.map(_.w.ready := false.B)
  io.in(inflightSrc_w).w.ready := io.out.w.ready

  io.in.map(_.b.bits := io.out.b.bits)
  io.in.map(_.b.valid := false.B)
  (io.in(inflightSrc_w).b, io.out.b) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  switch(w_state) {
    is(s_idle) {
      when(thisReq_w.fire) {
        inflightSrc_w := inputArb_w.io.chosen
        io.in(inputArb_w.io.chosen).aw.ready := true.B
        when(thisReq_w.valid) { w_state := s_writeResp }
      }
    }
    is(s_writeResp) {
      when(io.out.b.fire) { w_state := s_idle }
    }
  }
}
