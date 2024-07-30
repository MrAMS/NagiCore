package nagicore.loongarch

import chisel3._
import chisel3.util._
import nagicore.bus.{AXI4SRAM, AXI4IO, SyncRam, SyncRamType, SyncRamIO}
import nagicore.loongarch.Config
import nagicore.bus.AXI4Dummy
import nagicore.bus.AXI4XBar1toN
import nagicore.bus.AXI4XBarNto1
import nagicore.bus.AXI4SRAM_MultiCycs
// import stages7.{ID, PREIF, MEM, IF, WB, EX}
// import nscscc2024.{ID, PREIF, MEM, IF, WB, EX}

// class Core extends Module with Config{
//     val io = IO(new Bundle{})

//     val preif_part = Module(new PREIF)
//     val if_part = Module(new IF)
//     val id_part = Module(new ID)
//     val ex_part = Module(new EX)
//     val mem_part = Module(new MEM)
//     val wb_part = Module(new WB)

//     preif_part.io.preif2if <> if_part.io.preif2if
//     if_part.io.if2id <> id_part.io.if2id
//     id_part.io.id2ex <> ex_part.io.id2ex
//     ex_part.io.ex2preif <> preif_part.io.ex2preif
//     ex_part.io.ex2id <> id_part.io.ex2id
//     ex_part.io.ex2mem <> mem_part.io.ex2mem
//     mem_part.io.mem2id <> id_part.io.mem2id
//     mem_part.io.mem2wb <> wb_part.io.mem2wb
//     wb_part.io.wb2id <> id_part.io.wb2id
//     wb_part.io.stall_all := false.B

//     val isram_ctrl = Module(new AXI4SRAM(XLEN, XLEN, 1.toLong<<XLEN, 8))
//     val dsram_ctrl = Module(new AXI4SRAM(XLEN, XLEN, 1.toLong<<XLEN, 8))
//     val uart_axi4 = Module(new AXI4SRAM(XLEN, XLEN, 1.toLong<<XLEN, 8))

//     // val dummy = Module(new AXI4Dummy(XLEN, XLEN))
//     // mem_part.io.dsram <> dsram_ctrl.io.axi

//     val xbar_imem = Module(new AXI4XBarNto1(2, XLEN, XLEN, AXI4IDBITS))
//     xbar_imem.io.in(0) <> if_part.io.isram
//     xbar_imem.io.out <> isram_ctrl.io.axi

//     val xbar_dmem = Module(new AXI4XBar1toN(XLEN, XLEN, AXI4IDBITS, List(
//         (0x80000000L, 0x400000L, false),
//         (0x80400000L, 0x400000L, false),
//         (0xbfd00000L, 0x400000L, false),
//     )))

//     xbar_dmem.io.in <> mem_part.io.dmem
//     xbar_dmem.io.out(0) <> xbar_imem.io.in(1)
//     xbar_dmem.io.out(1) <> dsram_ctrl.io.axi
//     xbar_dmem.io.out(2) <> uart_axi4.io.axi

//     val isram = Module(new SyncRam(XLEN, 1.toLong<<XLEN, SyncRamType.DPIC))
//     val dsram = Module(new SyncRam(XLEN, 1.toLong<<XLEN, SyncRamType.DPIC))
//     val uart = Module(new SyncRam(XLEN, 1.toLong<<XLEN, SyncRamType.DPIC))
//     isram_ctrl.io.sram <> isram.io
//     dsram_ctrl.io.sram <> dsram.io
//     uart_axi4.io.sram <> uart.io
// }

class Core extends Module with Config{
    val io = IO(new Bundle{})

    val preif_part = Module(new nscscc2024.PREIF)
    val if_part = Module(new nscscc2024.IF)
    val id_part = Module(new nscscc2024.ID)
    val ex_part = Module(new nscscc2024.EX)
    val mem_part = Module(new nscscc2024.MEM)
    val wb_part = Module(new nscscc2024.WB)

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

    val isram_ctrl = Module(new AXI4SRAM_MultiCycs(XLEN, XLEN, 8, 1.toLong<<XLEN, 8, 1))
    val dsram_ctrl = Module(new AXI4SRAM_MultiCycs(XLEN, XLEN, 8, 1.toLong<<XLEN, 8, 1))
    val uart_axi4 = Module(new AXI4SRAM(XLEN, XLEN, 1.toLong<<XLEN, 8))

    // val dummy = Module(new AXI4Dummy(XLEN, XLEN))
    // mem_part.io.dsram <> dsram_ctrl.io.axi

    val xbar_imem = Module(new AXI4XBarNto1(2, XLEN, XLEN, AXI4IDBITS))
    xbar_imem.io.in(0) <> if_part.io.isram
    xbar_imem.io.out <> isram_ctrl.io.axi

    val xbar_dmem = Module(new AXI4XBar1toN(XLEN, XLEN, AXI4IDBITS, List(
        (0x80000000L, 0x400000L, false),
        (0x80400000L, 0x400000L, false),
        (0xbfd00000L, 0x400000L, false),
    )))

    xbar_dmem.io.in <> mem_part.io.dmem
    xbar_dmem.io.out(0) <> xbar_imem.io.in(1)
    xbar_dmem.io.out(1) <> dsram_ctrl.io.axi
    xbar_dmem.io.out(2) <> uart_axi4.io.axi

    val isram = Module(new SyncRam(XLEN, 1.toLong<<XLEN, SyncRamType.DPIC))
    val dsram = Module(new SyncRam(XLEN, 1.toLong<<XLEN, SyncRamType.DPIC))
    val uart = Module(new SyncRam(XLEN, 1.toLong<<XLEN, SyncRamType.DPIC))
    isram_ctrl.io.sram <> isram.io
    dsram_ctrl.io.sram <> dsram.io
    uart_axi4.io.sram <> uart.io
}

class CoreNSCSCC extends Module with Config{
    val RAM_DEPTH = 0x400000/4
    val io = IO(new Bundle{
        val isram = Flipped(new SyncRamIO(32, RAM_DEPTH))
        val dsram = Flipped(new SyncRamIO(32, RAM_DEPTH))
        val uart = new AXI4IO(XLEN, XLEN)
    })

    val preif_part = Module(new nscscc2024.PREIF)
    val if_part = Module(new nscscc2024.IF)
    val id_part = Module(new nscscc2024.ID)
    val ex_part = Module(new nscscc2024.EX)
    val mem_part = Module(new nscscc2024.MEM)
    val wb_part = Module(new nscscc2024.WB)

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

    val isram_axi4_wrapper = Module(new AXI4SRAM_MultiCycs(XLEN, XLEN, 8, RAM_DEPTH, 32, 3))
    val dsram_axi4_wrapper = Module(new AXI4SRAM_MultiCycs(XLEN, XLEN, 8, RAM_DEPTH, 32, 3))

    if_part.io.isram <> isram_axi4_wrapper.io.axi

    val xbar_imem = Module(new AXI4XBarNto1(2, XLEN, XLEN, AXI4IDBITS))
    xbar_imem.io.in(0) <> if_part.io.isram
    xbar_imem.io.out <> isram_axi4_wrapper.io.axi

    val xbar_dmem = Module(new AXI4XBar1toN(XLEN, XLEN, AXI4IDBITS, List(
        (0x80000000L, 0x400000L, false),
        (0x80400000L, 0x400000L, false),
        (0xbfd00000L, 0x400000L, false),
    )))

    xbar_dmem.io.in <> mem_part.io.dmem
    xbar_dmem.io.out(0) <> xbar_imem.io.in(1)
    xbar_dmem.io.out(1) <> dsram_axi4_wrapper.io.axi
    xbar_dmem.io.out(2) <> io.uart


    // val xbar = Module(new AXI4XBar(XLEN, XLEN, List((3, nagicore.unit.ip.axi_corssbar.Axi4RW.RW)), List((0x80000000L, 0x807FFFFFL), (0xbfd00000L, 0xbfd0FFFFL))))
    // xbar.io.slaves(0) <> dsram_axi4_wrapper.io.axi
    // xbar.io.slaves(1) <> io.uart
    // xbar.io.masters(0) <> mem_part.io.dsram

    isram_axi4_wrapper.io.sram <> io.isram
    dsram_axi4_wrapper.io.sram <> io.dsram
}
