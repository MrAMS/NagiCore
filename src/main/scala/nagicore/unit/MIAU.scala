package nagicore.unit

import chisel3._
import chisel3.util._
import nagicore.bus._

/**
  * Max In Array Unit
  * 求数组中的最大值，为龙芯杯个人赛决赛设计
  *
  * @param addrBits
  * @param dataBits
  */
class MIAU(addrBits:Int, dataBits: Int, idBits: Int) extends Module{
    val io = IO(new Bundle{
        val cmd = Flipped(new AXI4IO(addrBits, dataBits, idBits))
        val mem = new AXI4IO(addrBits, dataBits, idBits)
    })

    val MXR = RegInit(0.U(dataBits.W))
    val CMPR = RegInit(0.U(dataBits.W))
    val FIR = RegInit(0.U(dataBits.W))

    val raddr = Reg(UInt(addrBits.W))
    val rid   = Reg(UInt(idBits.W))
    val rlen  = Reg(UInt(8.W))

    val rs_idle :: rs_r :: Nil = Enum(2)
    val rs = RegInit(rs_idle)

    when(io.cmd.ar.fire){
        raddr := io.cmd.ar.bits.addr
        rid := io.cmd.ar.bits.id
        rlen := io.cmd.ar.bits.len
        rs := rs_r
    }

    when(io.cmd.r.fire){
        raddr := raddr + (dataBits/8).U
        when(rlen === 0.U){
            rs := rs_idle
        }otherwise{
            rlen := rlen - 1.U
        }
    }
    io.cmd.ar.ready := rs === rs_idle
    io.cmd.r.valid := rs === rs_r
    io.cmd.r.bits.id := rid
    io.cmd.r.bits.last := rlen === 0.U
    io.cmd.r.bits.resp := 0.U
    io.cmd.r.bits.data := FIR

    io.cmd.aw <> DontCare
    io.cmd.w <> DontCare
    io.cmd.b <> DontCare

    val axi_w_agent = Module(new AXI4WriteAgent(addrBits, dataBits, 1))
    axi_w_agent.io.axi.aw <> io.mem.aw
    axi_w_agent.io.axi.w <> io.mem.w
    axi_w_agent.io.axi.b <> io.mem.b
    axi_w_agent.io.cmd.in <> DontCare
    axi_w_agent.io.cmd.in.req := false.B

    object State extends ChiselEnum {
        val idle        = Value(1.U)
        val read        = Value(2.U)
        val cmp         = Value(4.U)
        val write       = Value(8.U)
        val end         = Value(16.U)
    }

    val state = RegInit(State.idle)

    val mem_addr = RegInit("h80400000".U(dataBits.W))

    val axi_r_agent = Module(new AXI4ReadAgent(addrBits, dataBits, 1))
    axi_r_agent.io.axi.ar <> io.mem.ar
    axi_r_agent.io.axi.r <> io.mem.r
    axi_r_agent.io.cmd.in.addr := mem_addr
    axi_r_agent.io.cmd.in.len := 0.U
    axi_r_agent.io.cmd.in.size := log2Up(dataBits).U
    axi_r_agent.io.cmd.in.req := false.B

    when(state === State.idle){
        state := State.read
        axi_r_agent.io.cmd.in.req := true.B
    }

    when(state === State.read && axi_r_agent.io.cmd.out.ready){
        // printf(cf"state read at ${mem_addr}\n")
        state := State.cmp
        CMPR := axi_r_agent.io.cmd.out.rdata
        mem_addr := mem_addr + (dataBits/8).U
    }
    when(state === State.cmp){
        when(CMPR > MXR){
            MXR := CMPR
        }
        when(mem_addr === "h80700000".U){
            state := State.write
        }.otherwise{
            state := State.read
            axi_r_agent.io.cmd.in.req := true.B
        }
    }
    when(state === State.write){
        printf(cf"mia finish\n")
        axi_w_agent.io.cmd.in.req := true.B
        axi_w_agent.io.cmd.in.addr := "h80700000".U
        axi_w_agent.io.cmd.in.len := 0.U
        axi_w_agent.io.cmd.in.size := log2Up(dataBits).U
        axi_w_agent.io.cmd.in.wdata(0) := MXR
        axi_w_agent.io.cmd.in.wmask(0) := Fill(dataBits/4, "b1".U)
        state := State.end
    }
    when(state === State.end && axi_w_agent.io.cmd.out.ready){
        FIR := 233.U
    }

}