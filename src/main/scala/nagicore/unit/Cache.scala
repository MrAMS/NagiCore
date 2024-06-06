package nagicore.unit

import chisel3._
import chisel3.util._

import nagicore.bus._
import chisel3.util.random.LFSR
import nagicore.utils.isPowerOf2

object CacheMemType extends Enumeration {
    type CacheMemType = Value
    val Reg, BlockRAM = Value
}

/**
  * CacheRAM 第二个周期返回读内容的同步RAM
  *
  * @param width
  * @param depth
  * @param imp
  */
class CacheMem(width: Int, depth: Int, imp: CacheMemType.CacheMemType=CacheMemType.Reg) extends Module{
    val io = IO(new SyncRamIO(width, depth))
    imp match {
        case _ => {
            val sram = Module(new SyncRam(width, depth))
            sram.io <> io
        }
    }
}

class CachePipedIOFrontBits[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: T) extends Bundle{
    val valid     = Bool()
    val addr      = UInt(addrBits.W)
    val wmask     = UInt((dataBits/8).W)
    val size      = UInt(2.W)
    val wdata     = UInt(dataBits.W)
    val uncache   = Bool()
    val pipedata  = pipedataT
}

class CachePipedIOFront[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: T) extends Bundle{
    val bits        = Input(new CachePipedIOFrontBits[T](addrBits, dataBits, pipedataT))
    val stall       = Output(Bool()) 
}

class CachePipedIOBackBits[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: T) extends Bundle{
    val valid       = Bool()
    val rdata       = UInt(dataBits.W)
    val pipedata    = pipedataT
}

class CachePipedIOBack[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: T) extends Bundle{
    val bits        = Output(new CachePipedIOBackBits[T](addrBits, dataBits, pipedataT))
    val stall       = Input(Bool())
}

class CachePipedIO[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: T) extends Bundle{
    val front = new CachePipedIOFront[T](addrBits, dataBits, pipedataT)
    val back = new CachePipedIOBack[T](addrBits, dataBits, pipedataT)
}

/**
  * 阻塞式两级流水线Cache
  * @pipeline
  *                    stage1             stage2
  * EX                -> MEM1            -> MEM2           -> WB
  * 向SRAM发出地址      读SRAM和TLB         比较Tag，选择数据   字节选择，符号扩展
  *                     preg1               preg2
  *
  * @param addrBits
  * @param dataBits
  * @param ways         相连数，必须为2的幂次
  * @param sets         行数
  * @param blockWords   块字个数
  * @param pipedataT    流水线其他信号
  */
class CachePiped[T <: Bundle](addrBits: Int, dataBits: Int, ways: Int, sets: Int, blockWords: Int, pipedataT: T) extends Module{
    require(isPowerOf2(ways))

    val io = IO(new Bundle{
        val axi = new AXI4IO(addrBits, dataBits)
        val master = new CachePipedIO(addrBits, dataBits, pipedataT)
    })
    
    // Block address
    // [ Tag | Index | Word | Byte ]
    //    |      |      |      |
    //   line   set     
    val len_byte = log2Ceil(dataBits/8)
    val num_word = blockWords
    val len_word = log2Ceil(num_word)
    assert(log2Ceil(blockWords*dataBits/8)==len_word+len_byte)
    val len_idx = log2Ceil(sets)
    val len_tag = addrBits - len_idx - len_word - len_byte
    val beats_len = num_word

    val axi_w_agent = Module(new AXI4WriteAgent(addrBits, dataBits, beats_len))
    axi_w_agent.io.axi.aw <> io.axi.aw
    axi_w_agent.io.axi.w <> io.axi.w
    axi_w_agent.io.axi.b <> io.axi.b
    axi_w_agent.io.cmd.in.req := false.B
    axi_w_agent.io.cmd.in.addr := 0.U
    axi_w_agent.io.cmd.in.wdata := VecInit.fill(blockWords)(0.U)
    axi_w_agent.io.cmd.in.wmask := VecInit.fill(blockWords)(0.U)
    axi_w_agent.io.cmd.in.size := 0.U
    axi_w_agent.io.cmd.in.len := 0.U

    val axi_r_agent = Module(new AXI4ReadAgent(addrBits, dataBits, beats_len))
    axi_r_agent.io.axi.ar <> io.axi.ar
    axi_r_agent.io.axi.r <> io.axi.r
    axi_r_agent.io.cmd.in.req := false.B
    axi_r_agent.io.cmd.in.addr := 0.U
    axi_r_agent.io.cmd.in.len := 0.U
    axi_r_agent.io.cmd.in.size := 0.U

    val random_way = LFSR(16)(log2Up(ways)-1, 0)
    val active_way = Reg(chiselTypeOf(random_way))

    val data_bank = Seq.fill(ways)(Seq.fill(num_word)(Module(new CacheMem(dataBits, sets))))
    val data_bank_io = VecInit(data_bank.map(t => VecInit(t.map(_.io))))
    val tag_v = Seq.fill(ways)(Module(new CacheMem(len_tag+1, sets)))
    val tag_v_io = VecInit(tag_v.map(_.io))
    val dirty = Seq.fill(ways)(Module(new CacheMem(1, sets)))
    val dirty_io = VecInit(dirty.map(_.io))
    val addr_idx = io.master.front.bits.addr(len_idx+len_word+len_byte-1, len_word+len_byte)
    tag_v_io.map(io => {
        io.addr := addr_idx
        io.en := true.B
        io.we := false.B
        io.din := 0.U
    })
    data_bank_io.map(_.map(io => {
        io.addr := addr_idx
        io.en := true.B
        io.we := false.B
        io.din := 0.U
    }))
    dirty_io.map(io => {
        io.addr := addr_idx
        io.en := true.B
        io.we := false.B
        io.din := 0.U
    })

    // 流水使能信号
    val pipego = Wire(Bool())

    // stage1 pipeline registers
    val preg1 = RegEnable(io.master.front.bits, pipego)
    
    // stage2 pipeline registers
    val preg2 = RegEnable(preg1, pipego)
    object Stage2State extends ChiselEnum {
        val lookup, writeback, replace, replaceEnd, uncache, uncacheEnd = Value
    }
    // stage2 state
    val state_s2 = RegInit(Stage2State.lookup)
    val hit = Wire(Bool())

    val rdatas = RegEnable(VecInit.tabulate(ways){ i =>
            VecInit.tabulate(num_word){ j =>
                data_bank_io(i)(j).dout
            }
        }, pipego)
    val rtags = RegEnable(VecInit.tabulate(ways){ i =>
            tag_v_io(i).dout(len_tag, 1)
        }, pipego)
    val rvalid = RegEnable(VecInit.tabulate(ways){ i =>
            tag_v_io(i).dout(0).asBool
        }, pipego)
    val rdirty = RegEnable(VecInit.tabulate(ways){ i =>
            dirty_io(i).dout.asBool
        }, pipego)
    
    pipego :=
        (
        // 连续命中
        (state_s2 === Stage2State.lookup && hit) ||
        // 无效命令
        !preg2.valid ||
        // 替换完成
        state_s2 === Stage2State.replaceEnd ||
        // 
        state_s2 === Stage2State.uncacheEnd
        ) &&
        // 下一级无阻塞请求
        !io.master.back.stall
    
    val rdata_hit = WireDefault(0.U(dataBits.W))
    val rdata_replace = Reg(UInt(dataBits.W))

    io.master.front.stall := !pipego
    io.master.back.bits.valid := pipego && preg2.valid
    io.master.back.bits.rdata := Mux(state_s2 === Stage2State.replaceEnd, rdata_replace, rdata_hit)
    io.master.back.bits.pipedata := preg2.pipedata

    val addr_s2 = preg2.addr
    // TODO: VIPT, let TLB pass in real tag
    val addr_tag_s2 = addr_s2(addrBits-1, len_idx+len_word+len_byte)
    val addr_idx_s2 = addr_s2(len_idx+len_word+len_byte-1, len_word+len_byte)
    val addr_word_s2 = addr_s2(len_word+len_byte-1, len_byte)

    // hit read after write bypass
    val hit_rw_bypass_valid_input = WireDefault(false.B)
    val hit_rw_bypass_valid = RegEnable(hit_rw_bypass_valid_input, pipego)
    val hit_rw_bypass_val_input = WireDefault(0.U(dataBits.W))
    val hit_rw_bypass_val = RegEnable(hit_rw_bypass_val_input, pipego)
    val hit_rw_bypass_addr_input = WireDefault(0.U(addrBits.W))
    val hit_rw_bypass_addr = RegEnable(hit_rw_bypass_addr_input, pipego)

    val hits = VecInit.tabulate(ways){ i =>
        rtags(i) === addr_tag_s2 && rvalid(i)
    }
    hit := hits.reduceTree(_||_) && !preg2.uncache


    // 请求读缺失行，并开始替换Cache行
    def load_miss_lines() = {
        axi_r_agent.io.cmd.in.req := true.B
        axi_r_agent.io.cmd.in.addr := addr_tag_s2 ## addr_idx_s2 ## 0.U((len_word+len_byte).W)
        axi_r_agent.io.cmd.in.len := (blockWords-1).U
        axi_r_agent.io.cmd.in.size := log2Up(dataBits).U
        state_s2 := Stage2State.replace
    }

    def writeSyncRam(io: SyncRamIO, addr: UInt, data: UInt) = {
        io.addr := addr
        io.en := true.B
        io.we := true.B
        io.din := data
    }

    switch(state_s2){
        is(Stage2State.lookup){
            when(preg2.valid){
                when(hit){
                    val hit_way = PriorityEncoder(hits)
                    when(preg2.wmask.orR){
                        // 命中写
                        val wdata = Cat((0 until (dataBits/8)).reverse.map(i =>
                            Mux(preg2.wmask(i),
                                preg2.wdata(i*8+7, i*8),
                                rdatas(hit_way)(addr_word_s2)(i*8+7, i*8)
                            )))
                        writeSyncRam(data_bank_io(hit_way)(addr_word_s2), addr_idx_s2, wdata)
                        hit_rw_bypass_valid_input := true.B
                        hit_rw_bypass_val_input := wdata
                        hit_rw_bypass_addr_input := addr_s2
                        writeSyncRam(dirty_io(hit_way), addr_idx_s2, 1.U(1.W))
                    }otherwise{
                        // 命中读
                        // 当上一个hit write同一个地址时，需要前递
                        val bypass = hit_rw_bypass_valid && hit_rw_bypass_addr === addr_s2
                        rdata_hit := Mux(bypass,
                            hit_rw_bypass_val,
                            rdatas(hit_way)(addr_word_s2)
                        )
                    }
                    state_s2 := Stage2State.lookup
                }.otherwise{
                    active_way := random_way
                    when(rdirty(random_way)&&rvalid(random_way)){
                        state_s2 := Stage2State.writeback
                    }.otherwise{
                        load_miss_lines()
                    }
                    
                }
            }
        }
        is(Stage2State.writeback){
            // 等待代理空闲
            when(axi_w_agent.io.cmd.out.ready){
                // 交给代理写入将要被替换的脏行
                val addr = rtags(active_way) ## addr_idx_s2 ## 0.U((len_word+len_byte).W)
                axi_w_agent.io.cmd.in.addr := addr
                axi_w_agent.io.cmd.in.req := true.B
                axi_w_agent.io.cmd.in.wdata := rdatas(active_way)
                axi_w_agent.io.cmd.in.wmask := VecInit.fill(blockWords)(1.U)
                axi_w_agent.io.cmd.in.size := log2Up(dataBits).U
                axi_w_agent.io.cmd.in.len := (blockWords-1).U
                
                load_miss_lines()
            }.otherwise{
                state_s2 := Stage2State.writeback
            }
        }
        is(Stage2State.replace){
            when(axi_r_agent.io.cmd.out.ready){
                val word_i = axi_r_agent.io.cmd.out.order
                val wdata_active = Mux(word_i === addr_word_s2, 
                    Cat((0 until (dataBits/8)).reverse.map(i =>
                        Mux(preg2.wmask(i),
                            preg2.wdata(i*8+7, i*8),
                            axi_r_agent.io.cmd.out.rdata(i*8+7, i*8)
                        )
                    )),
                    axi_r_agent.io.cmd.out.rdata
                )
                
                writeSyncRam(data_bank_io(active_way)(word_i), addr_idx_s2, wdata_active)

                when(word_i === addr_word_s2){
                    rdata_replace := wdata_active
                }

                when(axi_r_agent.io.cmd.out.last){
                    state_s2 := Stage2State.replaceEnd
                    writeSyncRam(tag_v_io(active_way), addr_idx_s2, addr_tag_s2 ## 1.U(1.W))
                    writeSyncRam(dirty_io(active_way), addr_idx_s2, 0.U(1.W))
                }
            }
        }
        // 等待下一级准备好接受
        is(Stage2State.replaceEnd){
            when(!io.master.back.stall){
                state_s2 := Stage2State.lookup
            }
        }
    }
}

    // // 发起Cache行读取
    // def load_lines() = {
        
    //     data_bank.map(_.map(m => {
    //         m.io.addr := addr_idx
    //         m.io.en := true.B
    //         m.io.we := false.B
    //     }))
    //     tag_v.map(m => {
    //         m.io.addr := addr_idx
    //         m.io.en := true.B
    //         m.io.we := false.B
    //     })
    //     dirty.map(m => {
    //         m.io.addr := addr_idx
    //         m.io.en := true.B
    //         m.io.we := false.B
    //     })
    // }
    // // 开始新一轮Cache访问
    // def new_cache_session() = {
    //     load_lines()
    //     preg2 := io.master.front
    //     state_s2 := Stage2State.fetchlines
    // }

        // is(Stage2State.idle){
        //     when(io.master.front.valid){
        //         new_cache_session()
        //     }otherwise{
        //         state_s2 := Stage2State.idle
        //     }
        // }
        // is(Stage2State.fetchlines){
        //     rdatas := VecInit.tabulate(ways){ i =>
        //         VecInit.tabulate(num_word){ j =>
        //             data_bank(i)(j).io.dout
        //         }
        //     }
        //     rtags := VecInit.tabulate(ways){ i =>
        //         tag_v(i).io.dout(len_tag, 1)
        //     }
        //     rvalid := VecInit.tabulate(ways){ i =>
        //         tag_v(i).io.dout(0).asBool
        //     }
        //     rdirty := VecInit.tabulate(ways){ i =>
        //         dirty(i).io.dout.asBool
        //     }
        //     state_s2 := Stage2State.lookup
        // }
