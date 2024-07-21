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
        case _ => {
            exportVerilog(() => new nagicore.loongarch.Core)
        }
    }
}

object GlobalConfg{
    var SIM = true
}