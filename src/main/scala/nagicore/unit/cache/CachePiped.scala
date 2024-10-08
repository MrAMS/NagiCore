package nagicore.unit.cache

import chisel3._
import chisel3.util._

import nagicore.bus._
import chisel3.util.random.LFSR
import nagicore.utils.isPowerOf2
import nagicore.GlobalConfg

class CachePipedIOFrontBits[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: () => T) extends Bundle{
    val valid     = Bool()
    val addr      = UInt(addrBits.W)
    val wmask     = UInt((dataBits/8).W)
    val size      = UInt(2.W)
    val wdata     = UInt(dataBits.W)
    val uncache   = Bool()
    val pipedata  = pipedataT()
}

class CachePipedIOFront[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: () => T) extends Bundle{
    val bits        = Input(new CachePipedIOFrontBits[T](addrBits, dataBits, pipedataT))
    val stall       = Output(Bool()) 
}

class CachePipedIOBackBits[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val valid       = Bool()
    val rdata       = UInt(dataBits.W)
    // 整个缓存块，注意uncahce访问时，结果无意义
    val rline       = Vec(blockWords, UInt(dataBits.W))
    val pipedata_s1 = pipedataT()
    val pipedata_s2 = pipedataT()
    // for debug
    val addr        = UInt(addrBits.W)
    val size        = UInt(2.W)
    val wdata       = UInt(dataBits.W)
    val wmask       = UInt((dataBits/8).W)
}

class CachePipedIOBack[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val bits        = Output(new CachePipedIOBackBits[T](addrBits, dataBits, blockWords, pipedataT))
    val stall       = Input(Bool())
}

class CachePipedIO[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val front = new CachePipedIOFront[T](addrBits, dataBits, pipedataT)
    val back = new CachePipedIOBack[T](addrBits, dataBits, blockWords, pipedataT)
}

/**
  * 阻塞式两级流水线Cache（已弃用）
  * @pipeline
  *                   stage1             stage2
  * EX                -> MEM1            -> MEM2           -> WB
  * 向SRAM发出地址      读SRAM和TLB         比较Tag，选择数据   字节选择，符号扩展
  *                       |preg1|------->|preg2|
  *         |-------------------|
  * ADDR--->| Cache RAM(2 cyc)  |------->|rtag, rdata ...|
  *         |-------------------|
  *
  * @param addrBits
  * @param dataBits
  * @param ways         相连数，必须为2的幂次
  * @param sets         行数
  * @param blockWords   块字个数
  * @param pipedataT    流水线其他信号
  */
class CachePiped[T <: Bundle](addrBits: Int, dataBits: Int, ways: Int, sets: Int, blockWords: Int, pipedataT: () => T, id: Int=0, replaceT: CacheReplaceType.CacheReplaceType=CacheReplaceType.Random) extends Module{
    require(isPowerOf2(ways))
    require(isPowerOf2(dataBits))

    val io = IO(new Bundle{
        val axi = new AXI4IO(addrBits, dataBits)
        val master = new CachePipedIO(addrBits, dataBits, blockWords, pipedataT)
    })
    
    // Block address
    // [ Tag | Index | Word | Byte ]
    //    |      |      |      |
    //   line   set     
    val len_byte = log2Ceil(dataBits/8)
    val num_word = blockWords
    val len_word = log2Ceil(num_word)
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
    val lru = Reg(Vec(sets, UInt(log2Up(ways).W)))

    val active_way = Reg(UInt(log2Up(ways).W))

    val data_bank = Seq.fill(ways)(Seq.fill(num_word)(Module(new CacheMem(dataBits, sets))))
    val data_bank_io = VecInit(data_bank.map(t => VecInit(t.map(_.io))))
    val tag_v = Seq.fill(ways)(Module(new CacheMem(len_tag+1, sets)))
    val tag_v_io = VecInit(tag_v.map(_.io))
    val dirty = Seq.fill(ways)(Module(new CacheMem(1, sets)))
    val dirty_io = VecInit(dirty.map(_.io))
    val addr_idx = io.master.front.bits.addr(len_idx+len_word+len_byte-1, len_word+len_byte)

    // 流水使能信号
    val pipego = Wire(Bool())

    tag_v_io.map(io => {
        io.addr := addr_idx
        io.en := pipego
        io.re := true.B
        io.we := false.B
        io.din := 0.U
        io.wmask := Fill(len_tag+1, true.B)
    })
    data_bank_io.map(_.map(io => {
        io.addr := addr_idx
        io.en := pipego
        io.re := true.B
        io.we := false.B
        io.din := 0.U
        io.wmask := Fill(dataBits, true.B)
    }))
    dirty_io.map(io => {
        io.addr := addr_idx
        io.en := pipego
        io.re := true.B
        io.we := false.B
        io.din := 0.U
        io.wmask := Fill(1, true.B)
    })



    // stage1 pipeline registers
    val preg1 = RegEnable(io.master.front.bits, pipego)
    
    // stage2 pipeline registers
    val preg2 = RegEnable(preg1, pipego)
    object Stage2State extends ChiselEnum {
        //  0       1          2        3           4            5            6
        val lookup, writeback, replace, replaceEnd, uncacheWait, uncacheRead, uncacheEnd = Value
    }
    // stage2 state
    val state_s2 = RegInit(Stage2State.lookup)
    val hit = Wire(Bool())

    val pipego_reg = pipego

    val rdatas = RegEnable(VecInit.tabulate(ways){ i =>
            VecInit.tabulate(num_word){ j =>
                data_bank_io(i)(j).dout
            }
        }, pipego_reg)
    val rtags = RegEnable(VecInit.tabulate(ways){ i =>
            tag_v_io(i).dout(len_tag, 1)
        }, pipego_reg)
    val rvalid = RegEnable(VecInit.tabulate(ways){ i =>
            tag_v_io(i).dout(0).asBool
        }, pipego_reg)
    val rdirty = RegEnable(VecInit.tabulate(ways){ i =>
            dirty_io(i).dout.asBool
        }, pipego_reg)

    pipego :=
        (
        // 连续命中
        (state_s2 === Stage2State.lookup && hit) ||
        // 无效命令
        !preg2.valid ||
        // 替换完成
        state_s2 === Stage2State.replaceEnd ||
        // uncache访问
        state_s2 === Stage2State.uncacheEnd
        ) &&
        // 下一级无阻塞请求
        !io.master.back.stall

    val addr_s2 = preg2.addr
    // TODO: VIPT, let TLB pass in real tag
    val addr_tag_s2 = addr_s2(addrBits-1, len_idx+len_word+len_byte)
    val addr_idx_s2 = addr_s2(len_idx+len_word+len_byte-1, len_word+len_byte)
    val addr_word_s2 = addr_s2(len_word+len_byte-1, len_byte)

    val rdatas_hit = WireDefault(VecInit.fill(blockWords)(0.U(dataBits.W)))
    val rdatas_replace = RegInit(VecInit.fill(blockWords)(0.U(dataBits.W)))
    val rdata_uncache = RegInit(0.U(dataBits.W))

    io.master.front.stall := !pipego
    io.master.back.bits.valid := pipego && preg2.valid
//    io.master.back.bits.rdata := Mux(state_s2 === Stage2State.replaceEnd, rdata_replace, rdata_hit)
    io.master.back.bits.rdata := MuxCase(rdatas_hit(addr_word_s2), Seq(
        (state_s2 === Stage2State.replaceEnd) -> rdatas_replace(addr_word_s2),
        (state_s2 === Stage2State.uncacheEnd) -> rdata_uncache
    ))
    io.master.back.bits.rline := Mux(state_s2 === Stage2State.replaceEnd, rdatas_replace,
        rdatas_hit
    )
    io.master.back.bits.pipedata_s1 := preg1.pipedata
    io.master.back.bits.pipedata_s2 := preg2.pipedata

    io.master.back.bits.addr := preg2.addr
    io.master.back.bits.size := preg2.size
    io.master.back.bits.wdata := preg2.wdata
    io.master.back.bits.wmask := preg2.wmask

    val bypass_addr = Reg(UInt((len_tag+len_idx).W))
    val bypass_val = Reg(Vec(blockWords, UInt(dataBits.W)))
    val bypass_valid = Reg(Vec(blockWords, Bool()))
    for(i <- 0 until blockWords){
        bypass_valid(i) := 0.U
    }

    val hits = VecInit.tabulate(ways){ i =>
        rtags(i) === addr_tag_s2 && rvalid(i)
    }
    hit := hits.reduceTree(_||_) && !preg2.uncache

    if(GlobalConfg.SIM){
        import  nagicore.unit.DPIC_PERF_CACHE
        val dpic_perf_cache = Module(new DPIC_PERF_CACHE)
        dpic_perf_cache.io.clk := clock
        dpic_perf_cache.io.rst := reset
        dpic_perf_cache.io.valid := preg2.valid && state_s2 === Stage2State.lookup && !io.master.back.stall
        dpic_perf_cache.io.id := id.U
        dpic_perf_cache.io.access_type := Cat(preg2.uncache, hit)
    }


    // 请求读缺失行，并开始替换Cache行
    def load_miss_lines() = {
        axi_r_agent.io.cmd.in.req := true.B
        axi_r_agent.io.cmd.in.addr := addr_tag_s2 ## addr_idx_s2 ## 0.U((len_word+len_byte).W)
        axi_r_agent.io.cmd.in.len := (blockWords-1).U
        axi_r_agent.io.cmd.in.size := log2Up(dataBits).U
        state_s2 := Stage2State.replace
    }

    def writeSyncRam(io: RamIO, addr: UInt, data: UInt) = {
        io.addr := addr
        io.en := true.B
        io.re := false.B
        io.we := true.B
        io.din := data
    }

    def wait_uncache_ready() = {
        when(preg2.wmask.orR){
            when(axi_w_agent.io.cmd.out.ready){
                axi_w_agent.io.cmd.in.addr := preg2.addr
                axi_w_agent.io.cmd.in.req := true.B
                axi_w_agent.io.cmd.in.wdata(0) := preg2.wdata
                axi_w_agent.io.cmd.in.wmask(0) := preg2.wmask
                axi_w_agent.io.cmd.in.size := preg2.size
                axi_w_agent.io.cmd.in.len := 0.U
                state_s2 := Stage2State.uncacheEnd
            }
        }.otherwise{
            when(axi_w_agent.io.cmd.out.ready&&axi_r_agent.io.cmd.out.ready){
                axi_r_agent.io.cmd.in.req := true.B
                axi_r_agent.io.cmd.in.addr := preg2.addr
                axi_r_agent.io.cmd.in.size := preg2.size
                axi_r_agent.io.cmd.in.len := 0.U
                state_s2 := Stage2State.uncacheRead
            }
        }
    }

    switch(state_s2){
        is(Stage2State.lookup){
            when(preg2.valid){
                when(hit){
                    val hit_way = PriorityEncoder(hits)
                    if(replaceT == CacheReplaceType.LRU){
                        lru(addr_idx_s2) := ~hit_way
                    }
                    // 当连续hit wr/ww同一Cache行时，需要前递
                    val hit_rw_bypass_need = bypass_addr === Cat(addr_tag_s2, addr_idx_s2)
                    val rdatas_real = VecInit.tabulate(blockWords){
                        i => Mux(hit_rw_bypass_need && bypass_valid(i),
                            bypass_val(i),
                            rdatas(hit_way)(i)
                        )
                    }
                    when(preg2.wmask.orR){
                        // 命中写
                        val wdata = Cat((0 until (dataBits/8)).reverse.map(i =>
                            Mux(preg2.wmask(i),
                                preg2.wdata(i*8+7, i*8),
                                rdatas_real(addr_word_s2)(i*8+7, i*8)
                            )))
                        writeSyncRam(data_bank_io(hit_way)(addr_word_s2), addr_idx_s2, wdata)
                        bypass_addr := Cat(addr_tag_s2, addr_idx_s2)
                        bypass_valid(addr_word_s2) := true.B
                        bypass_val(addr_word_s2) := wdata
                        writeSyncRam(dirty_io(hit_way), addr_idx_s2, 1.U(1.W))
                    }otherwise{
                        // 命中读
                        rdatas_hit := rdatas_real
                    }
                    state_s2 := Stage2State.lookup
                }.otherwise{
                    when(preg2.uncache){
                        wait_uncache_ready()
                        // or just state_s2 := Stage2State.uncacheWait
                    }.otherwise{
                        val active_way_wire = Wire(chiselTypeOf(active_way))
                        if(replaceT == CacheReplaceType.LRU){
                            active_way_wire := lru(addr_idx_s2)
                        }else{
                            active_way_wire := random_way
                        }
                        active_way := active_way_wire
                        when(rdirty(active_way_wire) && rvalid(active_way_wire)) {
                            state_s2 := Stage2State.writeback
                        }.otherwise {
                            load_miss_lines()
                        }
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
                bypass_val(word_i) := wdata_active

                rdatas_replace(word_i) := wdata_active

                when(axi_r_agent.io.cmd.out.last){
                    state_s2 := Stage2State.replaceEnd
                    writeSyncRam(tag_v_io(active_way), addr_idx_s2, addr_tag_s2 ## 1.U(1.W))
                    writeSyncRam(dirty_io(active_way), addr_idx_s2, 0.U(1.W))
                    bypass_addr := Cat(addr_tag_s2, addr_idx_s2)
                }
            }
        }
        // 等待下一级准备好接受
        is(Stage2State.replaceEnd){
            when(!io.master.back.stall){
                state_s2 := Stage2State.lookup
                for(i <- 0 until blockWords){
                    bypass_valid(i) := true.B
                }
            }
        }
        is(Stage2State.uncacheWait){
            wait_uncache_ready()
        }
        is(Stage2State.uncacheRead){
            // 等待代理空闲
            when(axi_r_agent.io.cmd.out.ready){
                rdata_uncache := axi_r_agent.io.cmd.out.rdata
                state_s2 := Stage2State.uncacheEnd
            }
        }
        is(Stage2State.uncacheEnd){
            // 等待下一级准备好接受
            when(!io.master.back.stall){
                state_s2 := Stage2State.lookup
            }
        }

    }
}