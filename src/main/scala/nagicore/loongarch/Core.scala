package nagicore.loongarch

import chisel3._
import chisel3.util._
import nagicore.loongarch.Config
import nagicore.loongarch.stages._

class Core extends Module with Config{
    val io = IO(new Bundle{
        val clk = Input(Clock())
        val rst = Input(Bool())
    })

    val if_stage = Module(new IF)
    val id_stage = Module(new ID)
    val ex_stage = Module(new EX)
    val mem_stage = Module(new MEM)
    val wb_stage = Module(new WB)

    if_stage.io.if2id <> id_stage.io.if2id
    id_stage.io.id2ex <> ex_stage.io.id2ex
    ex_stage.io.ex2if <> if_stage.io.ex2if
    ex_stage.io.ex2id <> id_stage.io.ex2id
    ex_stage.io.ex2mem <> mem_stage.io.ex2mem
    mem_stage.io.mem2id <> id_stage.io.mem2id
    mem_stage.io.mem2wb <> wb_stage.io.mem2wb
    wb_stage.io.wb2id <> id_stage.io.wb2id
    wb_stage.io.stall_all := false.B
}