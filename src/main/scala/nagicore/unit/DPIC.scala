package nagicore.unit
import chisel3._
import chisel3.util._
import nagicore.bus.SyncRamIO


// TODO delete
// class DPIC_SRAM(addr_width: Int, data_width: Int) extends BlackBox(Map("ADDR_WIDTH" -> addr_width, "DATA_WIDTH" -> data_width)) with HasBlackBoxResource{
//     val io = IO(new Bundle {
//         val clk     = Input(Clock())
//         val rst     = Input(Bool())
//         val data    = new CachePipedIO(addr_width, data_width, () => new SyncRamIO(addr_width, data_width))
//     })
//     addResource("/sv/DPIC_SRAM.sv")
//     addResource("/sv/DPIC_TYPES_DEFINE.sv")
// }

class DPIC_UPDATE_GPR(gpr_num: Int, data_width: Int) extends BlackBox(Map("GPR_NUM" -> gpr_num, "DATA_WIDTH" -> data_width)) with HasBlackBoxResource{
    val io = IO(new Bundle{
        val clk     = Input(Clock())
        val rst     = Input(Bool())
        val id      = Input(UInt(log2Ceil(gpr_num).W))
        val wdata   = Input(UInt(data_width.W))
    })
    addResource("/sv/DPIC_TYPES_DEFINE.sv")
    addResource("/sv/DPIC_UPDATE_GPR.sv")
}

class DPIC_UPDATE_PC(data_width: Int) extends BlackBox(Map("DATA_WIDTH" -> data_width)) with HasBlackBoxResource{
    val io = IO(new Bundle{
        val clk     = Input(Clock())
        val rst     = Input(Bool())
        val wen     = Input(Bool())
        val pc      = Input(UInt(data_width.W))
    })
    addResource("/sv/DPIC_TYPES_DEFINE.sv")
    addResource("/sv/DPIC_UPDATE_PC.sv")
}

class DPIC_TRACE_MEM(addr_width: Int, data_width: Int) extends BlackBox(Map("ADDR_WIDTH" -> addr_width, "DATA_WIDTH" -> data_width)) with HasBlackBoxResource{
    val io = IO(new Bundle{
        val clk     = Input(Clock())
        val rst     = Input(Bool())
        val valid   = Input(Bool())
        val addr    = Input(UInt(addr_width.W))
        val wmask   = Input(UInt((data_width/8).W))
        val size    = Input(UInt(2.W))
        val data    = Input(UInt(data_width.W))
    })
    addResource("/sv/DPIC_TYPES_DEFINE.sv")
    addResource("/sv/DPIC_TRACE_MEM.sv")
}