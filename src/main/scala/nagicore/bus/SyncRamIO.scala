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

/**
  * 两个周期的同步RAM
  * 当EN拉低时，不会写入任何数据，读数据将会保持在上一个状态；读后写时，将会继续读上一次读地址的数据
  * When inactive, no data is written to the RAM and the output bus remains in its previous state.
  * [NO_CHANGE Mode](https://docs.amd.com/r/en-US/am007-versal-memory/NO_CHANGE-Mode-DEFAULT)
  * @note 
  * @param addrBits
  * @param dataBits
  * @param imp
  */
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
                    val wmask   = Input(UInt((dataBits/8).W))
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
            val mem = Mem(1<<addrBits, UInt(dataBits.W))
            // val enable_read = io.en && !io.we
            // val rdata = mem.read(io.addr, enable_read)
            // io.dout := Mux(enable_read, rdata, RegEnable(rdata, enable_read))
            // val rdata = mem.read(io.addr, enable_read)
            // io.dout = 
            // io.dout := rdata
            // when(io.en&&io.we){
            //     mem.write(io.addr, io.din)
            // }
            val rdata = mem.read(RegEnable(io.addr, io.en && !io.we))
            io.dout := rdata
            when(io.en&&io.we){
                mem.write(io.addr, io.din)
            }
            /*
            val regs = Reg(Vec(1<<addrBits, UInt(dataBits.W)))
            // io.dout := regs(io.addr)
            /* NO_CHANGE Mode ref: https://docs.amd.com/r/en-US/am007-versal-memory/NO_CHANGE-Mode-DEFAULT */
            val rdata = regs(RegEnable(io.addr, io.en && !io.we))
            io.dout := rdata
            // io.dout := Mux(RegNext(io.we), RegNext(io.din), RegNext(regs(io.addr)))
            when(io.en&&io.we){
                regs(io.addr) := io.din
            }
            */
        }
    }
}


