package nagicore.unit

import chisel3._
import chisel3.util._
import nagicore.GlobalConfg

class RingBuffIO[T <: Bundle](dataT: ()=> T) extends Bundle{
    val full    = Output(Bool())
    val empty   = Output(Bool())

    val push    = Input(Bool())
    val wdata   = Input(dataT())
    val pop     = Input(Bool())
    val rdata   = Output(dataT())
    val clear   = Input(Bool())
}

class RingBuff[T <: Bundle](dataT: ()=> T, len: Int, id: Int=0) extends Module{
    require((len&(len-1))==0)
    val io = IO(new RingBuffIO(dataT))
    val buff = Reg(Vec(len, dataT()))
    val buff_head = RegInit(0.U(log2Up(len).W))
    val buff_tail = RegInit(0.U(log2Up(len).W))
    val buff_valid = RegInit(VecInit.fill(len)(false.B))
    val empty = !buff_valid(buff_head)
    val full = buff_valid(buff_tail)

    io.empty := empty
    io.full := full
    io.rdata := buff(buff_head)

    when(io.push && !full){
        buff_tail := buff_tail + 1.U
        buff(buff_tail) := io.wdata
        buff_valid(buff_tail) := true.B
    }

    when(io.pop && !empty){
        buff_head := buff_head + 1.U
        buff_valid(buff_head) := false.B
    }

    when(io.clear){
        buff_head := 0.U
        buff_tail := 0.U
        buff_valid := VecInit.fill(len)(false.B)
    }

    if(GlobalConfg.SIM){
        val dpic_perf_instrs_buff = Module(new DPIC_PERF_BUFF)
        dpic_perf_instrs_buff.io.clk := clock
        dpic_perf_instrs_buff.io.rst := reset
        dpic_perf_instrs_buff.io.id := id.U
        dpic_perf_instrs_buff.io.head := buff_head
        dpic_perf_instrs_buff.io.tail := buff_tail
        dpic_perf_instrs_buff.io.full := full
        dpic_perf_instrs_buff.io.reload := io.clear
    }

}
