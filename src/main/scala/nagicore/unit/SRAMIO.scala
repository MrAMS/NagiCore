package nagicore.unit

import chisel3._
import chisel3.util._

class SRAM_IO(addr_width: Int, data_width: Int) extends Bundle{
    val req     = Input(Bool())
    val addr    = Input(UInt(addr_width.W))
    val wmask   = Input(UInt(log2Ceil(data_width).W))
    val size    = Input(UInt(2.W))
    val wdata   = Input(UInt(data_width.W))
    val rdata   = Output(UInt(data_width.W))
    val stall   = Output(Bool())
    val valid   = Output(Bool())
}


