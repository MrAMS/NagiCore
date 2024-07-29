package nagicore

import chisel3._
import chisel3.util._

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import nagicore.bus._

class TestAXI4Agent extends AnyFlatSpec with ChiselScalatestTester{
    behavior of "Write Agent"
    it should "pass" in {
        test(new AXI4WriteAgent(32, 32, 4)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
            val rand = new scala.util.Random
            dut.io.axi.aw.ready.poke(true.B)
            dut.io.axi.w.ready.poke(true.B)
            dut.io.axi.b.valid.poke(false.B)
            dut.io.axi.b.resp.poke(0.U)
            dut.io.cmd.in.req.poke(false.B)
            dut.clock.step()
            dut.io.cmd.in.req.poke(true.B)
            dut.io.cmd.in.addr.poke(1.U)
            dut.io.cmd.in.len.poke(3.U)
            dut.io.cmd.in.size.poke(5.U)
            for (i <- 0 to 3) {
                dut.io.cmd.in.wmask(i).poke(0xf.U)
                dut.io.cmd.in.wdata(i).poke(i.U)
            }
            dut.clock.step()
//            dut.io.axi.aw.addr.expect(1.U)
            dut.io.cmd.in.req.poke(false.B)
            dut.clock.step()
            while(!dut.io.axi.w.last.peekBoolean()){
                dut.io.axi.w.ready.poke(rand.nextBoolean().B)
                dut.clock.step()
            }
            dut.io.axi.b.valid.poke(true.B)
            dut.clock.step()
            dut.io.cmd.out.ready.expect(true.B)
            dut.io.cmd.out.resp.expect(0.U)
        }
    }
    behavior of "Read Agent"
    it should "pass" in {
        test(new AXI4ReadAgent(32, 32, 4)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
            val rand = new scala.util.Random
            dut.io.axi.ar.ready.poke(true.B)
            dut.io.axi.r.valid.poke(false.B)
            dut.io.axi.r.resp.poke(0.U)
            dut.io.cmd.in.req.poke(false.B)
            dut.clock.step()
            dut.io.cmd.in.req.poke(true.B)
            dut.io.cmd.in.addr.poke(1.U)
            dut.io.cmd.in.len.poke(3.U)
            dut.io.cmd.in.size.poke(5.U)
            dut.clock.step()

            dut.io.cmd.in.req.poke(false.B)
            dut.clock.step()
            var cnt: BigInt = 0
            while(cnt < 4){
                val skip = rand.nextBoolean()
                dut.io.axi.r.valid.poke(skip.B)
                dut.io.axi.r.data.poke(cnt)
                dut.io.axi.r.last.poke((cnt==3).B)
                dut.clock.step()
                if(skip) cnt += 1
            }
            dut.io.axi.r.valid.poke(false.B)
            dut.clock.step()
        }
    }
}