package nagicore.unit

import chisel3._
import chisel3.util._

class GPRIO(dataBits: Int, addrBits: Int, rchannel: Int) extends Bundle {
    val raddr   = Input(Vec(rchannel, UInt(addrBits.W)))
    val rdata   = Output(Vec(rchannel, UInt(dataBits.W)))
    val wen     = Input(Bool())
    val waddr   = Input(UInt(addrBits.W))
    val wdata   = Input(UInt(dataBits.W))
}

class GPR(dataBits: Int, regNum: Int, rchannel: Int) extends Module {
    val io = IO(new GPRIO(dataBits, log2Up(regNum), rchannel))
    val regs = Reg(Vec(regNum, UInt(dataBits.W)))
    // val regs = Mem(regNum, UInt(dataBits.W))
    for(i <- 0 until rchannel){
        io.rdata(i) := Mux(io.raddr(i) =/= 0.U, regs(io.raddr(i)), 0.U)
    }
    when(io.wen && io.waddr =/= 0.U){
        regs(io.waddr) := io.wdata
    }
}
