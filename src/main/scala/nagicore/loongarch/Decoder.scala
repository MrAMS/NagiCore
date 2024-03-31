package nagicore.loongarch

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import nagicore.unit.ALU
import nagicore.unit.ALU_OP
import nagicore.unit.BR_TYPE
import nagicore.utils.onehot

object DecoderMap{
    import Instructions._
    object ImmType extends ChiselEnum{
        val si12    = Value((1<<0).U)
        val si20    = Value((1<<1).U)
        val ui12    = Value((1<<2).U)
        val ui5     = Value((1<<3).U)
        val offs26  = Value((1<<4).U)
        val offs16  = Value((1<<5).U)
    }
    object regType extends ChiselEnum{
        val rj     = Value((1<<0).U)
        val rk     = Value((1<<1).U)
        val rd     = Value((1<<2).U)
        // can be used as GPR_WB=false
        val x      = Value((1<<3).U)
        // for inst: BL
        val num1   = Value((1<<4).U)
    }


    def make_instr_list(
        regB_sel: regType.Type                  = regType.rk,
        regC_sel: regType.Type                  = regType.rd,
        imm_type: ImmType.Type                  = ImmType.si12,
        aluA_sel: CtrlFlags.aluASel.Type        = CtrlFlags.aluASel.ra,
        aluB_sel: CtrlFlags.aluBSel.Type        = CtrlFlags.aluBSel.rb,
        alu_op: ALU_OP.Type                     = ALU_OP.X,
        brpcAdd_sel: CtrlFlags.brpcAddSel.Type  = CtrlFlags.brpcAddSel.pc,
        br_type: BR_TYPE.Type                   = BR_TYPE.NEVER,
        ld_type: CtrlFlags.ldType.Type          = CtrlFlags.ldType.x,
        st_type: CtrlFlags.stType.Type          = CtrlFlags.stType.x,
        ill_instr: Bool                         = false.B)
        = List(regB_sel, regC_sel, imm_type, aluA_sel, aluB_sel, alu_op, brpcAdd_sel, br_type, ld_type, st_type, ill_instr)
    
    val default_instr_list = make_instr_list(ill_instr=true.B)
    val instr_list_map = Array(
        // GR[rd] = GR[rj]+GR[rk]
        ADDW        -> make_instr_list(alu_op=ALU_OP.ADD),
        // GR[rd] = GR[rj]-GR[rk]
        SUBW        -> make_instr_list(alu_op=ALU_OP.SUB),
        // GR[rd] = GR[rj]+SignExt(si12, 32)
        ADDIW       -> make_instr_list(alu_op=ALU_OP.ADD, aluB_sel = CtrlFlags.aluBSel.imm),
        // GR[rd] = {si20, 12'b0}
        LU12IW      -> make_instr_list(alu_op=ALU_OP.COPY_B, imm_type=ImmType.si20),
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
        BEQ         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.EQ),
        BNE         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.NE),
        BLT         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.LT),
        BGE         -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.GE),
        BLTU        -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.LTU),
        BGEU        -> make_instr_list(regB_sel=regType.rd, regC_sel=regType.x, alu_op=ALU_OP.X, br_type=BR_TYPE.GEU),
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

        STB         -> make_instr_list(regC_sel=regType.x, alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, st_type=CtrlFlags.stType.b),
        STH         -> make_instr_list(regC_sel=regType.x, alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, st_type=CtrlFlags.stType.h),
        STW         -> make_instr_list(regC_sel=regType.x, alu_op=ALU_OP.ADD, aluB_sel=CtrlFlags.aluBSel.imm, st_type=CtrlFlags.stType.w),
        
    )
    // val tmap = Array(
    //     STB -> List(CtrlFlags.nextPc.pc),
    // )
    // print(instr_list_map.map(
    //         x => x._2.map(y => y.asUInt)
    //                     .reduce(Cat(_, _)).litValue.toString(2) ++ "\n"
    //             ).reduce(_ ++ _)
    //     )
    // print(instr_list_map.map(
    //     x => 2.U.litValue.toString(2) ++ "\n"
    //         ).reduce(_ ++ _)
    // )
    
    // val decode_table: TruthTable = TruthTable(instr_list_map.map(
    //         x => x._1 -> BitPat(
    //                 s"b${x._2.map(y => y.asUInt)
    //                     .reduce(Cat(_, _))
    //                 }"
    //             )
    //     ),
    //     BitPat("b0")
    // )
}

// case class Pattern(val name: String, val code: BigInt) extends DecodePattern {
//   def bitPat: BitPat = BitPat("b" + code.toString(2))
// }

// object NameContainsAdd extends DecodeField[Pattern, DecoderMap.ImmType.Type] {
//   def name = "name contains 'add'"
//   def genTable(i: Pattern) = if (i.name.contains("add")) DecoderMap.ImmType.offs16 else DecoderMap.ImmType.si12
// }

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
    val flags = ListLookup(io.instr, DecoderMap.default_instr_list, DecoderMap.instr_list_map)
    // 0            1           2           3           4           5           6           7           8           9           10        
    // regB_sel,    regC_sel,   imm_type,   aluA_sel,   aluB_sel,   alu_op,     brpcAdd_sel, br_type,   ld_type,    st_type,    ill_instr
    io.aluA_sel := flags(3)
    io.aluB_sel := flags(4)
    io.alu_op := flags(5)
    io.brpcAdd_sel := flags(6)
    io.br_type := flags(7)
    io.ld_type := flags(8)
    io.st_type := flags(9)
    io.ill_instr := flags(10)

    def imm_gen(inst: UInt, imm_type: DecoderMap.ImmType.Type): UInt = {
        val imm = Wire(UInt(32.W))
        import DecoderMap.ImmType._
        imm := onehot.Mux(imm_type, Seq(
            si12    -> Cat(Fill(18, inst(21)), inst(21, 10), 0.U(2.W)),
            si20    -> Cat(inst(24, 5), 0.U(12.W)),
            ui12    -> Cat(0.U(20.W), inst(21, 10)),
            ui5     -> Cat(0.U(27.W), inst(14, 10)),
            offs26  -> Cat(Fill(4, inst(9)), inst(9, 0), inst(25, 10), 0.U(2.W)),
            offs16  -> Cat(Fill(14, inst(25)), inst(25, 10), 0.U(2.W)),
        ))
        imm
    }
    
    val (imm_type, imm_type_valid) = DecoderMap.ImmType.safe(flags(2).asUInt)
    assert(imm_type_valid)

    io.imm := imm_gen(io.instr, imm_type)
    val rd = io.instr(4, 0).asUInt
    val rj = io.instr(9, 5).asUInt
    val rk = io.instr(14, 10).asUInt
    io.ra := rj

    val (rb_type, rb_type_valid) = DecoderMap.regType.safe(flags(0).asUInt)
    assert(rb_type_valid)
    io.rb := onehot.Mux(rb_type, Seq(
        DecoderMap.regType.rj   -> rj,
        DecoderMap.regType.rk   -> rk,
        DecoderMap.regType.rd   -> rd,
        DecoderMap.regType.x    -> 0.U,
        DecoderMap.regType.num1 -> 1.U,
    ))

    val (rc_type, rc_type_valid) = DecoderMap.regType.safe(flags(1).asUInt)
    assert(rc_type_valid)
    io.rc := onehot.Mux(rc_type, Seq(
        DecoderMap.regType.rj   -> rj,
        DecoderMap.regType.rk   -> rk,
        DecoderMap.regType.rd   -> rd,
        DecoderMap.regType.x    -> 0.U,
        DecoderMap.regType.num1 -> 1.U,
    ))
}