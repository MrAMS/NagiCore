package nagicore.loongarch

import chisel3._
import chisel3.util._
import nagicore.bus.{AXI4Slave, AXI4IO, SyncRam, SyncRamType, SyncRamIO}
import nagicore.loongarch.Config
import nagicore.loongarch.stages._

class Core extends Module with Config{
    val io = IO(new Bundle{})

    val preif_part = Module(new PREIF)
    val if_part = Module(new IF)
    val id_part = Module(new ID)
    val ex_part = Module(new EX)
    val mem_part = Module(new MEM)
    val wb_part = Module(new WB)

    preif_part.io.preif2if <> if_part.io.preif2if
    if_part.io.if2id <> id_part.io.if2id
    id_part.io.id2ex <> ex_part.io.id2ex
    ex_part.io.ex2preif <> preif_part.io.ex2preif
    ex_part.io.ex2id <> id_part.io.ex2id
    ex_part.io.ex2mem <> mem_part.io.ex2mem
    mem_part.io.mem2id <> id_part.io.mem2id
    mem_part.io.mem2wb <> wb_part.io.mem2wb
    wb_part.io.wb2id <> id_part.io.wb2id
    wb_part.io.stall_all := false.B

    val isram_ctrl = Module(new AXI4Slave(XLEN, XLEN, 1.toLong<<XLEN, 8))
    val dsram_ctrl = Module(new AXI4Slave(XLEN, XLEN, 1.toLong<<XLEN, 8))
    if_part.io.isram <> isram_ctrl.io.axi
    mem_part.io.dsram <> dsram_ctrl.io.axi
    val isram = Module(new SyncRam(XLEN, 1.toLong<<XLEN, SyncRamType.DPIC))
    val dsram = Module(new SyncRam(XLEN, 1.toLong<<XLEN, SyncRamType.DPIC))
    isram_ctrl.io.sram <> isram.io
    dsram_ctrl.io.sram <> dsram.io
}

class CoreNSCSCC extends Module with Config{
    val RAM_DEPTH = 0x400000/4
    val io = IO(new Bundle{
        val isram = Flipped(new SyncRamIO(32, RAM_DEPTH))
        val dsram = Flipped(new SyncRamIO(32, RAM_DEPTH))
    })

    val preif_part = Module(new PREIF)
    val if_part = Module(new IF)
    val id_part = Module(new ID)
    val ex_part = Module(new EX)
    val mem_part = Module(new MEM)
    val wb_part = Module(new WB)

    preif_part.io.preif2if <> if_part.io.preif2if
    if_part.io.if2id <> id_part.io.if2id
    id_part.io.id2ex <> ex_part.io.id2ex
    ex_part.io.ex2preif <> preif_part.io.ex2preif
    ex_part.io.ex2id <> id_part.io.ex2id
    ex_part.io.ex2mem <> mem_part.io.ex2mem
    mem_part.io.mem2id <> id_part.io.mem2id
    mem_part.io.mem2wb <> wb_part.io.mem2wb
    wb_part.io.wb2id <> id_part.io.wb2id
    wb_part.io.stall_all := false.B

    val isram_ctrl = Module(new AXI4Slave(XLEN, XLEN, RAM_DEPTH, 32))
    val dsram_ctrl = Module(new AXI4Slave(XLEN, XLEN, RAM_DEPTH, 32))

    if_part.io.isram <> isram_ctrl.io.axi
    mem_part.io.dsram <> dsram_ctrl.io.axi
    isram_ctrl.io.sram <> io.isram
    dsram_ctrl.io.sram <> io.dsram
}
