package nagicore.unit.ip.axi_corssbar

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

import nagicore.bus.AXI4IO
import nagicore.bus.AXI4AIO
import nagicore.bus.AXI4WIO
import nagicore.bus.AXI4BIO
import nagicore.bus.AXI4RIO

sealed abstract class Axi4RW(val v: Int)
object Axi4RW {
  final case object RW extends Axi4RW(v = 0)
  final case object RO extends Axi4RW(v = 1)
  final case object WO extends Axi4RW(v = 2)
}

class AXI4FullAIO(addrBits: Int, idBits: Int, userBits: Int) extends AXI4AIO(addrBits){
    val lock = Output(UInt(1.W))
    val cache = Output(UInt(4.W))
    val prot = Output(UInt(3.W))
    val qos = Output(UInt(4.W))
    val region = Output(UInt(4.W))
    val user = Output(UInt(userBits.W))
}

class AXI4FullWIO(dataBits: Int, userBits: Int) extends AXI4WIO(dataBits){
    val user = Output(UInt(userBits.W))
}

class AXI4FullBIO(userBits: Int) extends AXI4BIO{
    val user    = Input(UInt(userBits.W))
}

class AXI4FullRIO(dataBits: Int, userBits: Int) extends AXI4RIO(dataBits){
    val user    = Input(UInt(userBits.W))
}

class AXI4FullIO(addrBits: Int, dataBits: Int, idBits: Int, userBits: Int) extends Bundle{
    val ar      = new AXI4FullAIO(addrBits, idBits, userBits)
    val r       = new AXI4FullRIO(dataBits, userBits)
    val aw      = new AXI4FullAIO(addrBits, idBits, userBits)
    val w       = new AXI4FullWIO(dataBits, userBits)
    val b       = new AXI4FullBIO(userBits)
}

class clk_rst extends Bundle{
    val aclk    = Input(Bool())
    val aresetn = Input(Bool())
    val srst    = Input(Bool())
}

class axicb_crossbar_top(addr_width: Int, data_width: Int, id_width: Int,
    // mastrs: [(priority, rw)]
    masters: List[(Int, Axi4RW)],
    slaves: List[(Long, Long)]
    ) extends BlackBox(Map(
        "AXI_ADDR_W" -> addr_width,
        "AXI_ID_W" -> id_width,
        "AXI_DATA_W" -> data_width,
        // "MST_NB" -> masters.length,
        "MST_NB" -> 4,
        "SLV_NB" -> 4,
        // "SLV_NB" -> slaves.length,
        // 1-AXI4 0-AXI4-lite
        "AXI_SIGNALING" -> 1,
        "MST0_PRIORITY" -> masters.lift(0).getOrElse(0, Axi4RW.RW)._2.v,
        "MST1_PRIORITY" -> masters.lift(1).getOrElse(0, Axi4RW.RW)._2.v,
        "MST2_PRIORITY" -> masters.lift(2).getOrElse(0, Axi4RW.RW)._2.v,
        "MST3_PRIORITY" -> masters.lift(3).getOrElse(0, Axi4RW.RW)._2.v,

        "SLV0_START_ADDR"   -> slaves.lift(0).getOrElse((0L, 4095L))._1,
        "SLV0_END_ADDR"     -> slaves.lift(0).getOrElse((0L, 4095L))._2,
        "SLV1_START_ADDR"   -> slaves.lift(1).getOrElse((0L, 4095L))._1,
        "SLV1_END_ADDR"     -> slaves.lift(1).getOrElse((0L, 4095L))._2,
        "SLV2_START_ADDR"   -> slaves.lift(2).getOrElse((0L, 4095L))._1,
        "SLV2_END_ADDR"     -> slaves.lift(2).getOrElse((0L, 4095L))._2,
        "SLV3_START_ADDR"   -> slaves.lift(3).getOrElse((0L, 4095L))._1,
        "SLV3_END_ADDR"     -> slaves.lift(3).getOrElse((0L, 4095L))._2,
    )) with HasBlackBoxResource{
    val io = IO(new Bundle{
        val aclk    = Input(Bool())
        val aresetn = Input(Bool())
        val srst    = Input(Bool())
        val slv = Vec(4, Flipped(new AXI4FullIO(addr_width, data_width, id_width, 1)))
        val slv_clk = Vec(4, new clk_rst)
        val mst = Vec(4, new AXI4FullIO(addr_width, data_width, id_width, 1))
        val mst_clk = Vec(4, new clk_rst)
    })
    addResource("/sv/axi_crossbar/axicb_crossbar_top.sv")
    addResource("/sv/axi_crossbar/axicb_checker.sv")
    addResource("/sv/axi_crossbar/axicb_mst_if.sv")
    addResource("/sv/axi_crossbar/axicb_mst_switch.sv")
    addResource("/sv/axi_crossbar/axicb_pipeline.sv")
    addResource("/sv/axi_crossbar/axicb_round_robin_core.sv")
    addResource("/sv/axi_crossbar/axicb_round_robin.sv")
    addResource("/sv/axi_crossbar/axicb_scfifo_ram.sv")
    addResource("/sv/axi_crossbar/axicb_scfifo.sv")
    addResource("/sv/axi_crossbar/axicb_slv_if.sv")
    addResource("/sv/axi_crossbar/axicb_slv_switch.sv")
    addResource("/sv/axi_crossbar/axicb_switch_top.sv")
}

// class AXI4XBar(addr_width: Int, data_width: Int,
// // mastrs: [(priority, rw)]
// masters_cfg: List[(Int, Axi4RW)],
// slaves_cfg: List[(Long, Long)])
// extends Module{
//     val io = IO(new Bundle {
//         val slaves = Vec(slaves_cfg.length, new AXI4IO(addr_width, data_width))
//         val masters = Vec(masters_cfg.length, Flipped(new AXI4IO(addr_width, data_width)))
//     })
//     val ip = Module(new axicb_crossbar_top(addr_width, data_width, 8, masters_cfg, slaves_cfg))
//     ip.io.mst <> DontCare
//     ip.io.mst_clk <> DontCare
//     ip.io.slv <> DontCare
//     ip.io.slv_clk <> DontCare
//     for(i <- 0 until slaves_cfg.length){
//         // ip.io.mst(i) <> DontCare
//         ip.io.mst(i).ar.viewAsSupertype(new AXI4AIO(addr_width)) <> io.slaves(i).ar
//         ip.io.mst(i).aw.viewAsSupertype(new AXI4AIO(addr_width)) <> io.slaves(i).aw
//         ip.io.mst(i).r.viewAsSupertype(new AXI4RIO(data_width)) <> io.slaves(i).r
//         ip.io.mst(i).w.viewAsSupertype(new AXI4WIO(data_width)) <> io.slaves(i).w
//         ip.io.mst(i).b.viewAsSupertype(new AXI4BIO) <> io.slaves(i).b
//         ip.io.mst_clk(i).aclk := clock.asBool
//         ip.io.mst_clk(i).srst := reset.asBool
//         ip.io.mst_clk(i).aresetn := !reset.asBool
//         // io.masters(i).r.bits <> ip.io.mst(i).r
//         // io.masters(i).aw.bits <> ip.io.mst(i).aw
//         // io.masters(i).w.bits <> ip.io.mst(i).w
//         // io.masters(i).b.bits <> ip.io.mst(i).b
//     }
//     for(i <- 0 until masters_cfg.length){
//         ip.io.slv(i).ar.viewAsSupertype(new AXI4AIO(addr_width)) <> io.masters(i).ar
//         // id = {MASTER_ID, ID} ? let all slaves transfer to master 0
//         ip.io.slv(i).ar.id := "h10".U
//         ip.io.slv(i).aw.viewAsSupertype(new AXI4AIO(addr_width)) <> io.masters(i).aw
//         ip.io.slv(i).aw.id := "h10".U
//         ip.io.slv(i).r.viewAsSupertype(new AXI4RIO(data_width)) <> io.masters(i).r
//         ip.io.slv(i).w.viewAsSupertype(new AXI4WIO(data_width)) <> io.masters(i).w
//         ip.io.slv(i).b.viewAsSupertype(new AXI4BIO) <> io.masters(i).b

//         ip.io.slv_clk(i).aclk := clock.asBool
//         ip.io.slv_clk(i).srst := false.B
//         ip.io.slv_clk(i).aresetn := !reset.asBool
//     }
//     ip.io.aclk := clock.asBool
//     ip.io.srst := false.B
//     ip.io.aresetn := !reset.asBool
//     // ip.io.mst_clk.map(x => {

//     // })
//     // ip.io.slv_clk.map(x => {
//     //     x.aclk := clock.asBool
//     //     x.srst := reset.asBool
//     //     x.aresetn := !reset.asBool
//     // })
// }

