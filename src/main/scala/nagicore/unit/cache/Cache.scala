package nagicore.unit.cache

import chisel3.VecInit._
import chisel3._
import chisel3.util._
import nagicore.bus._
import chisel3.util.random.LFSR
import nagicore.utils.isPowerOf2
import nagicore.GlobalConfg

class CacheIOFrontBits[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: () => T) extends Bundle{
    val valid     = Bool()
    val addr      = UInt(addrBits.W)
    val wmask     = UInt((dataBits/8).W)
    val size      = UInt(2.W)
    val wdata     = UInt(dataBits.W)
    val uncache   = Bool()
    val pipedata  = pipedataT()
}

class CacheIOFront[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: () => T) extends Bundle{
    val bits        = Input(new CacheIOFrontBits[T](addrBits, dataBits, pipedataT))
    val stall       = Output(Bool())
}

class CacheIOBackBits[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val valid       = Bool()
    val rdata       = UInt(dataBits.W)
    // 整个缓存块，注意uncahce访问时，结果无意义
    val rline       = Vec(blockWords, UInt(dataBits.W))
    val pipedata    = pipedataT()
}

class CacheIOBack[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val bits        = Output(new CacheIOBackBits[T](addrBits, dataBits, blockWords, pipedataT))
    val stall       = Input(Bool())
}

class CacheIO[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val front = new CacheIOFront[T](addrBits, dataBits, pipedataT)
    val back = new CacheIOBack[T](addrBits, dataBits, blockWords, pipedataT)
}

/**
  * 阻塞式单周期Cache
  * @pipeline
  *                   stage0             stage1
                                         比较Tag，选择数据   字节选择，符号扩展
  * CONTROL SIGNAL---------------------->|preg|
  *                   |-------------|
  * ADDR------------> |  Cache RAM  |--->|rtag, rdata, ...|
  *                   |-------------|
  *
  * @param addrBits
  * @param dataBits
  * @param ways         相连数，必须为2的幂次
  * @param sets         行数
  * @param blockWords   块字个数
  * @param pipedataT    流水线其他信号
  */
class Cache[T <: Bundle](addrBits: Int, dataBits: Int, ways: Int, sets: Int, blockWords: Int, id: Int=0,
                         pipedataT: () => T,
                         replaceT: CacheReplaceType.CacheReplaceType=CacheReplaceType.Random) extends Module{
    require(isPowerOf2(ways))
    require(isPowerOf2(dataBits))

    val io = IO(new Bundle{
        val axi = new AXI4IO(addrBits, dataBits)
        val master = new CacheIO(addrBits, dataBits, blockWords, pipedataT)
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
    axi_w_agent.io.cmd.in.wdata := fill(blockWords)(0.U)
    axi_w_agent.io.cmd.in.wmask := fill(blockWords)(0.U)
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

    val data_bank = Seq.fill(ways)(Seq.fill(num_word)(Module(new Ram(dataBits, sets, RamType.RAM_1CYC))))
    val data_bank_io = VecInit(data_bank.map(t => VecInit(t.map(_.io))))
    val tag_v = Seq.fill(ways)(Module(new Ram(len_tag+1, sets, RamType.RAM_1CYC)))
    val tag_v_io = VecInit(tag_v.map(_.io))
    val dirty = Seq.fill(ways)(Module(new Ram(1, sets, RamType.RAM_1CYC)))
    val dirty_io = VecInit(dirty.map(_.io))
    val addr_idx = io.master.front.bits.addr(len_idx+len_word+len_byte-1, len_word+len_byte)

    // 流水使能信号
    val pipego = Wire(Bool())

    tag_v_io.foreach(io => {
        io.addr := addr_idx
        io.en := pipego
        io.re := true.B
        io.we := false.B
        io.din := 0.U
        io.wmask := Fill(len_tag+1, true.B)
    })
    data_bank_io.foreach(_.foreach(io => {
        io.addr := addr_idx
        io.en := pipego
        io.re := true.B
        io.we := false.B
        io.din := 0.U
        io.wmask := Fill(dataBits, true.B)
    }))
    dirty_io.foreach(io => {
        io.addr := addr_idx
        io.en := pipego
        io.re := true.B
        io.we := false.B
        io.din := 0.U
        io.wmask := Fill(1, true.B)
    })



    // stage1 pipeline registers
    val preg = RegEnable(io.master.front.bits, pipego)
    
    // // stage2 pipeline registers
    // val preg2 = RegEnable(preg, pipego)
    object StageState extends ChiselEnum {
        //  0       1          2        3           4            5            6
        val lookup, writeback, replace, replaceEnd, uncacheWait, uncacheRead, uncacheEnd = Value
    }
    // stage2 state
    val state = RegInit(StageState.lookup)
    val hit = Wire(Bool())

    val pipego_reg = pipego

    val rdatas = RegEnable(tabulate(ways){ i =>
            tabulate(num_word){ j =>
                data_bank_io(i)(j).dout
            }
        }, pipego_reg)
    val rtags = RegEnable(tabulate(ways){ i =>
            tag_v_io(i).dout(len_tag, 1)
        }, pipego_reg)
    val rvalid = RegEnable(tabulate(ways){ i =>
            tag_v_io(i).dout(0).asBool
        }, pipego_reg)
    val rdirty = RegEnable(tabulate(ways){ i =>
            dirty_io(i).dout.asBool
        }, pipego_reg)

    pipego :=
        (
        // 连续命中
        (state === StageState.lookup && hit) ||
        // 无效命令
        !preg.valid ||
        // 替换完成
        state === StageState.replaceEnd ||
        // uncache访问
        state === StageState.uncacheEnd
        ) &&
        // 下一级无阻塞请求
        !io.master.back.stall

    val addr_reg = preg.addr
    // TODO: VIPT, let TLB pass in real tag
    val addr_tag_reg = addr_reg(addrBits-1, len_idx+len_word+len_byte)
    val addr_idx_reg = addr_reg(len_idx+len_word+len_byte-1, len_word+len_byte)
    val addr_word_reg = addr_reg(len_word+len_byte-1, len_byte)

    val rdatas_hit = WireDefault(fill(blockWords)(0.U(dataBits.W)))
    val rdatas_replace = RegInit(fill(blockWords)(0.U(dataBits.W)))
    val rdata_uncache = RegInit(0.U(dataBits.W))

    io.master.front.stall := !pipego
    io.master.back.bits.valid := pipego && preg.valid
//    io.master.back.bits.rdata := Mux(state_s2 === Stage2State.replaceEnd, rdata_replace, rdata_hit)
    io.master.back.bits.rdata := MuxCase(rdatas_hit(addr_word_reg), Seq(
        (state === StageState.replaceEnd) -> rdatas_replace(addr_word_reg),
        (state === StageState.uncacheEnd) -> rdata_uncache
    ))
    io.master.back.bits.rline := Mux(state === StageState.replaceEnd, rdatas_replace,
        rdatas_hit
    )
    io.master.back.bits.pipedata := preg.pipedata

    val bypass_addr = Reg(UInt((len_tag+len_idx).W))
    val bypass_val = Reg(Vec(blockWords, UInt(dataBits.W)))
    val bypass_valid = Reg(Vec(blockWords, Bool()))
    for(i <- 0 until blockWords){
        bypass_valid(i) := 0.U
    }

    val hits = tabulate(ways){ i =>
        rtags(i) === addr_tag_reg && rvalid(i)
    }
    hit := hits.reduceTree(_||_) && !preg.uncache

    if(GlobalConfg.SIM){
        import  nagicore.unit.DPIC_PERF_CACHE
        val dpic_perf_cache = Module(new DPIC_PERF_CACHE)
        dpic_perf_cache.io.clk := clock
        dpic_perf_cache.io.rst := reset
        dpic_perf_cache.io.valid := preg.valid && state === StageState.lookup && !io.master.back.stall
        dpic_perf_cache.io.id := id.U
        dpic_perf_cache.io.access_type := Cat(preg.uncache, hit)
    }


    // 请求读缺失行，并开始替换Cache行
    def load_miss_lines() = {
        axi_r_agent.io.cmd.in.req := true.B
        axi_r_agent.io.cmd.in.addr := addr_tag_reg ## addr_idx_reg ## 0.U((len_word+len_byte).W)
        axi_r_agent.io.cmd.in.len := (blockWords-1).U
        axi_r_agent.io.cmd.in.size := log2Up(dataBits).U
        state := StageState.replace
    }

    def writeSyncRam(io: RamIO, addr: UInt, data: UInt) = {
        io.addr := addr
        io.en := true.B
        io.re := false.B
        io.we := true.B
        io.din := data
    }

    def wait_uncache_ready() = {
        when(preg.wmask.orR){
            when(axi_w_agent.io.cmd.out.ready){
                axi_w_agent.io.cmd.in.addr := preg.addr
                axi_w_agent.io.cmd.in.req := true.B
                axi_w_agent.io.cmd.in.wdata(0) := preg.wdata
                axi_w_agent.io.cmd.in.wmask(0) := preg.wmask
                axi_w_agent.io.cmd.in.size := preg.size
                axi_w_agent.io.cmd.in.len := 0.U
                state := StageState.uncacheEnd
            }
        }.otherwise{
            when(axi_w_agent.io.cmd.out.ready&&axi_r_agent.io.cmd.out.ready){
                axi_r_agent.io.cmd.in.req := true.B
                axi_r_agent.io.cmd.in.addr := preg.addr
                axi_r_agent.io.cmd.in.size := preg.size
                axi_r_agent.io.cmd.in.len := 0.U
                state := StageState.uncacheRead
            }
        }
    }

    switch(state){
        is(StageState.lookup){
            when(preg.valid){
                when(hit){
                    val hit_way = PriorityEncoder(hits)
                    if(replaceT == CacheReplaceType.LRU){
                        lru(addr_idx_reg) := ~hit_way
                    }
                    // 当连续hit wr/ww同一Cache行时，需要前递
                    val hit_rw_bypass_need = bypass_addr === Cat(addr_tag_reg, addr_idx_reg)
                    val rdatas_real = tabulate(blockWords){
                        i => Mux(hit_rw_bypass_need && bypass_valid(i),
                            bypass_val(i),
                            rdatas(hit_way)(i)
                        )
                    }
                    when(preg.wmask.orR){
                        // 命中写
                        val wdata = Cat((0 until (dataBits/8)).reverse.map(i =>
                            Mux(preg.wmask(i),
                                preg.wdata(i*8+7, i*8),
                                rdatas_real(addr_word_reg)(i*8+7, i*8)
                            )))
                        writeSyncRam(data_bank_io(hit_way)(addr_word_reg), addr_idx_reg, wdata)
                        bypass_addr := Cat(addr_tag_reg, addr_idx_reg)
                        bypass_valid(addr_word_reg) := true.B
                        bypass_val(addr_word_reg) := wdata
                        writeSyncRam(dirty_io(hit_way), addr_idx_reg, 1.U(1.W))
                    }otherwise{
                        // 命中读
                        rdatas_hit := rdatas_real
                    }
                    state := StageState.lookup
                }.otherwise{
                    when(preg.uncache){
                        wait_uncache_ready()
                        // or just state_s2 := Stage2State.uncacheWait
                    }.otherwise{
                        val active_way_wire = Wire(chiselTypeOf(active_way))
                        if(replaceT == CacheReplaceType.LRU){
                            active_way_wire := lru(addr_idx_reg)
                        }else{
                            active_way_wire := random_way
                        }
                        active_way := active_way_wire
                        when(rdirty(active_way_wire) && rvalid(active_way_wire)) {
                            state := StageState.writeback
                        }.otherwise {
                            load_miss_lines()
                        }
                    }
                }
            }
        }
        is(StageState.writeback){
            // 等待代理空闲
            when(axi_w_agent.io.cmd.out.ready){
                // 交给代理写入将要被替换的脏行
                val addr = rtags(active_way) ## addr_idx_reg ## 0.U((len_word+len_byte).W)
                axi_w_agent.io.cmd.in.addr := addr
                axi_w_agent.io.cmd.in.req := true.B
                axi_w_agent.io.cmd.in.wdata := rdatas(active_way)
                axi_w_agent.io.cmd.in.wmask := fill(blockWords)(1.U)
                axi_w_agent.io.cmd.in.size := log2Up(dataBits).U
                axi_w_agent.io.cmd.in.len := (blockWords-1).U

                load_miss_lines()
            }.otherwise{
                state := StageState.writeback
            }
        }
        is(StageState.replace){
            when(axi_r_agent.io.cmd.out.ready){
                val word_i = axi_r_agent.io.cmd.out.order
                val wdata_active = Mux(word_i === addr_word_reg,
                    Cat((0 until (dataBits/8)).reverse.map(i =>
                        Mux(preg.wmask(i),
                            preg.wdata(i*8+7, i*8),
                            axi_r_agent.io.cmd.out.rdata(i*8+7, i*8)
                        )
                    )),
                    axi_r_agent.io.cmd.out.rdata
                )

                writeSyncRam(data_bank_io(active_way)(word_i), addr_idx_reg, wdata_active)
                bypass_val(word_i) := wdata_active

                rdatas_replace(word_i) := wdata_active

                when(axi_r_agent.io.cmd.out.last){
                    state := StageState.replaceEnd
                    writeSyncRam(tag_v_io(active_way), addr_idx_reg, addr_tag_reg ## 1.U(1.W))
                    writeSyncRam(dirty_io(active_way), addr_idx_reg, 0.U(1.W))
                    bypass_addr := Cat(addr_tag_reg, addr_idx_reg)
                }
            }
        }
        // 等待下一级准备好接受
        is(StageState.replaceEnd){
            when(!io.master.back.stall){
                state := StageState.lookup
                for(i <- 0 until blockWords){
                    bypass_valid(i) := true.B
                }
            }
        }
        is(StageState.uncacheWait){
            wait_uncache_ready()
        }
        is(StageState.uncacheRead){
            // 等待代理空闲
            when(axi_r_agent.io.cmd.out.ready){
                rdata_uncache := axi_r_agent.io.cmd.out.rdata
                state := StageState.uncacheEnd
            }
        }
        is(StageState.uncacheEnd){
            // 等待下一级准备好接受
            when(!io.master.back.stall){
                state := StageState.lookup
            }
        }

    }
}