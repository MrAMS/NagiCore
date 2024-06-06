package nagicore.loongarch.stages

import chisel3._
import chisel3.util._
import nagicore.loongarch.Config
import nagicore.loongarch.CtrlFlags
import nagicore.unit.DPIC_SRAM
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
    val bypass_rc   = Output(UInt(GPR_LEN.W))
    val bypass_val  = Output(UInt(XLEN.W))
}

class MEM extends Module with Config{
    val io = IO(new Bundle {
        val ex2mem = Flipped(new ex2memIO())
        val mem2wb = new mem2wbIO()
        val mem2id = new mem2idIO()
    })

    val dmem = Module(new DPIC_SRAM(XLEN, XLEN))
    
    // pipeline registers
    val preg = RegEnable(io.ex2mem.bits, !io.mem2wb.stall && !dmem.io.data.back.stall)

    io.ex2mem.stall := io.mem2wb.stall
    io.mem2wb.bits.valid := preg.valid && !dmem.io.data.back.stall

    io.mem2wb.bits.instr := preg.instr

    io.mem2wb.bits.alu_out := preg.alu_out

    io.mem2wb.bits.rc := preg.rc

    io.mem2wb.bits.ld_type := preg.ld_type
    
    val addr = preg.alu_out
    dmem.io.clk := clock
    dmem.io.rst := reset
    // TODO
    dmem.io := DontCare
//    dmem.io.data.front.addr := addr
//    dmem.io.data.front.wdata := Flags.onehotMux(preg.st_type, Seq(
//        CtrlFlags.stType.x  -> 0.U,
//        CtrlFlags.stType.b  -> Fill(XLEN/8, preg.rb_val(7, 0)),
//        CtrlFlags.stType.h  -> Fill(XLEN/16, preg.rb_val(15, 0)),
//        CtrlFlags.stType.w  -> preg.rb_val(31, 0),
//    ))
//    dmem.io.data.front.valid := (preg.ld_type =/= Flags.bp(CtrlFlags.ldType.x) || preg.st_type =/= Flags.bp(CtrlFlags.stType.x)) && preg.valid
//    dmem.io.data.front.wmask := Flags.onehotMux(preg.st_type, Seq(
//        CtrlFlags.stType.x  -> 0.U,
//        CtrlFlags.stType.b  -> ("b1".U<<addr(1, 0)),
//        CtrlFlags.stType.h  -> ("b11".U<<(addr(1)##0.U(1.W))),
//        CtrlFlags.stType.w  -> "b1111".U,
//    ))
//    dmem.io.data.front.size := Flags.onehotMux(preg.st_type, Seq(
//        CtrlFlags.stType.x  -> 0.U,
//        CtrlFlags.stType.b  -> 0.U,
//        CtrlFlags.stType.h  -> 1.U,
//        CtrlFlags.stType.w  -> 2.U,
//    )) | Flags.onehotMux(preg.ld_type, Seq(
//        CtrlFlags.ldType.x  -> 0.U,
//        CtrlFlags.ldType.b  -> 0.U,
//        CtrlFlags.ldType.bu -> 0.U,
//        CtrlFlags.ldType.h  -> 1.U,
//        CtrlFlags.ldType.hu -> 1.U,
//        CtrlFlags.ldType.w  -> 2.U,
//    ))
//    assert((preg.st_type===Flags.bp(CtrlFlags.stType.h) && addr(0) === 0.U) || preg.st_type=/=Flags.bp(CtrlFlags.stType.h))
//    assert((preg.st_type===Flags.bp(CtrlFlags.stType.w) && addr(1, 0) === 0.U) || preg.st_type=/=Flags.bp(CtrlFlags.stType.w))
//    dmem.io.data.front.uncache := false.B
//
//    io.mem2wb.bits.rdata := dmem.io.data.back.rdata

    io.mem2wb.bits.pc := preg.pc

    io.mem2id.bypass_rc := Mux(preg.valid, preg.rc, 0.U)
    io.mem2id.bypass_val := preg.alu_out

    

}