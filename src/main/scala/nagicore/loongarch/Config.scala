package nagicore.loongarch

import chisel3._
import chisel3.util._

trait Config{
    def XLEN = 32
    def GPR_NUM = 32
    def GPR_LEN = log2Up(GPR_NUM)

    def SIM = true
    def PC_START = (0x1c000000).U(XLEN.W)

    def DPIC_UPDATE = true
}