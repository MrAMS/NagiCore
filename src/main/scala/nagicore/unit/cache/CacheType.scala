package nagicore.unit.cache

import chisel3._
import chisel3.util._

import nagicore.bus.{Ram, RamIO}

object CacheMemType extends Enumeration {
    type CacheMemType = Value
    val RAM_2cyc, BRAM_1cyc, RAM_1cyc = Value
}

object CacheReplaceType extends Enumeration {
    type CacheReplaceType = Value
    val Random, LRU = Value
}

/**
  * CacheRAM 第二个周期返回读内容的同步RAM
  *
  * @param width
  * @param depth
  * @param imp
  */
class CacheMem(width: Int, depth: Int, imp: CacheMemType.CacheMemType=CacheMemType.RAM_2cyc) extends Module{
    val io = IO(new RamIO(width, depth))
    imp match {
        case _ => {
            val sram = Module(new Ram(width, depth))
            sram.io <> io
        }
    }
}