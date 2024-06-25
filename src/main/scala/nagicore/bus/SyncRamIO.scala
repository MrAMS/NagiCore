package nagicore.bus

import chisel3._
import chisel3.util._
import nagicore.unit.CacheMemType.Value

object SyncRamType extends Enumeration {
    type SyncRamType = Value
    val Reg, DPIC = Value
}

class SyncRamIO(addrBits: Int, dataBits: Int) extends Bundle{
    val addr    = Input(UInt(addrBits.W))
    val din     = Input(UInt(dataBits.W))
    val dout    = Output(UInt(dataBits.W))
    val en      = Input(Bool())
    val we      = Input(Bool())
}

class SyncRam(addrBits:Int, dataBits: Int, imp: SyncRamType.SyncRamType=SyncRamType.Reg) extends Module{
    val io = IO(new SyncRamIO(addrBits, dataBits))
    imp match {
        case SyncRamType.DPIC => {
            class DPIC_SRAM extends BlackBox(Map("ADDR_WIDTH" -> addrBits, "DATA_WIDTH" -> dataBits)) with HasBlackBoxResource{
                val io = IO(new Bundle {
                    val clk     = Input(Clock())
                    val rst     = Input(Bool())
                    val en      = Input(Bool())
                    val addr    = Input(UInt(addrBits.W))
                    val wmask   = Input(UInt(log2Up(dataBits).W))
                    val size    = Input(UInt(2.W))
                    val wdata   = Input(UInt(dataBits.W))
                    val rdata   = Output(UInt(dataBits.W))
                })
                addResource("/sv/DPIC_SRAM.sv")
                addResource("/sv/DPIC_TYPES_DEFINE.sv")
            }
            val sram = Module(new DPIC_SRAM)
            sram.io.clk := clock
            sram.io.rst := reset
            sram.io.addr := io.addr
            sram.io.wdata := io.din
            sram.io.wmask := Fill(dataBits/8, io.we)
            sram.io.size := log2Up(dataBits/8).U
            sram.io.en := io.en
            io.dout := sram.io.rdata
        }
        case _ => {
            val regs = Reg(Vec(1<<addrBits, UInt(dataBits.W)))
            // io.dout := regs(io.addr)
            // 写优先
            io.dout := Mux(RegNext(io.we), RegNext(io.din), RegNext(regs(io.addr)))
            when(io.en&&io.we){
                regs(io.addr) := io.din
            }
        }
    }
}


