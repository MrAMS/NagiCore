package nagicore.loongarch.nscscc2024

import chisel3._
import chisel3.util._
import nagicore.bus.{AXI4SRAM, AXI4IO, Ram, RamType, RamIO}
import nagicore.bus.{AXI4XBar1toN, AXI4XBarNto1, AXI4SRAM_MultiCycs}

class Core extends Module with Config{
    val io = IO(new Bundle{})

    val preif_part = Module(new stages.PREIF)
    val if_part = Module(new stages.IF)
    val id_part = Module(new stages.ID)
    val ex_part = Module(new stages.EX)
    val mem_part = Module(new stages.MEM)

    preif_part.io.preif2if <> if_part.io.preif2if
    if_part.io.if2id <> id_part.io.if2id
    id_part.io.id2ex <> ex_part.io.id2ex
    ex_part.io.ex2preif <> preif_part.io.ex2preif
    ex_part.io.ex2id <> id_part.io.ex2id
    ex_part.io.ex2mem <> mem_part.io.ex2mem
    mem_part.io.mem2id <> id_part.io.mem2id
    mem_part.io.stall_all := false.B

    val isram_ctrl = Module(new AXI4SRAM_MultiCycs(XLEN, XLEN, 8, 1.toLong<<XLEN, 8, 3, 2))
    val dsram_ctrl = Module(new AXI4SRAM_MultiCycs(XLEN, XLEN, 8, 1.toLong<<XLEN, 8, 3, 2))
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

    val isram = Module(new Ram(XLEN, 1.toLong<<XLEN, RamType.DPIC_2CYC))
    val dsram = Module(new Ram(XLEN, 1.toLong<<XLEN, RamType.DPIC_2CYC))
    val uart = Module(new Ram(XLEN, 1.toLong<<XLEN, RamType.DPIC_2CYC))
    isram_ctrl.io.sram <> isram.io
    dsram_ctrl.io.sram <> dsram.io
    uart_axi4.io.sram <> uart.io
}

class CoreNSCSCC extends Module with Config{
    val RAM_DEPTH = 0x400000/4
    val io = IO(new Bundle{
        val isram = Flipped(new RamIO(32, RAM_DEPTH))
        val dsram = Flipped(new RamIO(32, RAM_DEPTH))
        val uart = new AXI4IO(XLEN, XLEN)
    })

    val preif_part = Module(new stages.PREIF)
    val if_part = Module(new stages.IF)
    val id_part = Module(new stages.ID)
    val ex_part = Module(new stages.EX)
    val mem_part = Module(new stages.MEM)

    preif_part.io.preif2if <> if_part.io.preif2if
    if_part.io.if2id <> id_part.io.if2id
    id_part.io.id2ex <> ex_part.io.id2ex
    ex_part.io.ex2preif <> preif_part.io.ex2preif
    ex_part.io.ex2id <> id_part.io.ex2id
    ex_part.io.ex2mem <> mem_part.io.ex2mem
    mem_part.io.mem2id <> id_part.io.mem2id
    mem_part.io.stall_all := false.B

    val isram_axi4_wrapper = Module(new AXI4SRAM_MultiCycs(XLEN, XLEN, 8, RAM_DEPTH, 32, 3, 2))
    val dsram_axi4_wrapper = Module(new AXI4SRAM_MultiCycs(XLEN, XLEN, 8, RAM_DEPTH, 32, 3, 2))

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

    isram_axi4_wrapper.io.sram <> io.isram
    dsram_axi4_wrapper.io.sram <> io.dsram
}
