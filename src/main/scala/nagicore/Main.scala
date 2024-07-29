package nagicore

import chisel3._
import chisel3.util._ 
import _root_.circt.stage._

object Main extends App {
    val target = args(0)
    val build_dir = "./build"
    println(target)
    def exportVerilog(core: () => chisel3.RawModule): Unit = {
        println("Export Verilog Started")
        val chiselStageOption = Seq(
            chisel3.stage.ChiselGeneratorAnnotation(() => core()),
            CIRCTTargetAnnotation(CIRCTTarget.Verilog)
        )
        val firtoolOptions = Seq(
            // FirtoolOption("--lowering-options=disallowLocalVariables,locationInfoStyle=wrapInAtSquareBracket,noAlwaysComb"),
            FirtoolOption("--lowering-options=disallowLocalVariables,locationInfoStyle=wrapInAtSquareBracket,noAlwaysComb"),
//            FirtoolOption("--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=wrapInAtSquareBracket,noAlwaysComb"),

            FirtoolOption("--split-verilog"),
            FirtoolOption("-o=" + build_dir),
            FirtoolOption("--disable-all-randomization"),
        )
        val executeOptions = chiselStageOption ++ firtoolOptions
        val executeArgs = Array("-td", build_dir)
        (new ChiselStage).execute(executeArgs, executeOptions)
    }
    target match {
        case "NSCSCC" => {
            GlobalConfg.SIM = false
            exportVerilog(() => new nagicore.loongarch.CoreNSCSCC)
        }
        // case "TEST" => {
        //     exportVerilog(() => new Module{
        //         val io = IO(new Bundle {
        //             val clk = Input(Clock())
        //         })
        //         val a = "h123".U
        //         val xbar = Module(new nagicore.unit.ip.axi_corssbar.AXI4XBar(32, 32, List((0, nagicore.unit.ip.axi_corssbar.Axi4RW.RW)), List(("0x80000000", "0x807FFFFF"))))
        //         xbar.io.masters <> DontCare
        //         xbar.io.slaves <> DontCare
        //         xbar.io.slaves(0).ar.addr := 2.U(32.W)
        //     })
        // }
        case _ => {
            exportVerilog(() => new nagicore.loongarch.Core)
        }
    }
}

object GlobalConfg{
    var SIM = true
}