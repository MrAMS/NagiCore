package nagicore.bus

import chisel3._
import chisel3.util._
import nagicore.unit.CacheMemType.Value

object SyncRamType extends Enumeration {
    type SyncRamType = Value
    val Reg, DPIC = Value
}

class SyncRamIO(dataBits: Int, depth: Int) extends Bundle{
    val addr    = Input(UInt(log2Up(depth).W))
    val din     = Input(UInt(dataBits.W))
    val dout    = Output(UInt(dataBits.W))
    val en      = Input(Bool())
    val we      = Input(Bool())
}

class SyncRam(dataBits: Int, depth: Int, imp: SyncRamType.SyncRamType=SyncRamType.Reg) extends Module{
    val io = IO(new SyncRamIO(dataBits, depth))
    val addrBits = 1 << depth
    imp match {
        case SyncRamType.DPIC => {
            class DPICRAM extends BlackBox(Map("ADDR_WIDTH" -> addrBits, "DATA_WIDTH" -> dataBits)) with HasBlackBoxResource{
                val io = IO(new Bundle {
                    val clk     = Input(Clock())
                    val rst     = Input(Bool())
                    val en      = Input(Bool())
                    val addr    = Input(UInt(addrBits.W))
                    val wmask   = Input(UInt(log2Up(addrBits).W))
                    val size    = Input(UInt(2.W))
                    val wdata   = Input(UInt(dataBits.W))
                    val rdata   = Output(UInt(dataBits.W))
                })
            }
            val sram = Module(new DPICRAM)
            sram.io.clk := clock
            sram.io.rst := reset
            sram.io.addr := io.addr
            sram.io.wdata := io.din
            sram.io.wmask := Fill(dataBits/8, 1.U(1.W))
            sram.io.size := log2Up(dataBits/8).U
            sram.io.en := io.en
            io.dout := sram.io.rdata
        }
        case _ => {
            val regs = Reg(Vec(depth, UInt(dataBits.W)))
            io.dout := ShiftRegister(regs(io.addr), 1, io.en)
            when(io.en&&io.we){
                regs(io.addr) := io.din
            }
        }
    }
}


