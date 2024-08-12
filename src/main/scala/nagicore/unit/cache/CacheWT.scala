package nagicore.unit.cache

import chisel3.VecInit._
import chisel3._
import chisel3.util._
import nagicore.bus._
import chisel3.util.random.LFSR
import nagicore.utils.isPowerOf2
import nagicore.GlobalConfg
import nagicore.unit.RingBuff

class CacheWTIOFrontBits[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: () => T) extends Bundle{
    val valid     = Bool()
    val addr      = UInt(addrBits.W)
    val we        = Bool()
    val wmask     = UInt((dataBits/8).W)
    val size      = UInt(2.W)
    val wdata     = UInt(dataBits.W)
    val uncache   = Bool()
    val pipedata  = pipedataT()
}

class CacheWTIOFront[T <: Bundle](addrBits: Int, dataBits: Int, pipedataT: () => T) extends Bundle{
    val bits        = Input(new CacheWTIOFrontBits[T](addrBits, dataBits, pipedataT))
    val stall       = Output(Bool())
}

class CacheWTIOBackBits[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val valid       = Bool()
    val rdata       = UInt(dataBits.W)
    // 整个缓存块，注意uncahce访问时，结果无意义
    val rline       = Vec(blockWords, UInt(dataBits.W))
    val pipedata    = pipedataT()
    // for debug
    val addr        = UInt(addrBits.W)
    val size        = UInt(2.W)
    val wdata       = UInt(dataBits.W)
    val wmask       = UInt((dataBits/8).W)
}

class CacheWTIOBack[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val bits        = Output(new CacheWTIOBackBits[T](addrBits, dataBits, blockWords, pipedataT))
    val stall       = Input(Bool())
}

class CacheWTIO[T <: Bundle](addrBits: Int, dataBits: Int, blockWords: Int, pipedataT: () => T) extends Bundle{
    val front = new CacheWTIOFront[T](addrBits, dataBits, pipedataT)
    val back = new CacheWTIOBack[T](addrBits, dataBits, blockWords, pipedataT)
}

/**
  * 单周期写通型Cache，一拍写入到WriteBuffer中
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
class CacheWT[T <: Bundle](addrBits: Int, dataBits: Int, ways: Int, sets: Int, blockWords: Int, writeBuffLen: Int,
                        pipedataT: () => T,
                        replaceT: CacheReplaceType.CacheReplaceType=CacheReplaceType.Random,
                        debug_id: Int=0) extends Module{
    require(isPowerOf2(ways))
    require(isPowerOf2(dataBits))

    val io = IO(new Bundle{
        val axi = new AXI4IO(addrBits, dataBits)
        val cmd = new CacheWTIO(addrBits, dataBits, blockWords, pipedataT)
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
    axi_w_agent.io.cmd.in := DontCare
    axi_w_agent.io.cmd.in.req := false.B
    val axi_r_agent = Module(new AXI4ReadAgent(addrBits, dataBits, beats_len))
    axi_r_agent.io.axi.ar <> io.axi.ar
    axi_r_agent.io.axi.r <> io.axi.r
    axi_r_agent.io.cmd.in := DontCare
    axi_r_agent.io.cmd.in.req := false.B

    class WriteInfo extends Bundle{
        val addr    = UInt(addrBits.W)
        val size    = UInt(2.W)
        val wmask   = UInt((dataBits/8).W)
        val wdata   = UInt(dataBits.W)
    }
    val write_buff = Module(new RingBuff(()=>new WriteInfo, writeBuffLen, 1, debug_id))
    write_buff.io.push := false.B
    write_buff.io.pop := false.B
    write_buff.io.wdata := DontCare
    write_buff.io.clear := false.B

    val random_way = LFSR(16)(log2Up(ways)-1, 0)
    val lru = Reg(Vec(sets, UInt(log2Up(ways).W)))

    val active_way = Reg(UInt(log2Up(ways).W))

    val data_bank = Seq.fill(ways)(Seq.fill(num_word)(Module(new Ram(dataBits, sets, RamType.RAM_1CYC))))
    val data_bank_io = VecInit(data_bank.map(t => VecInit(t.map(_.io))))
    val tag_v = Seq.fill(ways)(Module(new Ram(len_tag+1, sets, RamType.RAM_1CYC)))
    val tag_v_io = VecInit(tag_v.map(_.io))
    val addr_idx = io.cmd.front.bits.addr(len_idx+len_word+len_byte-1, len_word+len_byte)

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



    // stage1 pipeline registers
    val preg = RegEnable(io.cmd.front.bits, pipego)
    
    // // stage2 pipeline registers
    // val preg2 = RegEnable(preg, pipego)
    object StageState extends ChiselEnum {
        //  0       1               2           3               4           5                   6
        val lookup, replaceWait,    replace,    replaceWrite,   replaceEnd, uncacheReadWait,    uncacheRead,
        //  7           8
            uncacheEnd, waitWriteBuff = Value
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
    
    val data_ok = (
        // 当hit read/write 或者 uncache write且write_buffer未满 的时候，可以连续命中，单周期返回
        (state === StageState.lookup && ((!preg.we && hit) || (preg.we && hit && !write_buff.io.full) || (preg.we && preg.uncache && !write_buff.io.full)) ) ||
        // 替换完成
        (state === StageState.replaceEnd) ||
        // uncache访问
        (state === StageState.uncacheEnd) ||
        // 成功写入WBUFF
        (state === StageState.waitWriteBuff && !write_buff.io.full)
    )
    pipego := (data_ok ||
        // 无效命令
        !preg.valid) &&
        // 下一级无阻塞请求
        !io.cmd.back.stall



    val addr_reg = preg.addr
    // TODO: VIPT, let TLB pass in real tag
    val addr_tag_reg = addr_reg(addrBits-1, len_idx+len_word+len_byte)
    val addr_idx_reg = addr_reg(len_idx+len_word+len_byte-1, len_word+len_byte)
    val addr_word_reg = if(len_word!=0) addr_reg(len_word+len_byte-1, len_byte) else 0.U

    val rdatas_hit = WireDefault(fill(blockWords)(0.U(dataBits.W)))
    val rdatas_replace = RegInit(fill(blockWords)(0.U(dataBits.W)))
    val rdata_uncache = RegInit(0.U(dataBits.W))

    io.cmd.front.stall := !pipego
    io.cmd.back.bits.valid := data_ok && preg.valid
//    io.master.back.bits.rdata := Mux(state_s2 === Stage2State.replaceEnd, rdata_replace, rdata_hit)
    io.cmd.back.bits.rdata := MuxCase(rdatas_hit(addr_word_reg), Seq(
        (state === StageState.replaceEnd) -> rdatas_replace(addr_word_reg),
        (state === StageState.uncacheEnd) -> rdata_uncache
    ))
    io.cmd.back.bits.rline := Mux(state === StageState.replaceEnd, rdatas_replace,
        rdatas_hit
    )
    io.cmd.back.bits.pipedata := preg.pipedata

    io.cmd.back.bits.addr := addr_reg
    io.cmd.back.bits.size := preg.size
    io.cmd.back.bits.wdata := preg.wdata
    io.cmd.back.bits.wmask := preg.wmask


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
        dpic_perf_cache.io.valid := preg.valid && state === StageState.lookup && !io.cmd.back.stall
        dpic_perf_cache.io.id := debug_id.U
        dpic_perf_cache.io.access_type := Cat(preg.uncache, hit)
    }


    def writeRAM(io: RamIO, addr: UInt, data: UInt) = {
        io.addr := addr
        io.en := true.B
        io.re := false.B
        io.we := true.B
        io.din := data
    }

    // 当WriteBuff和AXIW都写完了之后，才能保证读到的是正确的
    val axi_ok_to_read = write_buff.io.empty && axi_w_agent.io.cmd.out.ready && axi_r_agent.io.cmd.out.ready

    def launch_uncahe_read() = {
        axi_r_agent.io.cmd.in.req := true.B
        axi_r_agent.io.cmd.in.addr := preg.addr
        axi_r_agent.io.cmd.in.size := preg.size
        axi_r_agent.io.cmd.in.len := 0.U
        state := StageState.uncacheRead
    }

    def lanuch_replace_read() = {
        axi_r_agent.io.cmd.in.req := true.B
        axi_r_agent.io.cmd.in.addr := addr_tag_reg ## addr_idx_reg ## 0.U((len_word+len_byte).W)
        axi_r_agent.io.cmd.in.len := (blockWords-1).U
        axi_r_agent.io.cmd.in.size := log2Up(dataBits).U
    }

    val hit_way = PriorityEncoder(hits)
    // 当连续hit wr/ww同一Cache行时，需要前递
    val hit_rw_bypass_need = bypass_addr === Cat(addr_tag_reg, addr_idx_reg)
    val rdatas_real = tabulate(blockWords){
        i => Mux(hit_rw_bypass_need && bypass_valid(i),
            bypass_val(i),
            rdatas(hit_way)(i)
        )
    }
    val wdata_real = Cat((0 until (dataBits/8)).reverse.map(i =>
        Mux(preg.wmask(i),
            preg.wdata(i*8+7, i*8),
            rdatas_real(addr_word_reg)(i*8+7, i*8)
        )))

    def write_write_buff() = {
        write_buff.io.push := true.B
        write_buff.io.wdata.addr := addr_reg
        write_buff.io.wdata.size := preg.size
        write_buff.io.wdata.wdata := wdata_real
        write_buff.io.wdata.wmask := Fill(dataBits/8, 1.U)
    }

    switch(state){
        is(StageState.lookup){
            when(preg.valid){
                when(hit){
                    if(replaceT == CacheReplaceType.LRU){
                        lru(addr_idx_reg) := ~hit_way
                    }
                    when(preg.we){
                        // 命中写
                        writeRAM(data_bank_io(hit_way)(addr_word_reg), addr_idx_reg, wdata_real)
                        bypass_addr := Cat(addr_tag_reg, addr_idx_reg)
                        bypass_valid(addr_word_reg) := true.B
                        bypass_val(addr_word_reg) := wdata_real
                        when(!write_buff.io.full){
                            when(!io.cmd.back.stall){ // 防止后级阻塞时，造成多余写入
                                write_write_buff()
                            }
                        }.otherwise{
                            state := StageState.waitWriteBuff
                        }
                    }otherwise{
                        // 命中读
                        rdatas_hit := rdatas_real
                    }
                    state := StageState.lookup
                }.otherwise{
                    when(preg.uncache){
                        when(preg.we){
                            when(!write_buff.io.full){
                                when(!io.cmd.back.stall){ // 防止后级阻塞时，造成多余写入
                                    write_write_buff()
                                }
                            }.otherwise{
                                state := StageState.waitWriteBuff
                            }
                        }.otherwise{
                            when(axi_ok_to_read){
                                launch_uncahe_read()
                            }.otherwise{
                                state := StageState.uncacheReadWait
                            }
                        }
                        // or just state_s2 := Stage2State.uncacheWait
                    }.otherwise{
                        val active_way_wire = Wire(chiselTypeOf(active_way))
                        if(replaceT == CacheReplaceType.LRU){
                            active_way_wire := lru(addr_idx_reg)
                        }else{
                            active_way_wire := random_way
                        }
                        active_way := active_way_wire
                        when(axi_ok_to_read){
                            lanuch_replace_read()
                            state := StageState.replace
                        }.otherwise{
                            state := StageState.replaceWait
                        }
                    }
                }
            }
        }
        is(StageState.replaceWait){
            when(axi_ok_to_read){
                lanuch_replace_read()
                state := StageState.replace
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

                writeRAM(data_bank_io(active_way)(word_i), addr_idx_reg, wdata_active)
                bypass_val(word_i) := wdata_active

                rdatas_replace(word_i) := wdata_active

                when(axi_r_agent.io.cmd.out.last){
                    state := StageState.replaceWrite
                    writeRAM(tag_v_io(active_way), addr_idx_reg, addr_tag_reg ## 1.U(1.W))
                    bypass_addr := Cat(addr_tag_reg, addr_idx_reg)
                }
            }
        }
        is(StageState.replaceWrite){
            when(!write_buff.io.full){
                write_buff.io.push := true.B
                write_buff.io.wdata.addr := preg.addr
                write_buff.io.wdata.size := preg.size
                write_buff.io.wdata.wdata := bypass_val(addr_word_reg)
                write_buff.io.wdata.wmask := Fill(dataBits/8, 1.U)
                state := StageState.replaceEnd
            }
        }
        // 等待下一级准备好接受并且成功写入到WriteBuff
        is(StageState.replaceEnd){
            when(!io.cmd.back.stall){
                state := StageState.lookup

                for(i <- 0 until blockWords){
                    bypass_valid(i) := true.B
                }
            }
        }
        is(StageState.uncacheReadWait){
            when(axi_ok_to_read){
                launch_uncahe_read()
            }
        }
        is(StageState.uncacheRead){
            // 等待代理空闲并且WriteBuff为空
            when(axi_r_agent.io.cmd.out.ready){
                rdata_uncache := axi_r_agent.io.cmd.out.rdata
                state := StageState.uncacheEnd
            }
        }
        is(StageState.uncacheEnd){
            // 等待下一级准备好接受
            when(!io.cmd.back.stall){
                state := StageState.lookup
            }
        }

        is(StageState.waitWriteBuff){
            when(!write_buff.io.full){
                write_write_buff()

                state := StageState.lookup
            }
        }
    }

    when(!write_buff.io.empty){
        when(axi_w_agent.io.cmd.out.ready){
            axi_w_agent.io.cmd.in.req := true.B
            axi_w_agent.io.cmd.in.addr := write_buff.io.rdatas(0).addr
            axi_w_agent.io.cmd.in.len := 0.U
            axi_w_agent.io.cmd.in.size := write_buff.io.rdatas(0).size
            axi_w_agent.io.cmd.in.wdata(0) := write_buff.io.rdatas(0).wdata
            axi_w_agent.io.cmd.in.wmask(0) := write_buff.io.rdatas(0).wmask

            write_buff.io.pop := true.B
        }
    }
}