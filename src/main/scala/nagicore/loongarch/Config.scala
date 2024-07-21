package nagicore.loongarch

import chisel3._
import chisel3.util._

trait Config{
    def XLEN = 32
    def GPR_NUM = 32
    def GPR_LEN = log2Up(GPR_NUM)

    def PC_START = "h80000000".U(XLEN.W)

    def ICACHE_WAYS = 2
    def ICACHE_LINES = 128
    def ICACHE_WORDS = 4

    def INSTRS_BUFF_SIZE = 8

    def DCACHE_WAYS = 2
    def DCACHE_LINES = 128*4
    def DCACHE_WORDS = 8
}