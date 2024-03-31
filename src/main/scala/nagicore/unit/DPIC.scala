package nagicore.unit
import chisel3._
import chisel3.util._

class DPIC_SRAM(addr_width: Int, data_width: Int) extends BlackBox(Map("ADDR_WIDTH" -> addr_width, "DATA_WIDTH" -> data_width)) with HasBlackBoxResource{
    val io = IO(new Bundle{
        val clk     = Input(Clock())
        val rst     = Input(Bool())
        val en      = Input(Bool())
        val wmask   = Input(UInt(log2Ceil(data_width).W))
        val addr    = Input(UInt(addr_width.W))
        val wdata   = Input(UInt(data_width.W))
        val rdata   = Output(UInt(data_width.W))
    })
    addResource("/sv/DPIC_SRAM.sv")
}

class DPIC_DIFF_GPR(gpr_num: Int, data_bits: Int) extends BlackBox with HasBlackBoxResource{
    val io = IO(new Bundle{
        val clk     = Input(Clock())
        val rst     = Input(Bool())
        val wen     = Input(Bool())
        val id      = Input(UInt(log2Ceil(gpr_num).W))
        val wdata   = Input(UInt(data_bits.W))
    })
    addResource("/sv/DPIC_DIFF_GPR")
}