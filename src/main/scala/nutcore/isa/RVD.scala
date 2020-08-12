package nutcore.isa

import nutcore.{FuType, HasInstrType, HasNutCoreParameter, LSUOpType}
import chisel3._
import chisel3.util._


object RVDInstr extends HasNutCoreParameter with HasInstrType {

  def FADD_D             = BitPat("b0000001??????????????????1010011")
  def FSUB_D             = BitPat("b0000101??????????????????1010011")
  def FMUL_D             = BitPat("b0001001??????????????????1010011")
  def FDIV_D             = BitPat("b0001101??????????????????1010011")
  def FSGNJ_D            = BitPat("b0010001??????????000?????1010011")
  def FSGNJN_D           = BitPat("b0010001??????????001?????1010011")
  def FSGNJX_D           = BitPat("b0010001??????????010?????1010011")
  def FMIN_D             = BitPat("b0010101??????????000?????1010011")
  def FMAX_D             = BitPat("b0010101??????????001?????1010011")
  def FCVT_S_D           = BitPat("b010000000001?????????????1010011")
  def FCVT_D_S           = BitPat("b010000100000?????????????1010011")
  def FSQRT_D            = BitPat("b010110100000?????????????1010011")
  def FLE_D              = BitPat("b1010001??????????000?????1010011")
  def FLT_D              = BitPat("b1010001??????????001?????1010011")
  def FEQ_D              = BitPat("b1010001??????????010?????1010011")
  def FCVT_W_D           = BitPat("b110000100000?????????????1010011")
  def FCVT_WU_D          = BitPat("b110000100001?????????????1010011")
  def FCVT_L_D           = BitPat("b110000100010?????????????1010011")
  def FCVT_LU_D          = BitPat("b110000100011?????????????1010011")
  def FMV_X_D            = BitPat("b111000100000?????000?????1010011")
  def FCLASS_D           = BitPat("b111000100000?????001?????1010011")
  def FCVT_D_W           = BitPat("b110100100000?????????????1010011")
  def FCVT_D_WU          = BitPat("b110100100001?????????????1010011")
  def FCVT_D_L           = BitPat("b110100100010?????????????1010011")
  def FCVT_D_LU          = BitPat("b110100100011?????????????1010011")
  def FMV_D_X            = BitPat("b111100100000?????000?????1010011")
  def FLD                = BitPat("b?????????????????011?????0000111")
  def FSD                = BitPat("b?????????????????011?????0100111")
  def FMADD_D            = BitPat("b?????01??????????????????1000011")
  def FMSUB_D            = BitPat("b?????01??????????????????1000111")
  def FNMSUB_D           = BitPat("b?????01??????????????????1001011")
  def FNMADD_D           = BitPat("b?????01??????????????????1001111")

  val table = Array(
    FLD -> List(InstrFI, FuType.lsu, LSUOpType.ld),
    FSD -> List(InstrFS, FuType.lsu, LSUOpType.sd)
  )

  //  (isFp, src1Type, src2Type, src3Type, rfWen, fpWen, fuOpType, inputFunc, outputFunc)
  //  val table = Array(

  //    FLD -> List(Y, reg, imm, imm, N, Y, LSUOpType.ld, in_raw, out_raw),
  //    C_FLD -> List(Y, reg, imm, imm, N, Y, LSUOpType.ld, in_raw, out_raw),
  //    C_FLDSP -> List(Y, reg, imm, imm, N, Y, LSUOpType.ld, in_raw, out_raw),
  //    FSD -> List(Y, reg, fp, imm, N, N, LSUOpType.sd, in_raw, out_raw),
  //    C_FSD -> List(Y, reg, fp, imm, N, N, LSUOpType.sd, in_raw, out_raw),
  //    C_FSDSP -> List(Y, reg, fp, imm, N, N, LSUOpType.sd, in_raw, out_raw),
  //    // fp fp -> fp
  //    FADD_D   -> List(Y, fp, fp, imm, N, Y, fadd, in_raw, out_raw),
  //    FSUB_D   -> List(Y, fp, fp, imm, N, Y, fsub, in_raw, out_raw),
  //    FMUL_D   -> List(Y, fp, fp, imm, N, Y, fmul, in_raw, out_raw),
  //    FDIV_D   -> List(Y, fp, fp, imm, N, Y, fdiv, in_raw, out_raw),
  //    FMIN_D   -> List(Y, fp, fp, imm, N, Y, fmin, in_raw, out_raw),
  //    FMAX_D   -> List(Y, fp, fp, imm, N, Y, fmax, in_raw, out_raw),
  //    FSGNJ_D  -> List(Y, fp, fp, imm, N, Y, fsgnj, in_raw, out_raw),
  //    FSGNJN_D -> List(Y, fp, fp, imm, N, Y, fsgnjn, in_raw, out_raw),
  //    FSGNJX_D -> List(Y, fp, fp, imm, N, Y, fsgnjx, in_raw, out_raw),
  //    // fp -> fp
  //    FSQRT_D  -> List(Y, fp, imm, imm, N, Y, fsqrt, in_raw, out_raw),
  //    FCVT_S_D -> List(Y, fp, imm, imm, N, Y, d2s, in_raw, out_box),
  //    FCVT_D_S -> List(Y, fp, imm, imm, N, Y, s2d, in_unbox, out_raw),
  //    // fp fp fp -> fp
  //    FMADD_D  -> List(Y, fp, fp, fp, N, Y, fmadd, in_raw, out_raw),
  //    FNMADD_D -> List(Y, fp, fp, fp, N, Y, fnmadd, in_raw, out_raw),
  //    FMSUB_D  -> List(Y, fp, fp, fp, N, Y, fmsub, in_raw, out_raw),
  //    FNMSUB_D -> List(Y, fp, fp, fp, N, Y, fnmsub, in_raw, out_raw),
  //    // fp -> gp
  //    FCLASS_D  -> List(Y, fp, imm, imm, Y, N, fclass, in_raw, out_raw),
  //    FMV_X_D   -> List(Y, fp, imm, imm, Y, N, fmv_f2i, in_raw, out_raw),
  //    FCVT_W_D  -> List(Y, fp, imm, imm, Y, N, f2w, in_raw, out_sext),
  //    FCVT_WU_D -> List(Y, fp, imm, imm, Y, N, f2wu, in_raw, out_sext),
  //    FCVT_L_D  -> List(Y, fp, imm, imm, Y, N, f2l, in_raw, out_raw),
  //    FCVT_LU_D -> List(Y, fp, imm, imm, Y, N, f2lu, in_raw, out_raw),
  //    // fp fp -> gp
  //    FLE_D -> List(Y, fp, fp, imm, Y, N, fle, in_raw, out_raw),
  //    FLT_D -> List(Y, fp, fp, imm, Y, N, flt, in_raw, out_raw),
  //    FEQ_D -> List(Y, fp, fp, imm, Y, N, feq, in_raw, out_raw),
  //    // gp -> fp
  //    FMV_D_X   -> List(Y, reg, imm, imm, N, Y, fmv_i2f, in_raw, out_raw),
  //    FCVT_D_W  -> List(Y, reg, imm, imm, N, Y, w2f, in_raw, out_raw),
  //    FCVT_D_WU -> List(Y, reg, imm, imm, N, Y, wu2f, in_raw, out_raw),
  //    FCVT_D_L  -> List(Y, reg, imm, imm, N, Y, l2f, in_raw, out_raw),
  //    FCVT_D_LU -> List(Y, reg, imm, imm, N, Y, lu2f, in_raw, out_raw)
  //  )
}