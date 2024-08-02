package nagicore.bus

import chisel3._
import chisel3.util._
import nagicore.unit.cache.CacheMemType.{RAM_2cyc, Value}
import nagicore.GlobalConfg

object RamType extends Enumeration {
    type RamType = Value
    val RAM_2CYC, RAM_1CYC, BRAM_1CYC, DPIC_2CYC, DPIC_1CYC = Value
}

class RamIO(width: Int, depth: Long) extends Bundle{
    val addr    = Input(UInt(log2Up(depth).W))
    val din     = Input(UInt(width.W))
    val dout    = Output(UInt(width.W))
    val en      = Input(Bool())
    val we      = Input(Bool())
    val re      = Input(Bool())
    val wmask   = Input(UInt((width/8).W))
}

/**
  * RAM
  * @note 
  * @param addrBits
  * @param dataBits
  * @param imp
  */
class Ram(width: Int, depth: Long, imp: RamType.RamType=RamType.RAM_2CYC) extends Module{
    val io = IO(new RamIO(width, depth))
    val addrBits = log2Up(depth)
    imp match {
        case RamType.DPIC_2CYC => {
            import nagicore.unit.DPIC_RAM_2CYC
            val sram = Module(new DPIC_RAM_2CYC(addrBits, width))
            sram.io.clk := clock
            sram.io.rst := reset
            sram.io.addr := io.addr
            sram.io.re := io.re
            sram.io.we := io.we
            sram.io.wdata := io.din
            sram.io.wmask := io.wmask
            sram.io.size := log2Up(width/8).U
            sram.io.en := io.en
            io.dout := sram.io.rdata
        }
        case RamType.DPIC_1CYC => {
            import nagicore.unit.DPIC_RAM_1CYC
            val sram = Module(new DPIC_RAM_1CYC(addrBits, width))
            sram.io.clk := clock
            sram.io.rst := reset
            sram.io.addr := io.addr
            sram.io.wdata := io.din
            sram.io.re := io.re
            sram.io.we := io.we
            sram.io.wmask := io.wmask
            sram.io.size := log2Up(width/8).U
            sram.io.en := io.en
            io.dout := sram.io.rdata
        }
        case RamType.RAM_1CYC | RamType.BRAM_1CYC => {
            if(imp==RamType.BRAM_1CYC && !GlobalConfg.SIM){
                Predef.println(s"Xilinx BlockRAM IP blk_mem_${width}_${depth} needed")
                class BlockRAMIP extends BlackBox{
                    override val desiredName = s"blk_mem_${width}_${depth}"
                    val io = IO(new Bundle {
                        val addra   = Input(UInt(addrBits.W))
                        val clka    = Input(Clock())
                        val dina    = Input(UInt(width.W))
                        val douta   = Output(UInt(width.W))
                        val ena     = Input(Bool())
                        val wea     = Input(Bool())
                    })
                }
                val bram = Module(new BlockRAMIP)
                bram.io.clka := clock
                bram.io.addra := io.addr
                bram.io.dina := io.din
                bram.io.wea := io.we
                bram.io.ena := io.en
                io.dout := bram.io.douta
            }else{
                if(width%8==0){
                    val bytes = width/8
                    val mem = Mem(depth, Vec(bytes, UInt(8.W)))
                    when(io.en&&io.we){
                        val wdata = VecInit.tabulate(bytes){
                            i => io.din(8*((bytes-1-i)+1)-1, 8*(bytes-1-i))
                        }
                        assert(Cat(io.wmask.asBools)===io.wmask)
                        mem.write(io.addr, wdata, io.wmask.asBools)
                    }
                    // WRITE_FIRST Mode
                    io.dout := Cat(mem.read(io.addr))
                }else{
                    val mem = Mem(depth, UInt(width.W))
                    when(io.en&&io.we){
                        mem.write(io.addr, io.din)
                    }
                    io.dout := mem.read(io.addr)
                }
            }
        }
        case _ => {
            /**
             * 两个周期的同步RAM
             * 当EN拉低时，不会写入任何数据，读数据将会保持在上一个状态；读后写时，将会继续读上一次读地址的数据
             * When inactive, no data is written to the RAM and the output bus remains in its previous state.
             * [NO_CHANGE Mode](https://docs.amd.com/r/en-US/am007-versal-memory/NO_CHANGE-Mode-DEFAULT)
            */
            val mem = Mem(depth, UInt(width.W))
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
            assert(io.wmask.andR)
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


