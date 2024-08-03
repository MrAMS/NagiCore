package nagicore.unit

import chisel3._
import chisel3.util._
import nagicore.utils.Flags

object DIVU_IMP extends Enumeration {
    type DIVU_IMP = Value
    val none, radix2 = Value
}

class DIVU(dataBits: Int, imp_way: DIVU_IMP.DIVU_IMP = DIVU_IMP.radix2) extends Module{
    val io = IO(new Bundle{
        val a       = Input(UInt(dataBits.W))
        val b       = Input(UInt(dataBits.W))
        val signed  = Input(Bool())
        val quo     = Output(UInt(dataBits.W))
        val rem     = Output(UInt(dataBits.W))
        val valid   = Input(Bool())
        val busy    = Output(Bool())
    })

    imp_way match {
        case DIVU_IMP.radix2 => {
            /* ref: https://github.com/MaZirui2001/LAdataBitsR-pipeline-scala */

            /* stage1: solve sign */
            val sign_s = Mux(io.signed, io.a(dataBits-1) ^ io.b(dataBits-1), false.B)
            val sign_r = Mux(io.signed, io.a(dataBits-1), false.B)
            val src1 = Mux(io.signed && io.a(dataBits-1), ~io.a + 1.U, io.a)
            val src2 = Mux(io.signed && io.b(dataBits-1), ~io.b + 1.U, io.b)

            // get highest 1 in src1
            // TODO use log2
            val high_rev = PriorityEncoder(Reverse(src1))

            val cnt             = RegInit(0.U(6.W))
            val stage1_fire     = cnt === 0.U

            val src1_reg1       = RegEnable(src1, stage1_fire)
            val src2_reg1       = RegEnable(src2, stage1_fire)
            val signed_reg1     = RegEnable(io.signed, stage1_fire)
            val sign_s_reg1     = RegEnable(sign_s, stage1_fire)
            val sign_r_reg1     = RegEnable(sign_r, stage1_fire)
            val en_reg1         = RegEnable(io.valid, stage1_fire)
            val high_rev_reg1   = RegEnable(high_rev, stage1_fire)

            /* stage2+: div */
            val stage2_init     = en_reg1 && cnt === 0.U

            val src2_reg2       = RegEnable(src2_reg1, stage2_init)
            val signed_reg2     = RegEnable(signed_reg1, stage2_init)
            val sign_s_reg2     = RegEnable(sign_s_reg1, stage2_init)
            val sign_r_reg2     = RegEnable(sign_r_reg1, stage2_init)

            when(cnt =/= 0.U){
                cnt := cnt - 1.U
            }.elsewhen(en_reg1){
                cnt := (dataBits+1).U - high_rev_reg1
            }

            val quo_rem_reg = RegInit(0.U((dataBits*2+1).W))
            val quo = quo_rem_reg(dataBits-1, 0)
            val rem = quo_rem_reg(dataBits*2-1, dataBits)
            when(cnt =/= 0.U){
                val mins = rem - src2_reg2
                when(rem >= src2_reg2){
                    quo_rem_reg := mins(dataBits-1, 0) ## quo ## 1.U(1.W)
                }.otherwise{
                    quo_rem_reg := quo_rem_reg(dataBits*2-1, 0) ## 0.U(1.W)
                }
            }.elsewhen(en_reg1){
                quo_rem_reg := (0.U((dataBits+1).W) ## src1_reg1) << high_rev_reg1
            }

            io.busy := cnt =/= 0.U || en_reg1

            io.quo := Mux(signed_reg2,
                Mux(sign_s_reg2, ~quo + 1.U, quo),
                quo
            )

            val rem_res = quo_rem_reg(dataBits*2, dataBits+1)
            io.rem := Mux(signed_reg2,
                Mux(sign_r_reg2, ~rem_res + 1.U, rem_res),
                rem_res
            )
        }
        case DIVU_IMP.none => {
            io.busy := false.B
            io.quo := DontCare
            io.rem := DontCare
        }
    }
}

/* 
class DIVU(dataBits: Int) extends Module{
    val io = IO(new Bundle{
        val a       = Input(UInt(dataBits.W))
        val b       = Input(UInt(dataBits.W))
        val signed  = Input(Bool())
        val quo     = Output(UInt(dataBits.W))
        val rem     = Output(UInt(dataBits.W))
        val valid   = Input(Bool())
        val busy   = Output(Bool())
    })

    /* ref: https://github.com/MaZirui2001/LAdataBitsR-pipeline-scala */

    /* stage1: solve sign */
    val sign_s = Mux(io.signed, io.a(dataBits-1) ^ io.b(dataBits-1), false.B)
    val sign_r = Mux(io.signed, io.a(dataBits-1), false.B)
    val src1 = Mux(io.signed && io.a(dataBits-1), ~io.a + 1.U, io.a)
    val src2 = Mux(io.signed && io.b(dataBits-1), ~io.b + 1.U, io.b)

    // get highest 1 in src1
    val high_rev = PriorityEncoder(Reverse(src1))

    val src1_reg1       = ShiftRegister(src1, 1, !io.busy)
    val src2_reg1       = ShiftRegister(src2, 1, !io.busy)
    val signed_reg1     = ShiftRegister(io.signed, 1, !io.busy)
    val sign_s_reg1     = ShiftRegister(sign_s, 1, !io.busy)
    val sign_r_reg1     = ShiftRegister(sign_r, 1, !io.busy)
    val en_reg1         = ShiftRegister(io.valid, 1, !io.busy)
    val high_rev_reg1   = ShiftRegister(high_rev, 1, !io.busy)

    /* stage2+: div */
    val cnt             = RegInit(0.U(6.W))
    val stage2_init     = en_reg1 && cnt === 0.U

    val src2_reg2       = RegEnable(src2_reg1, stage2_init)
    val signed_reg2     = RegEnable(signed_reg1, stage2_init)
    val sign_s_reg2     = RegEnable(sign_s_reg1, stage2_init)
    val sign_r_reg2     = RegEnable(sign_r_reg1, stage2_init)

    when(cnt =/= 0.U){
        cnt := cnt - 1.U
    }.elsewhen(en_reg1){
        cnt := (dataBits+1).U - high_rev_reg1
    }

    val quo_rem_reg = RegInit(0.U((dataBits*2+1).W))
    val quo = quo_rem_reg(dataBits-1, 0)
    val rem = quo_rem_reg(dataBits*2-1, dataBits)
    when(cnt =/= 0.U){
        val mins = rem - src2_reg2
        when(rem >= src2_reg2){
            quo_rem_reg := mins ## quo ## 1.U(1.W)
        }.otherwise{
            quo_rem_reg := quo_rem_reg(dataBits*2-1, 0) ## 0.U(1.W)
        }
    }.elsewhen(en_reg1){
        quo_rem_reg := (0.U((dataBits+1).W) ## src1_reg1) << high_rev_reg1
    }

    !io.busy := cnt === 0.U

    io.quo := Mux(signed_reg2,
        Mux(sign_s_reg2, ~quo + 1.U, quo),
        quo
    )

    io.rem := Mux(signed_reg2,
        Mux(sign_r_reg2, ~quo_rem_reg(dataBits*2, dataBits+1) + 1.U, quo_rem_reg(dataBits*2, dataBits+1)),
        quo_rem_reg(dataBits*2, dataBits+1)
    )
}
 */