package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4IO
import nagicore.loongarch.Config
import nagicore.loongarch.CtrlFlags
import nagicore.unit.{CachePiped, DPIC_SRAM}
import nagicore.utils.Flags

class mem2wbBits extends Bundle with Config{
    val instr       = UInt(XLEN.W)
    val alu_out     = UInt(XLEN.W)
    val rc          = UInt(GPR_LEN.W)
    val ld_type     = CtrlFlags.ldType()
    val rdata       = UInt(XLEN.W)
    val pc          = UInt(XLEN.W)

    val valid       = Bool()
}

class mem2wbIO extends Bundle with Config{
    val bits = Output(new mem2wbBits)
    val stall = Input(Bool())
}


class mem2idIO extends Bundle with Config{
    // effective signal
    val bypass1_rc   = Output(UInt(GPR_LEN.W))
    val bypass1_val  = Output(UInt(XLEN.W))
    val bypass2_rc   = Output(UInt(GPR_LEN.W))
    val bypass2_val  = Output(UInt(XLEN.W))
}

class MEM extends Module with Config{
    val io = IO(new Bundle {
        val ex2mem = Flipped(new ex2memIO())
        val mem2wb = new mem2wbIO()
        val mem2id = new mem2idIO()
        val dsram = new AXI4IO(XLEN, XLEN)
    })

    /**
     * MEM
     * -----Cache----
     * DMEM1 -> DMEM2
     */

    val dcache = Module(new CachePiped(XLEN, XLEN, 2, 512, 4, () => new mem2wbBits()))
    dcache.io.axi <> io.dsram
    
    // pipeline registers
    val preg = RegEnable(io.ex2mem.bits, !dcache.io.master.front.stall)

    val addr = preg.alu_out
    dcache.io.master.front.bits.addr := preg.alu_out
    dcache.io.master.front.bits.uncache := false.B // TODO
    dcache.io.master.front.bits.wdata := Flags.onehotMux(preg.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> Fill(XLEN/8, preg.rb_val(7, 0)),
        CtrlFlags.stType.h  -> Fill(XLEN/16, preg.rb_val(15, 0)),
        CtrlFlags.stType.w  -> preg.rb_val(31, 0),
    ))
    dcache.io.master.front.bits.size := Flags.onehotMux(preg.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> 0.U,
        CtrlFlags.stType.h  -> 1.U,
        CtrlFlags.stType.w  -> 2.U,
    )) | Flags.onehotMux(preg.ld_type, Seq(
        CtrlFlags.ldType.x  -> 0.U,
        CtrlFlags.ldType.b  -> 0.U,
        CtrlFlags.ldType.bu -> 0.U,
        CtrlFlags.ldType.h  -> 1.U,
        CtrlFlags.ldType.hu -> 1.U,
        CtrlFlags.ldType.w  -> 2.U,
    ))
    dcache.io.master.front.bits.wmask := Flags.onehotMux(preg.st_type, Seq(
        CtrlFlags.stType.x  -> 0.U,
        CtrlFlags.stType.b  -> ("b1".U<<addr(1, 0)),
        CtrlFlags.stType.h  -> ("b11".U<<(addr(1)##0.U(1.W))),
        CtrlFlags.stType.w  -> "b1111".U,
    ))
    dcache.io.master.front.bits.valid := (preg.ld_type =/= Flags.bp(CtrlFlags.ldType.x) || preg.st_type =/= Flags.bp(CtrlFlags.stType.x)) && preg.valid
    dcache.io.master.front.bits.pipedata.instr := preg.instr
    dcache.io.master.front.bits.pipedata.alu_out := preg.alu_out
    dcache.io.master.front.bits.pipedata.rc := preg.rc
    dcache.io.master.front.bits.pipedata.ld_type := preg.ld_type
    dcache.io.master.front.bits.pipedata.pc := preg.pc
    dcache.io.master.front.bits.pipedata.valid := DontCare
    dcache.io.master.front.bits.pipedata.rdata := DontCare

    assert((preg.st_type===Flags.bp(CtrlFlags.stType.h) && addr(0) === 0.U) || preg.st_type=/=Flags.bp(CtrlFlags.stType.h))
    assert((preg.st_type===Flags.bp(CtrlFlags.stType.w) && addr(1, 0) === 0.U) || preg.st_type=/=Flags.bp(CtrlFlags.stType.w))

    io.ex2mem.stall := dcache.io.master.front.stall

    io.mem2wb.bits <> dcache.io.master.back.bits.pipedata_s2
    io.mem2wb.bits.rdata := dcache.io.master.back.bits.rdata
    io.mem2wb.bits.valid := dcache.io.master.back.bits.valid



    io.mem2id.bypass1_rc := Mux(dcache.io.master.back.bits.pipedata_s1.valid, dcache.io.master.back.bits.pipedata_s1.rc, 0.U)
    io.mem2id.bypass1_val := dcache.io.master.back.bits.pipedata_s1.alu_out

    io.mem2id.bypass2_rc := Mux(dcache.io.master.back.bits.pipedata_s2.valid, dcache.io.master.back.bits.pipedata_s2.rc, 0.U)
    io.mem2id.bypass2_val := dcache.io.master.back.bits.pipedata_s2.alu_out
    

}