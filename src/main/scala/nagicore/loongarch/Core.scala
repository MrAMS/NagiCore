package nagicore.loongarch

import chisel3._
import chisel3.util._
import nagicore.bus.AXI4Slave
import nagicore.loongarch.Config
import nagicore.loongarch.stages._

class Core extends Module with Config{
    val io = IO(new Bundle{
        val clk = Input(Clock())
        val rst = Input(Bool())
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

    val isram = Module(new AXI4Slave(XLEN, XLEN))
    val dsram = Module(new AXI4Slave(XLEN, XLEN))
    if_part.io.isram <> isram.io
    mem_part.io.dsram <> dsram.io
}