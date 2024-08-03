package nagicore.unit

import chisel3._
import chisel3.util._
import nagicore.utils._
import nagicore.GlobalConfg

object MULU_IMP extends Enumeration {
    type MULU_IMP = Value
    val none, synthesizer_1cyc, oneBitShift, xsArrayMul, MultiplierIP, synthesizer_DSP = Value
}

object MULU_OP{
    val MUL     = ALU_OP.MUL.takeRight(2)
    val MULH    = ALU_OP.MULH.takeRight(2)
    val MULHU   = ALU_OP.MULHU.takeRight(2)
}

/**
  * 乘法器
  *
  * @param dataBits 位宽
  * @param imp_way  实现方法，有3种实现方式，分别为：
  *                   synthesizer: 直接使用*，依靠综合器生成单周期乘法器
  *                   oneBitShift: 一位移位乘法实现
  *                   xsArrayMul:  使用香山的三周期ArrayMulDataModule实现
  * @note 注意valid信号只拉高一周期即可，busy在下一个周期开始拉高，直到乘法运算结束时拉低
  */
class MULU(dataBits: Int, imp_way: MULU_IMP.MULU_IMP = MULU_IMP.synthesizer_1cyc) extends Module{
    val io = IO(new Bundle{
        val a   = Input(UInt(dataBits.W))
        val b   = Input(UInt(dataBits.W))
        val op  = Input(UInt(2.W))
        val out = Output(UInt(dataBits.W))
        val vaild = Input(Bool())
        val busy = Output(Bool())
    })
    if(GlobalConfg.SIM){
        imp_way match {
            case MULU_IMP.xsArrayMul | MULU_IMP.MultiplierIP | MULU_IMP.synthesizer_DSP => {
                io.busy := io.vaild || RegNext(io.vaild)
            }
            case _ => {
                io.busy := false.B
            }
        }
        io.out := Flags.CasesMux(io.op, Seq(
            MULU_OP.MUL     -> (io.a.asSInt * io.b.asSInt)(31, 0).asUInt,
            MULU_OP.MULH    -> (io.a.asSInt * io.b.asSInt)(63, 32).asUInt,
            MULU_OP.MULHU   -> (io.a * io.b)(63, 32),
        ), 0.U)
    }else{
        imp_way match {
            case MULU_IMP.xsArrayMul => {
                import nagicore.unit.ip.Xiangshan.ArrayMulDataModule
                val arrayMul = Module(new ArrayMulDataModule(dataBits+1))
                arrayMul.io.a := Flags.ifEqu(io.op, MULU_OP.MULHU, 0.U(1.W), io.a(dataBits-1)) ## io.a
                arrayMul.io.b := Flags.ifEqu(io.op, MULU_OP.MULHU, 0.U(1.W), io.b(dataBits-1)) ## io.b
                val valid_reg1 = RegNext(io.vaild)
                arrayMul.io.regEnables(0) := io.vaild
                arrayMul.io.regEnables(1) := valid_reg1
                // val res = arrayMul.io.result
                val res = RegNext(arrayMul.io.result)
                io.out := Flags.CasesMux(io.op, Seq(
                    MULU_OP.MUL     -> res(31, 0),
                    MULU_OP.MULH    -> SignExt(res(63, 32), dataBits),
                    MULU_OP.MULHU   -> res(63, 32),
                ), 0.U)
                io.busy := io.vaild || valid_reg1
            }
            case MULU_IMP.synthesizer_DSP => {
                def DSPInPipe[T <: Data](a: T) = RegNext(a)
                def DSPOutPipe[T <: Data](a: T) = RegNext(a)
                val a = Flags.ifEqu(io.op, MULU_OP.MULHU, 0.U(1.W), io.a(dataBits-1)) ## io.a
                val b = Flags.ifEqu(io.op, MULU_OP.MULHU, 0.U(1.W), io.b(dataBits-1)) ## io.b
                val res = DSPOutPipe(DSPInPipe(a) * DSPInPipe(b))
                io.out := Flags.CasesMux(io.op, Seq(
                    MULU_OP.MUL     -> res(31, 0),
                    MULU_OP.MULH    -> SignExt(res(63, 32), dataBits),
                    MULU_OP.MULHU   -> res(63, 32),
                ), 0.U)
                val busy = RegInit(false.B)
                when(io.vaild && !busy){ busy := true.B }
                val ready = DSPOutPipe(DSPInPipe(io.vaild))
                when(ready){ busy := false.B }
                io.busy := busy
            }
            case MULU_IMP.MultiplierIP => {
                Predef.println(s"Xilinx Multiplier IP mult_${dataBits+1}_unsigned_2stages needed")
                class MultiplierIP extends BlackBox{
                    override val desiredName = s"mult_${dataBits+1}_unsigned_2stages"
                    val io = IO(new Bundle {
                        val CLK     = Input(Clock())
                        val A       = Input(UInt((dataBits+1).W))
                        val B       = Input(UInt((dataBits+1).W))
                        val P       = Output(UInt(((dataBits+1)*2).W))
                    })
                }
                val ip = Module(new MultiplierIP)
                ip.io.CLK := clock
                ip.io.A := Flags.ifEqu(io.op, MULU_OP.MULHU, 0.U(1.W), io.a(dataBits-1)) ## io.a
                ip.io.B := Flags.ifEqu(io.op, MULU_OP.MULHU, 0.U(1.W), io.b(dataBits-1)) ## io.b
                val res = ip.io.P
                io.out := Flags.CasesMux(io.op, Seq(
                    MULU_OP.MUL     -> res(31, 0),
                    MULU_OP.MULH    -> SignExt(res(63, 32), dataBits),
                    MULU_OP.MULHU   -> res(63, 32),
                ), 0.U)
                io.busy := io.vaild || RegNext(io.vaild) || RegNext(RegNext(io.vaild))
            }
            case MULU_IMP.none => {
                io.busy := false.B
                io.out := DontCare
            }
            case _ => {
                io.busy := false.B
                io.out := Flags.CasesMux(io.op, Seq(
                    MULU_OP.MUL     -> (io.a.asSInt * io.b.asSInt)(31, 0).asUInt,
                    MULU_OP.MULH    -> (io.a.asSInt * io.b.asSInt)(63, 32).asUInt,
                    MULU_OP.MULHU   -> (io.a * io.b)(63, 32),
                ), 0.U)
            }
        }
    }

    // if(imp_way == MULU_IMP.synthesizer){

    // }else{
    //     // TODO
    //     /*
    //     原理：
    //     n位数和n位数的乘法，Booth乘法将其转换为n/2个2*n位数（即部分积）相加，
    //     而华莱士树再将其转换为2*n个n/2 bits华莱士树，最终转换成两个2*n位数的加法，
    //     其中，每个n/2 bits华莱士树，有n/2个一位数相加，
    //      */
    //     // x * y
    //     def booth2(x: UInt, y: UInt, n: Int, yi: Int) =  {
    //         assert(yi>=1&&yi<=y.getWidth-1)
    //         val t = WireDefault(x)
    //         switch(y(yi+1,yi-1)){
    //             is(0.U){ t := 0.U }
    //             is(3.U){ t := x(n-2, 0) ## 0.U(1.W) }
    //             is(4.U){ t := x(n-2, 0) ## 0.U(1.W) }
    //             is(7.U){ t := 0.U }
    //         }
    //         Mux(y(yi+1), ~t + 1.U, t)
    //     }
    //     // Carry-Save Adder
    //     def CSA(a: UInt, b: UInt, cin: UInt) = {
    //         assert(a.getWidth==b.getWidth&&b.getWidth==cin.getWidth)
    //         val res = Vec(2, UInt(a.getWidth.W))
    //         val a_xor_b = a ^ b
    //         val a_and_b = a & b
    //         val sum = a_xor_b ^ cin
    //         val cout = a_and_b | (a_xor_b & cin)
    //         res(0) := sum
    //         res(1) := cout
    //         res
    //     }
    // }

}
