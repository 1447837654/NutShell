package nutcore.isa

import nutcore.{FuType, HasInstrType, HasNutCoreParameter, LSUOpType}
import chisel3._
import chisel3.util._

object RVFInstr extends HasNutCoreParameter with HasInstrType {

  def FLW                = BitPat("b?????????????????010?????0000111")
  def FSW                = BitPat("b?????????????????010?????0100111")
  def FADD_S             = BitPat("b0000000??????????????????1010011")
  def FSUB_S             = BitPat("b0000100??????????????????1010011")
  def FMUL_S             = BitPat("b0001000??????????????????1010011")
  def FDIV_S             = BitPat("b0001100??????????????????1010011")
  def FSGNJ_S            = BitPat("b0010000??????????000?????1010011")
  def FSGNJN_S           = BitPat("b0010000??????????001?????1010011")
  def FSGNJX_S           = BitPat("b0010000??????????010?????1010011")
  def FMIN_S             = BitPat("b0010100??????????000?????1010011")
  def FMAX_S             = BitPat("b0010100??????????001?????1010011")
  def FSQRT_S            = BitPat("b010110000000?????????????1010011")
  def FLE_S              = BitPat("b1010000??????????000?????1010011")
  def FLT_S              = BitPat("b1010000??????????001?????1010011")
  def FEQ_S              = BitPat("b1010000??????????010?????1010011")
  def FCVT_W_S           = BitPat("b110000000000?????????????1010011")
  def FCVT_WU_S          = BitPat("b110000000001?????????????1010011")
  def FCVT_L_S           = BitPat("b110000000010?????????????1010011")
  def FCVT_LU_S          = BitPat("b110000000011?????????????1010011")
  def FMV_X_W            = BitPat("b111000000000?????000?????1010011")
  def FCLASS_S           = BitPat("b111000000000?????001?????1010011")
  def FCVT_S_W           = BitPat("b110100000000?????????????1010011")
  def FCVT_S_WU          = BitPat("b110100000001?????????????1010011")
  def FCVT_S_L           = BitPat("b110100000010?????????????1010011")
  def FCVT_S_LU          = BitPat("b110100000011?????????????1010011")
  def FMV_W_X            = BitPat("b111100000000?????000?????1010011")
  def FMADD_S            = BitPat("b?????00??????????????????1000011")
  def FMSUB_S            = BitPat("b?????00??????????????????1000111")
  def FNMSUB_S           = BitPat("b?????00??????????????????1001011")
  def FNMADD_S           = BitPat("b?????00??????????????????1001111")

  val table = Array(
    FLW -> List(InstrFI, FuType.lsu, LSUOpType.flw),
    FSW -> List(InstrFS, FuType.lsu, LSUOpType.sw)
  )

  //  (isFp, src1Type, src2Type, src3Type, rfWen, fpWen, fuOpType, inputFunc, outputFunc)
  //  val DecodeDefault = List(N, imm, imm, imm, N, N, fadd, in_raw, out_raw)
  //  val table = Array(
  //    FLW -> List(Y, reg, imm, imm, N, Y, LSUOpType.flw, in_raw, out_raw),
  //    FSW -> List(Y, reg, fp, imm, N, N, LSUOpType.sw, in_raw, out_raw),
  //    // fp fp -> fp
  //    FADD_S   -> List(Y, fp, fp, imm, N, Y, fadd, in_unbox, out_box),
  //    FSUB_S   -> List(Y, fp, fp, imm, N, Y, fsub, in_unbox, out_box),
  //    FMUL_S   -> List(Y, fp, fp, imm, N, Y, fmul, in_unbox, out_box),
  //    FDIV_S   -> List(Y, fp, fp, imm, N, Y, fdiv, in_unbox, out_box),
  //    FMIN_S   -> List(Y, fp, fp, imm, N, Y, fmin, in_unbox, out_box),
  //    FMAX_S   -> List(Y, fp, fp, imm, N, Y, fmax, in_unbox, out_box),
  //    FSGNJ_S  -> List(Y, fp, fp, imm, N, Y, fsgnj, in_unbox, out_box),
  //    FSGNJN_S -> List(Y, fp, fp, imm, N, Y, fsgnjn, in_unbox, out_box),
  //    FSGNJX_S -> List(Y, fp, fp, imm, N, Y, fsgnjx, in_unbox, out_box),
  //    // fp -> fp
  //    FSQRT_S  -> List(Y, fp, imm, imm, N, Y, fsqrt, in_unbox, out_box),
  //    // fp fp fp -> fp
  //    FMADD_S  -> List(Y, fp, fp, fp, N, Y, fmadd, in_unbox, out_box),
  //    FNMADD_S -> List(Y, fp, fp, fp, N, Y, fnmadd, in_unbox, out_box),
  //    FMSUB_S  -> List(Y, fp, fp, fp, N, Y, fmsub, in_unbox, out_box),
  //    FNMSUB_S -> List(Y, fp, fp, fp, N, Y, fnmsub, in_unbox, out_box),
  //    // fp -> gp
  //    FCLASS_S  -> List(Y, fp, imm, imm, Y, N, fclass, in_unbox, out_raw),
  //    FMV_X_W   -> List(Y, fp, imm, imm, Y, N, fmv_f2i, in_raw, out_sext),
  //    FCVT_W_S  -> List(Y, fp, imm, imm, Y, N, f2w, in_unbox, out_sext),
  //    FCVT_WU_S -> List(Y, fp, imm, imm, Y, N, f2wu, in_unbox, out_sext),
  //    FCVT_L_S  -> List(Y, fp, imm, imm, Y, N, f2l, in_unbox, out_raw),
  //    FCVT_LU_S -> List(Y, fp, imm, imm, Y, N, f2lu, in_unbox, out_raw) ,
  //    // fp fp -> gp
  //    FLE_S -> List(Y, fp, fp, imm, Y, N, fle, in_unbox, out_raw),
  //    FLT_S -> List(Y, fp, fp, imm, Y, N, flt, in_unbox, out_raw),
  //    FEQ_S -> List(Y, fp, fp, imm, Y, N, feq, in_unbox, out_raw),
  //    // gp -> fp
  //    FMV_W_X   -> List(Y, reg, imm, imm, N, Y, fmv_i2f, in_raw, out_box),
  //    FCVT_S_W  -> List(Y, reg, imm, imm, N, Y, w2f, in_raw, out_box),
  //    FCVT_S_WU -> List(Y, reg, imm, imm, N, Y, wu2f, in_raw, out_box),
  //    FCVT_S_L  -> List(Y, reg, imm, imm, N, Y, l2f, in_raw, out_box),
  //    FCVT_S_LU -> List(Y, reg, imm, imm, N, Y, lu2f, in_raw, out_box)
  //  )
}