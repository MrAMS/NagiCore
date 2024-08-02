package nagicore.unit

import chisel3._
import chisel3.util._
import nagicore.utils.Flags
import nagicore.utils.isPowerOf2

object BP_TYPE{
    val dontcare = "??"
    val jump    = "01" // unconditional jump
    val cond    = "10" // conditional jump
    def apply() = UInt(2.W)
}

class BTBPredInIO(pcBits: Int) extends Bundle {
    val pc = UInt(pcBits.W)
}

class BTBPredOutIO(entryNum: Int, pcBits: Int) extends Bundle {
    val taken = Bool()
    val target = UInt(pcBits.W)
    val hit = Bool()
    val index = UInt(log2Ceil(entryNum).W)
}

class BTBUpdateIO(entryNum: Int, pcBits: Int) extends Bundle {
    val bp_type = BP_TYPE()
    val taken = Bool() // cond jump taken or not
    val pc = UInt(pcBits.W)
    val target = UInt(pcBits.W)
    val hit = Bool()
    val index = UInt(log2Ceil(entryNum).W) // hit btb index
    val valid = Bool()
}

class NoBTB(entryNum: Int, pcBits: Int, tagBits: Int, scInit: Int=0, instrBytes: Int=4) extends Module {
    require(pcBits >= tagBits && tagBits > 0 && instrBytes > 0)
    val io = IO(new Bundle {
        val pred = new Bundle {
            val in = Input(new BTBPredInIO(pcBits))
            val out = Output(new BTBPredOutIO(entryNum, pcBits))
        }
        val update = Input(new BTBUpdateIO(entryNum, pcBits))
    })
    io.pred.out := DontCare
    io.pred.out.taken := false.B
}

class BTB(entryNum: Int, pcBits: Int, tagBits: Int, scInit: Int=0, instrBytes: Int=4) extends Module {
    require(pcBits >= tagBits && tagBits > 0 && instrBytes > 0 && isPowerOf2(entryNum))
    val io = IO(new Bundle {
        val pred = new Bundle {
            val in = Input(new BTBPredInIO(pcBits))
            val out = Output(new BTBPredOutIO(entryNum, pcBits))
        }
        val update = Input(new BTBUpdateIO(entryNum, pcBits))
    })
    class BTBTableEntry extends Bundle {
        // 两位饱和计数器
        // 00: strongly not taken, 01: not taken, 10: taken, 11: strongly taken
        val sc = UInt(2.W)
        val tag = UInt(tagBits.W)
        val target = UInt(pcBits.W)
        val valid = Bool()
    }
    def get_tag(pc: UInt): UInt = {
        pc(log2Ceil(instrBytes)+tagBits-1, log2Ceil(instrBytes))
    }
    val table = RegInit(VecInit(Seq.fill(entryNum)({
        val bundle = Wire(new BTBTableEntry())
        bundle.sc := scInit.U(2.W)
        bundle.tag := 0.U
        bundle.target := 0.U
        bundle.valid := false.B
        bundle
    })))

    val entry_p = RegInit(0.U(log2Ceil(entryNum).W))

    val pred_tag = get_tag(io.pred.in.pc)
    val pred_hits = VecInit.tabulate(entryNum){
        i => pred_tag === table(i).tag && table(i).valid
    }
    val pred_hit = pred_hits.reduceTree(_ || _)
    val pred_hit_index = OHToUInt(pred_hits)
    val pred_hit_entry = table(pred_hit_index)
    io.pred.out.taken := pred_hit && pred_hit_entry.sc(1)
    io.pred.out.target := pred_hit_entry.target
    io.pred.out.hit := pred_hit
    io.pred.out.index := pred_hit_index

    when(io.update.valid){
        when(Flags.OHis(io.update.bp_type, BP_TYPE.jump)){
            when(io.update.hit){
                table(io.update.index).sc := 3.U
                table(io.update.index).target := io.update.target
            }.otherwise{
                table(entry_p).sc := 3.U
                table(entry_p).target := io.update.target
                table(entry_p).tag := get_tag(io.update.pc)
                table(entry_p).valid := true.B
                entry_p := entry_p + 1.U
            }
        }.elsewhen(Flags.OHis(io.update.bp_type, BP_TYPE.cond)){
            when(io.update.hit){
                val sc = table(io.update.index).sc
                table(io.update.index).sc := Mux(io.update.taken,
                    // 11 -> 11, 00 -> 01, 01 -> 10, 10 -> 11
                    Mux(sc===3.U, 3.U, Cat(sc(1)|sc(0), ~sc(0))),
                    // 00 -> 00, 01 -> 00, 10 -> 01, 11 -> 10
                    Mux(sc===0.U, 0.U, Cat(sc(1)&sc(0), ~sc(0)))
                )
                table(io.update.index).target := io.update.target
            }.otherwise{
                table(entry_p).sc := 2.U // weekly taken
                table(entry_p).target := io.update.target
                table(entry_p).tag := get_tag(io.update.pc)
                table(entry_p).valid := true.B
                entry_p := entry_p + 1.U
            }
        }
    }

}