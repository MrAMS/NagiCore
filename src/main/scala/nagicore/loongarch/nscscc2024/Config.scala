package nagicore.loongarch.nscscc2024

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

    def WBUFF_LEN = 4

    def BTB_ENTRYS = 8

    def AXI4IDBITS = 4
}