package nagicore.unit

import chisel3._
import chisel3.util._
import nagicore.GlobalConfg

class RingBuffIO[T <: Bundle](dataT: ()=> T, rchannel: Int) extends Bundle{
    val full    = Output(Bool())
    val empty   = Output(Bool())

    val push    = Input(Bool())
    val wdata   = Input(dataT())
    val pop     = Input(Bool())
    val popN    = Input(UInt(log2Up(rchannel).W))
    val rdatas  = Output(Vec(rchannel, dataT()))
    val rvalids = Output(Vec(rchannel, Bool()))
    val clear   = Input(Bool())
}

/**
  * 环形队列，多端口读，单端口写，读出多个时，需要拉高pop，并且传入popN指定读并弹出的数据个数
  * 注意，读出数据个数(popN+1)需要根据rvalids判断，不能超过有效的数据个数，模块不做检查
  *
  * @param dataT
  * @param len
  * @param rchannel
  * @param wchannel
  * @param id
  */
class RingBuff[T <: Bundle](dataT: ()=> T, len: Int, rchannel: Int, debug_id: Int) extends Module{
    require((len&(len-1))==0)
    val io = IO(new RingBuffIO(dataT, rchannel))
    val buff = Reg(Vec(len, dataT()))
    val buff_head = RegInit(0.U(log2Up(len).W))
    val buff_tail = RegInit(0.U(log2Up(len).W))
    val buff_valid = RegInit(VecInit.fill(len)(false.B))
    val empty = !buff_valid(buff_head)
    val full = buff_valid(buff_tail)

    io.empty := empty
    io.full := full
    for(i <- 0 until rchannel){
        io.rdatas(i) := buff(buff_head+i.U)
        io.rvalids(i) := buff_valid(buff_head+i.U)
    }

    when(io.clear){
        buff_head := 0.U
        buff_tail := 0.U
        for(i <- 0 until len)
            buff_valid(i) := false.B
    }.otherwise{
        when(io.push && !full){
            buff_tail := buff_tail + 1.U
            buff(buff_tail) := io.wdata
            buff_valid(buff_tail) := true.B
        }

        when(io.pop){
            for(i <- 0 until rchannel){
                when(io.popN === i.U){
                    buff_head := buff_head + (i+1).U
                    for(j <- 0 to i){
                        buff_valid(buff_head + j.U) := false.B
                    }
                }
            }
        }
    }



    if(GlobalConfg.SIM){
        val dpic_perf_instrs_buff = Module(new DPIC_PERF_BUFF)
        dpic_perf_instrs_buff.io.clk := clock
        dpic_perf_instrs_buff.io.rst := reset
        dpic_perf_instrs_buff.io.id := debug_id.U
        dpic_perf_instrs_buff.io.head := buff_head
        dpic_perf_instrs_buff.io.tail := buff_tail
        dpic_perf_instrs_buff.io.full := full
        dpic_perf_instrs_buff.io.reload := io.clear
    }

}
