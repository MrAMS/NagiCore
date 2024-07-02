package nagicore.unit

import chisel3._
import chisel3.util._
import os.stat
import nagicore.loongarch.CtrlFlags.ldType.bu

// class InstrsBuffIOFrontBits(addrBits:Int, dataBits: Int) extends Bundle{

// }

// class InstrsBuffIOFront(addrBits:Int, dataBits: Int) extends Bundle{
//     val bits    = Input(new InstrsBuffIOFrontBits(addrBits, dataBits))
// }

// class InstrsBuffIOBackBits(addrBits:Int, dataBits: Int) extends Bundle{
//     val valid = Output(Bool())

// }

// class InstrsBuffIOBack(addrBits:Int, dataBits: Int) extends Bundle{
//     val bits    = Output(new InstrsBuffIOBackBits(addrBits, dataBits))
// }

class InstrsBuffCacheBundle extends Bundle{
    val new_trans = Bool()
}

class InstrsBuffIO(addrBits:Int, dataBits: Int, cacheBlockWords: Int) extends Bundle{
    val in = Input(new Bundle {
        // 是否需要清空缓存并指定新的预取开始地址
        val new_trans = Bool()
        // 新的预取开始地址
        val trans_addr = UInt(addrBits.W)
        // 从缓存中读一个数据
        val fetch = Bool()
    })
    val cache = Flipped(new CachePipedIO(addrBits, dataBits, cacheBlockWords, ()=>new InstrsBuffCacheBundle))
    val out = Output(new Bundle {
        val busy    = Output(Bool())
        val instr   = Output(UInt(dataBits.W))
    })
}

/**
  * 指令预取,下一个周期读出预取指令
  *
  * @param addrBits
  * @param dataBits
  * @param cacheBlockWords 每个Cache Block有多少个dataBits
  * @param blockLen 缓存多少个Cache Block，必须是2的幂次
  */
class InstrsBuff(addrBits:Int, dataBits: Int, cacheBlockWords: Int, blockLen: Int) extends Module{
    require((blockLen&(blockLen-1))==0)
    val io = IO(new InstrsBuffIO(addrBits, dataBits, cacheBlockWords))
    val buff = RegInit(VecInit(Seq.fill(blockLen*cacheBlockWords)(0.U(dataBits.W))))
    val buff_head = RegInit(0.U(log2Up(blockLen*cacheBlockWords).W))
    val buff_tail = RegInit(0.U(log2Up(blockLen*cacheBlockWords).W))
    val buff_valid = RegInit(VecInit.fill(blockLen*cacheBlockWords)(false.B))
    val empty = !buff_valid(buff_head)
    val full = buff_valid(buff_tail + (cacheBlockWords-1).U)

    val addr = RegInit(0.U(addrBits.W))

    object State extends ChiselEnum {
        //  0       1           2               3
        val idle,   wait_cache, wait_new_trans, continue_read = Value
    }

    val state = RegInit(State.idle)


    io.out.busy := io.in.new_trans || (state === State.wait_new_trans) || (state === State.wait_cache) || (state === State.continue_read && empty)
    io.out.instr := buff(buff_head)


    io.cache.front.bits.pipedata.new_trans := false.B
    io.cache.front.bits.valid := true.B

    when(!io.cache.front.stall && state =/= State.wait_cache){
        addr := addr + (cacheBlockWords*dataBits/8).U
    }

    val new_trans_offset = RegInit(0.U(log2Up(cacheBlockWords).W))
    val word_len = log2Ceil(cacheBlockWords)
    val byte_len = log2Ceil(dataBits/8)

    def cache_new_trans()={
        addr := io.in.trans_addr(addrBits-1, word_len+byte_len) ## 0.U((word_len+byte_len).W)
        io.cache.front.bits.pipedata.new_trans := true.B
        io.cache.front.bits.valid := true.B
        state := State.wait_new_trans
        new_trans_offset := io.in.trans_addr(word_len+byte_len-1, byte_len)
    }

    switch(state){
        is(State.idle){
            io.cache.front.bits.valid := false.B
            when(io.in.new_trans){
                cache_new_trans()
            }
        }
        is(State.wait_cache){
            when(!io.cache.front.stall){
                cache_new_trans()
            }
        }
        is(State.wait_new_trans){
            when(io.cache.back.bits.pipedata_s2.new_trans && io.cache.back.bits.valid){
                state := State.continue_read
            }
        }
        is(State.continue_read){
            when(io.in.new_trans){
                buff_head := 0.U
                buff_tail := 0.U
                buff_valid := VecInit.fill(blockLen*cacheBlockWords)(false.B)
                when(!io.cache.front.stall){
                    cache_new_trans()
                }otherwise{
                    // 如果Cache阻塞中，需要先等Cache空闲
                    state := State.wait_cache
                }
            }.otherwise{
                when(io.cache.back.bits.valid && !full){
                    for(i <- 0 until cacheBlockWords){
                        buff(buff_tail+i.U) := io.cache.back.bits.rline(i)
                        buff_valid(buff_tail+i.U) := (i.U >= new_trans_offset)
                    }
                    buff_tail := buff_tail + cacheBlockWords.U
                    buff_head := buff_head + new_trans_offset
                    new_trans_offset := 0.U
                }
                when(io.in.fetch && !io.out.busy){
                    buff_head := buff_head + 1.U
                    buff_valid(buff_head) := false.B
                }
            }
        }
    }

    io.cache.front.bits.addr := addr
    io.cache.front.bits.size := log2Up(dataBits/8).U
    io.cache.front.bits.uncache := false.B
    io.cache.front.bits.wdata := DontCare
    io.cache.front.bits.wmask := 0.U
    io.cache.back.stall := full




    // io.front.stall := state === State.wait_new_trans || state === State.wait_last_trans || state === State.idle

    // io.back.bits.valid := !empty && !io.front.bits.new_trans


    // io.cache.front.bits.pipedata.new_trans := false.B
    // io.cache.front.bits.valid := true.B



    // switch(state){
    //     is(State.idle){
    //         io.cache.front.bits.valid := false.B
    //         buff_head := 0.U
    //         buff_tail := 0.U
    //         addr := (0x1c000000).U
    //         state := State.wait_last_trans
    //         io.cache.front.bits.pipedata.new_trans := true.B
    //     }
    //     is(State.wait_last_trans){
    //         when(io.cache.back.bits.pipedata_s2.new_trans){
    //             state := State.wait_new_trans
    //         }
    //     }
    //     is(State.wait_new_trans){
    //         when(io.cache.back.bits.valid && !full)
    //         {
    //             buff(buff_tail) := io.cache.back.bits.rdata
    //             buff_tail := buff_tail + 1.U
    //             state := State.continue_read
    //         }
    //     }
    //     is(State.continue_read){
    //         when(io.front.bits.new_trans){
    //             buff_head := 0.U
    //             buff_tail := 0.U
    //             addr := io.front.bits.trans_addr
    //             state := State.wait_new_trans
    //             io.cache.front.bits.pipedata.new_trans := true.B
    //         }.otherwise{
    //             when(io.cache.back.bits.valid && !full){
    //                 buff(buff_tail) := io.cache.back.bits.rdata
    //                 buff_tail := buff_tail + 1.U
    //             }
    //             when(io.front.bits.fetch && !empty && !io.back.stall){
    //                 buff_head := buff_head + 1.U
    //             }
    //         }
    //     }
    // }


}