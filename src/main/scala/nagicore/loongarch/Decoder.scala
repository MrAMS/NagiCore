package nagicore.loongarch

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import nagicore.unit.ALU
import nagicore.unit.ALU_OP
import nagicore.unit.BR_TYPE
import nagicore.utils.Flags
import org.json4s.scalap.scalasig.Flags

object DecoderMap{
    import Instructions._
    object ImmType{
        val si12    = "0000"
        val si20    = "0001"
        val ui12    = "0010"
        val ui5     = "0011"
        val offs26  = "0100"
        val offs16  = "0101"
        def apply() = UInt(4.W)
    }
    object regType{
        val rj     = "000"
        val rk     = "001"
        val rd     = "010"
        // can be used as GPR_WB=false
        val x      = "011"
        // for inst: BL
        val num1   = "100"
        def apply() = UInt(3.W)
    }


    def make_instr_list(
        regB_sel: String    = regType.rk,
        regC_sel: String    = regType.rd,
        imm_type: String    = ImmType.si12,
        aluA_sel: String    = CtrlFlags.aluASel.ra,
        aluB_sel: String    = CtrlFlags.aluBSel.rb,
        alu_op: String      = ALU_OP.X,
        brpcAdd_sel: String = CtrlFlags.brpcAddSel.pc,
        br_type: String     = BR_TYPE.NEVER,
        ld_type: String     = CtrlFlags.ldType.x,
        st_type: String     = CtrlFlags.stType.x,
        ill_instr: String   = "0")
        = Map(
            "regB_sel"->regB_sel,
            "regC_sel"->regC_sel,
            "imm_type"->imm_type,
            "aluA_sel"->aluA_sel,
            "aluB_sel"->aluB_sel,
            "alu_op"->alu_op,
            "brpcAdd_sel"->brpcAdd_sel,
            "br_type"->br_type,
            "ld_type"->ld_type,
            "st_type"->st_type,
            "ill_instr"->ill_instr
        )
    
    val default_instr_list = make_instr_list(ill_instr="1")
    val instr_list_map : Seq[(BitPat, Map[String, String])]   = Seq(
        // GR[rd] = GR[rj]+GR[rk]
        ADDW        -> make_instr_list(alu_op=ALU_OP.ADD),
        // GR[rd] = GR[rj]-GR[rk]
        SUBW        -> make_instr_list(alu_op=ALU_OP.SUB),
        // GR[rd] = GR[rj]+SignExt(si12, 32)
        ADDIW       -> make_instr_list(alu_op=ALU_OP.ADD, aluB_sel = CtrlFlags.aluBSel.imm),
        // GR[rd] = {si20, 12'b0}
        LU12IW      -> make_instr_list(alu_op=ALU_OP.COPY_B, aluB_sel = CtrlFlags.aluBSel.imm, imm_type=ImmType.si20),
        // GR[rd] = ([un]signed(GR[rj]) < [un]signed(GR[rk])) ? 1 : 0
        SLT         -> make_instr_list(alu_op=ALU_OP.LT),
        SLTU        -> make_instr_list(alu_op=ALU_OP.LTU),
        // GR[rd] = ([un]signed(GR[rj]) < [un]signed(SignExtend(si12, 32))) ? 1 : 0
        SLTI        -> make_instr_list(alu_op=ALU_OP.LT, aluB_sel = CtrlFlags.aluBSel.imm),
        SLTUI       -> make_instr_list(alu_op=ALU_OP.LTU, aluB_sel = CtrlFlags.aluBSel.imm),
        // GR[rd] = PC + SignExtend({si20, 12'b0}, 32)
        PCADDU12I   -> make_instr_list(alu_op=ALU_OP.ADD, aluA_sel = CtrlFlags.aluASel.pc, aluB_sel = CtrlFlags.aluBSel.imm, imm_type=ImmType.si20),
        // GR[rd] = GR[rj] ? GR[rk]
        AND         -> make_instr_list(alu_op=ALU_OP.AND),
        OR          -> make_instr_list(alu_op=ALU_OP.OR),
        XOR         -> make_instr_list(alu_op=ALU_OP.XOR),
        NOR         -> make_instr_list(alu_op=ALU_OP.NOR),
        // GR[rd] = GR[rj] ? ZeroExtend(ui12, 32)
        ANDI        -> make_instr_list(alu_op=ALU_OP.AND, aluB_sel = CtrlFlags.aluBSel.imm, imm_type = ImmType.ui12),
        ORI         -> make_instr_list(alu_op=ALU_OP.OR, aluB_sel = CtrlFlags.aluBSel.imm, imm_type = ImmType.ui12),
        XORI        -> make_instr_list(alu_op=ALU_OP.XOR, aluB_sel = CtrlFlags.aluBSel.imm, imm_type = ImmType.ui12),
        
        // product = signed(GR[rj]) * signed(GR[rk]); GR[rd] = product[31:0]
        MULW        -> make_instr_list(alu_op=ALU_OP.MUL),
        // product = signed(GR[rj]) * signed(GR[rk]); GR[rd] = product[63:32]
        MULHW       -> make_instr_list(alu_op=ALU_OP.MULH),
        // product = unsigned(GR[rj]) * unsigned(GR[rk]); GR[rd] = product[63:32]
        MULHWU      -> make_instr_list(alu_op=ALU_OP.MULHU),
        // quotient = signed(GR[rj]) / signed(GR[rk]); GR[rd] = quotient[31:0]
        DIVW        -> make_instr_list(alu_op=ALU_OP.DIV),
        // quotient = unsigned(GR[rj]) / unsigned(GR[rk]); GR[rd] = quotient[31:0]
        DIVWU       -> make_instr_list(alu_op=ALU_OP.DIVU),
        // remainder = signed(GR[rj]) % signed(GR[rk]); GR[rd] = remainder[31:0]
        MODW        -> make_instr_list(alu_op=ALU_OP.MOD),
        // remainder = unsigned(GR[rj]) % unsigned(GR[rk]); GR[rd] = remainder[31:0]
        MODWU       -> make_instr_list(alu_op=ALU_OP.MODU),

        // tmp = SLL(GR[rj], GR[rk][4:0]); GR[rd] = tmp[31:0]
        SLLW        -> make_instr_list(alu_op=ALU_OP.SL),
        // tmp = SRL(GR[rj], GR[rk][4:0]); GR[rd] = tmp[31:0]
        SRLW        -> make_instr_list(alu_op=ALU_OP.SR),
        // tmp = SRA(GR[rj], GR[rk][4:0]); GR[rd] = tmp[31:0]
        SRAW        -> make_instr_list(alu_op=ALU_OP.SRA),
        // tmp = SLL(GR[rj], ui5); GR[rd] = tmp[31:0]
        SLLIW       -> make_instr_list(alu_op=ALU_OP.SL, aluB_sel = CtrlFlags.aluBSel.imm, imm_type = ImmType.ui5),
        // tmp = SRL(GR[rj], ui5); GR[rd] = tmp[31:0]    
        SRLIW       -> make_instr_list(alu_op=ALU_OP.SR, aluB_sel = CtrlFlags.aluBSel.imm, imm_type = ImmType.ui5),
        // tmp = SRA(GR[rj], ui5); GR[rd] = tmp[31:0]
        SRAIW       -> make_instr_list(alu_op=ALU_OP.SRA, aluB_sel = CtrlFlags.aluBSel.imm, imm_type = ImmType.ui5),

        // if GR[rj] ? GR[rd] : PC = PC + SignExtend({offs16, 2'b0}, 32)
        BEQ         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.EQ,    imm_type=ImmType.offs16),
        BNE         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.NE,    imm_type=ImmType.offs16),
        BLT         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.LT,    imm_type=ImmType.offs16),
        BGE         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.GE,    imm_type=ImmType.offs16),
        BLTU        -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.LTU,   imm_type=ImmType.offs16),
        BGEU        -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.GEU,   imm_type=ImmType.offs16),
        // PC = PC + SignExtend({offs26, 2'b0}, 32)
        B           -> make_instr_list(regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.ALWAYS, imm_type=ImmType.offs26),
        // GR[1] = PC + 4; PC = PC + SignExtend({offs26, 2'b0}, 32)
        BL          -> make_instr_list(regC_sel=regType.num1, alu_op=ALU_OP.ADD, aluA_sel=CtrlFlags.aluASel.pc, aluB_sel = CtrlFlags.aluBSel.num4, br_type=BR_TYPE.ALWAYS, imm_type=ImmType.offs26),
        // GR[rd] = PC + 4; PC = GR[rj] + SignExtend({offs16, 2'b0}, 32)
        JIRL        -> make_instr_list(alu_op=ALU_OP.ADD, aluA_sel=CtrlFlags.aluASel.pc, aluB_sel = CtrlFlags.aluBSel.num4, br_type=BR_TYPE.ALWAYS, imm_type=ImmType.offs16, brpcAdd_sel = CtrlFlags.brpcAddSel.ra_val),


        LDB         -> make_instr_list(alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, ld_type=CtrlFlags.ldType.b),
        LDBU        -> make_instr_list(alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, ld_type=CtrlFlags.ldType.bu),
        LDH         -> make_instr_list(alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, ld_type=CtrlFlags.ldType.h),
        LDHU        -> make_instr_list(alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, ld_type=CtrlFlags.ldType.hu),
        LDW         -> make_instr_list(alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, ld_type=CtrlFlags.ldType.w),

        STB         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, st_type=CtrlFlags.stType.b),
        STH         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, st_type=CtrlFlags.stType.h),
        STW         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, st_type=CtrlFlags.stType.w),
        
    )
}

class Decoder extends Module with Config{
    val io = IO(new Bundle {
        val instr       = Input(UInt(XLEN.W))

        val ra          = Output(UInt(GPR_LEN.W))
        val rb          = Output(UInt(GPR_LEN.W))
        val rc          = Output(UInt(GPR_LEN.W))
        val imm         = Output(UInt(XLEN.W))
        val aluA_sel    = Output(CtrlFlags.aluASel())
        val aluB_sel    = Output(CtrlFlags.aluBSel())
        val alu_op      = Output(ALU_OP())
        val br_type     = Output(BR_TYPE())
        val brpcAdd_sel = Output(CtrlFlags.brpcAddSel())
        val ld_type     = Output(CtrlFlags.ldType())
        val st_type     = Output(CtrlFlags.stType())
        val ill_instr   = Output(Bool())

    })
    def decode_signal(signal_name:String) =
        Flags.decode_flag(signal_name, io.instr, DecoderMap.instr_list_map, DecoderMap.default_instr_list)

    io.aluA_sel := decode_signal("aluA_sel")
    io.aluB_sel := decode_signal("aluB_sel")
    io.alu_op := decode_signal("alu_op")
    io.brpcAdd_sel := decode_signal("brpcAdd_sel")
    io.br_type := decode_signal("br_type")
    io.ld_type := decode_signal("ld_type")
    io.st_type := decode_signal("st_type")
    io.ill_instr := decode_signal("ill_instr")

    def imm_gen(inst: UInt, imm_type: UInt): UInt = {
        val imm = Wire(UInt(32.W))
        import DecoderMap.ImmType._
        imm := Flags.CasesMux(imm_type, Seq(
            si12    -> Cat(Fill(20, inst(21)), inst(21, 10)),
            si20    -> Cat(inst(24, 5), 0.U(12.W)),
            ui12    -> Cat(0.U(20.W), inst(21, 10)),
            ui5     -> Cat(0.U(27.W), inst(14, 10)),
            offs26  -> Cat(Fill(4, inst(9)), inst(9, 0), inst(25, 10), 0.U(2.W)),
            offs16  -> Cat(Fill(14, inst(25)), inst(25, 10), 0.U(2.W)),
        ), 0.U)
        imm
    }
    
    val imm_type = decode_signal("imm_type")
    io.imm := imm_gen(io.instr, imm_type)

    val rd = io.instr(4, 0).asUInt
    val rj = io.instr(9, 5).asUInt
    val rk = io.instr(14, 10).asUInt
    io.ra := rj

    val rb_type = decode_signal("regB_sel")

    io.rb := Flags.CasesMux(rb_type, Seq(
        DecoderMap.regType.rj   -> rj,
        DecoderMap.regType.rk   -> rk,
        DecoderMap.regType.rd   -> rd,
        DecoderMap.regType.x    -> 0.U,
        DecoderMap.regType.num1 -> 1.U,
    ), 0.U)

    val rc_type = decode_signal("regC_sel")

    io.rc := Flags.CasesMux(rc_type, Seq(
        DecoderMap.regType.rj   -> rj,
        DecoderMap.regType.rk   -> rk,
        DecoderMap.regType.rd   -> rd,
        DecoderMap.regType.x    -> 0.U,
        DecoderMap.regType.num1 -> 1.U,
    ), 0.U)
    
}