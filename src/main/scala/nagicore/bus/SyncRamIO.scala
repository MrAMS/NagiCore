package nagicore.bus

import chisel3._
import chisel3.util._

class SyncRamIO(dataBits: Int, depth: Int) extends Bundle{
    val addr    = Input(UInt(log2Up(depth).W))
    val din     = Input(UInt(dataBits.W))
    val dout    = Output(UInt(dataBits.W))
    val en      = Input(Bool())
    val we      = Input(Bool())
}

class SyncRam(dataBits: Int, depth: Int, channle: Int=1) extends Module{
    val io = IO(new SyncRamIO(dataBits, depth))
    
    val regs = Reg(Vec(depth, UInt(dataBits.W)))
    io.dout := regs(io.addr)
    when(io.en){
        when(io.we){
            regs(io.addr) := io.din
        }
    }
}


