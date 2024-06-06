package nagicore

import chisel3._
import chisel3.util._

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import nagicore.unit._

class TestCache extends AnyFlatSpec with ChiselScalatestTester{
    behavior of "Cache"
    it should "pass" in {
        test(new CachePiped(32, 32, 2, 10, 4, new Bundle {})).withAnnotations(Seq(WriteVcdAnnotation)) { dut => 
            dut.io.axi.aw.ready.poke(true.B)
            dut.io.axi.w.ready.poke(true.B)
            dut.io.axi.b.valid.poke(true.B)
            dut.io.axi.b.bits.resp.poke(0.U)
            dut.io.axi.b.bits.resp.poke(0.U)
            dut.io.axi.ar.ready.poke(true.B)
            dut.io.axi.r.valid.poke(false.B)
            //! dangerous
            dut.io.axi.r.bits.last.poke(false.B)

            dut.io.master.front.bits.addr.poke(1.U)
            dut.io.master.front.bits.size.poke(2.U)
            dut.io.master.front.bits.uncache.poke(false.B)
            dut.io.master.front.bits.valid.poke(true.B)
            dut.io.master.front.bits.wdata.poke(1.U)
            dut.io.master.front.bits.wmask.poke(0xf.U)
            dut.io.master.back.stall.poke(false.B)
            dut.clock.step()
            dut.io.master.front.bits.valid.poke(false.B)
            dut.clock.step()
            /* 测试Cache替换 */
            // ar assert
            dut.clock.step()
            dut.clock.step()
            var timeout: Int = 10
            var rcnt: Int = 0
            dut.io.axi.r.valid.poke(true.B)
            while(dut.io.master.front.stall.peekBoolean() && timeout >=0){
                dut.io.axi.r.bits.data.poke((0x10+rcnt).U)
                if(dut.io.axi.r.ready.peekBoolean()){
                    rcnt += 1
                    if(rcnt == 4) dut.io.axi.r.bits.last.poke(true.B)
                }
                dut.clock.step()
                timeout -= 1

            }
            dut.io.axi.r.valid.poke(false.B)
            dut.io.master.front.bits.valid.poke(true.B)
            dut.io.master.front.bits.wmask.poke(0.U)
            dut.clock.step()
            dut.io.master.front.bits.valid.poke(false.B)
            while(dut.io.master.front.stall.peekBoolean() && timeout >=0){
                dut.clock.step()
                timeout -= 1
            }
            dut.clock.step()
            dut.clock.step()
            /* 测试Cache连续命中读写 */
            dut.io.master.front.bits.valid.poke(true.B)
            dut.io.master.front.bits.addr.poke(4.U)
            dut.clock.step()

            dut.io.master.front.bits.valid.poke(true.B)
            dut.io.master.front.bits.addr.poke(8.U)
            dut.clock.step()
            /* expect read addr=0x4 */
            dut.io.master.back.stall.expect(false.B)
            dut.io.master.back.bits.rdata.expect(0x11.U)
            dut.io.master.back.bits.valid.expect(true.B)

            dut.io.master.front.bits.valid.poke(true.B)
            dut.io.master.front.bits.addr.poke(8.U)
            dut.io.master.front.bits.wdata.poke(0x102.U)
            dut.io.master.front.bits.wmask.poke(0xf.U)
            dut.clock.step()
            /* expect read addr=0x8 */
            dut.io.master.back.stall.expect(false.B)
            dut.io.master.back.bits.rdata.expect(0x12.U)
            dut.io.master.back.bits.valid.expect(true.B)

            dut.io.master.front.bits.valid.poke(true.B)
            dut.io.master.front.bits.addr.poke(8.U)
            dut.io.master.front.bits.wmask.poke(0.U)
            dut.clock.step()

            dut.clock.step()
            /* expect read addr=0x8 */
            dut.io.master.back.stall.expect(false.B)
            dut.io.master.back.bits.rdata.expect(0x102.U)
            dut.io.master.back.bits.valid.expect(true.B)

            dut.io.master.front.bits.valid.poke(false.B)
            dut.io.master.front.bits.wmask.poke(false.B)
            dut.clock.step()

        }
    }
}