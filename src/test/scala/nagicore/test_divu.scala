package nagicore

import chisel3._
import chisel3.util._

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import nagicore.unit.DIVU

class TestDIVU extends AnyFlatSpec with ChiselScalatestTester{
    behavior of "DIVU"
    it should "pass" in {
        test(new DIVU(32)).withAnnotations(Seq(WriteVcdAnnotation)) { dut => 
            val rand = new scala.util.Random
            dut.reset.poke(true.B)
            dut.clock.step()
            dut.reset.poke(false.B)
            dut.clock.step()
            for(i <- 0 until 10){
                val max = 100
                val a = rand.nextInt(max)
                val b = rand.nextInt(max)
                val signed = rand.nextBoolean()
                dut.io.signed.poke(signed.B)
                dut.io.a.poke(a.U)
                dut.io.b.poke(b.U)
                dut.io.valid.poke(true.B)
                dut.clock.step()
                dut.io.valid.poke(false.B)
                dut.clock.step()
                while(dut.io.busy.peekBoolean()){
                    dut.clock.step()
                }

                println(s"$a/$b=${dut.io.quo.peekInt()}...${dut.io.rem.peekInt()}")
                // if(signed)
                //     dut.io.quo.expect((a / b).S.asUInt)
                // else
                //     dut.io.quo.expect((a / b).U)
                // dut.io.quo.expect(a_in / b_in)
            }
        }
    }
}