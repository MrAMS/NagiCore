package nagicore.unit

import chisel3._
import chisel3.util._

class GPRIO(dataBits: Int, addrBits: Int, rchannel: Int, wchannel: Int) extends Bundle {
    val raddr   = Input(Vec(rchannel, UInt(addrBits.W)))
    val rdata   = Output(Vec(rchannel, UInt(dataBits.W)))
    val wen     = Input(Vec(wchannel, Bool()))
    val waddr   = Input(Vec(wchannel, UInt(addrBits.W)))
    val wdata   = Input(Vec(wchannel, UInt(dataBits.W)))
}

class GPR(dataBits: Int, regNum: Int, rchannel: Int, wchannel: Int) extends Module {
    val io = IO(new GPRIO(dataBits, log2Up(regNum), rchannel, wchannel))
    val regs = Reg(Vec(regNum, UInt(dataBits.W)))
    // val regs = Reg(VecInit.fill(regNum)(0.U(dataBits.W)))
    // val regs = Mem(regNum, UInt(dataBits.W))
    for(i <- 0 until rchannel){
        io.rdata(i) := Mux(io.raddr(i) =/= 0.U, regs(io.raddr(i)), 0.U)
    }
    for(i <- 0 until wchannel){
        when(io.wen(i) && io.waddr(i) =/= 0.U){
            regs(io.waddr(i)) := io.wdata(i)
        }
    }
}
