package noop

import chisel3._
import chisel3.util._

import bus.simplebus.{SimpleBus, SimpleBusCrossbar}
import bus.axi4._
import utils._

trait NOOPConfig {
  val HasIcache = true
  val HasDcache = true
  val HasMExtension = true
  val HasDiv = true
  val debug = false
}

object AddressSpace {
  // [start, end)
  def mmio = List((0x40000000L, 0x50000000L))
  def dram = (0x80000000L, 0x90000000L)

  def isMMIO(addr: UInt) = mmio.map(range => (addr >= range._1.U && addr < range._2.U)).reduce(_ || _)
}

class NOOP(hasPerfCnt: Boolean = false) extends Module with NOOPConfig with HasCSRConst with HasFuType {
  val io = IO(new Bundle {
    val imem = new AXI4
    val dmem = new AXI4
    val mmio = new SimpleBus
    val difftest = new DiffTestIO
  })

  val ifu = Module(new IFU)
  val idu = Module(new IDU)
  val isu = Module(new ISU)
  val exu = Module(new EXU)
  val wbu = Module(new WBU)

  val icacheHit = WireInit(false.B)
  io.imem <> (if (HasIcache) {
    val icache = Module(new Cache(ro = true, name = "icache"))
    icacheHit := icache.io.hit
    icache.io.in <> ifu.io.imem
    icache.io.flush := ifu.io.flushVec(0)
    ifu.io.pc := icache.io.addr
    icache.io.mem
  } else { ifu.io.imem.toAXI4() })

  def pipelineConnect2[T <: Data](left: DecoupledIO[T], right: DecoupledIO[T],
    isFlush: Bool, entries: Int = 2, pipe: Boolean = false) = {
    right <> FlushableQueue(left, isFlush,  entries = entries, pipe = pipe)
  }

  pipelineConnect2(ifu.io.out, idu.io.in, ifu.io.flushVec(0))
  PipelineConnect(idu.io.out, isu.io.in, isu.io.out.fire(), ifu.io.flushVec(1))
  PipelineConnect(isu.io.out, exu.io.in, exu.io.out.fire(), ifu.io.flushVec(2))
  PipelineConnect(exu.io.out, wbu.io.in, true.B, ifu.io.flushVec(3))
  isu.io.flush := ifu.io.flushVec(2)
  exu.io.flush := ifu.io.flushVec(3)

  if (debug) {
    printf("%d: flush = %b, ifu:(%d,%d), idu:(%d,%d), isu:(%d,%d), exu:(%d,%d), wbu: (%d,%d)\n",
      GTimer(), ifu.io.flushVec.asUInt, ifu.io.out.valid, ifu.io.out.ready,
      idu.io.in.valid, idu.io.in.ready, isu.io.in.valid, isu.io.in.ready,
      exu.io.in.valid, exu.io.in.ready, wbu.io.in.valid, wbu.io.in.ready)
    when (ifu.io.out.valid) { printf("IFU: pc = 0x%x, instr = 0x%x\n", ifu.io.out.bits.pc, ifu.io.out.bits.instr) }
    when (idu.io.in.valid) { printf("IDU: pc = 0x%x, instr = 0x%x\n", idu.io.in.bits.pc, idu.io.in.bits.instr) }
    when (isu.io.in.valid) { printf("ISU: pc = 0x%x\n", isu.io.in.bits.pc) }
    when (exu.io.in.valid) { printf("EXU: pc = 0x%x\n", exu.io.in.bits.pc) }
    when (wbu.io.in.valid) { printf("WBU: pc = 0x%x\n", wbu.io.in.bits.pc) }
  }

  wbu.io.brIn <> exu.io.br
  isu.io.wb <> wbu.io.wb
  ifu.io.br <> wbu.io.brOut
  // forward
  isu.io.forward <> exu.io.forward
  exu.io.wbData := wbu.io.wb.rfWdata

  val dcacheHit = WireInit(false.B)
  io.dmem <> (if (HasDcache) {
    val dcache = Module(new Cache(ro = false, name = "dcache"))
    dcacheHit := dcache.io.hit
    dcache.io.in <> exu.io.dmem
    dcache.io.flush := false.B
    dcache.io.mem
  } else { exu.io.dmem.toAXI4() })
  io.mmio <> exu.io.mmio

  // csr
  val csr = Module(new CSR(hasPerfCnt))
  csr.access(
    valid = exu.io.csr.isCsr,
    src1 = exu.io.in.bits.data.src1,
    src2 = exu.io.in.bits.data.src2,
    func = exu.io.in.bits.ctrl.fuOpType
  )
  exu.io.csr.in <> csr.io.out
  exu.io.csrjmp <> csr.io.csrjmp
  csr.io.pc := exu.io.in.bits.pc
  csr.io.isInvOpcode := exu.io.in.bits.ctrl.isInvOpcode

  // perfcnt
  csr.io.perfCntCond.map( _ := false.B )
  csr.setPerfCnt(Mcycle, true.B)
  csr.setPerfCnt(Minstret, wbu.io.writeback)

  if (hasPerfCnt) {
    csr.setPerfCnt(MImemStall, ifu.io.imemStall)
    // instruction types
    csr.setPerfCnt(MALUInstr, exu.io.csr.instrType(FuAlu))
    csr.setPerfCnt(MBRUInstr, exu.io.csr.instrType(FuBru))
    csr.setPerfCnt(MLSUInstr, exu.io.csr.instrType(FuLsu))
    csr.setPerfCnt(MMDUInstr, exu.io.csr.instrType(FuMdu))
    csr.setPerfCnt(MCSRInstr, exu.io.csr.instrType(FuCsr))
    // load/store before dcache
    csr.setPerfCnt(MLoadInstr, exu.io.dmem.isRead() && exu.io.dmem.req.fire())
    csr.setPerfCnt(MLoadStall, BoolStopWatch(exu.io.dmem.isRead(), exu.io.dmem.resp.fire()))
    csr.setPerfCnt(MStoreStall, BoolStopWatch(exu.io.dmem.isWrite(), exu.io.dmem.resp.fire()))
    // mmio
    csr.setPerfCnt(MmmioInstr, io.mmio.req.fire())
    // cache
    csr.setPerfCnt(MIcacheHit, icacheHit)
    csr.setPerfCnt(MDcacheHit, dcacheHit)
    // mul
    csr.setPerfCnt(MmulInstr, exu.io.csr.isMul)
    // pipeline wait
    csr.setPerfCnt(MIFUFlush, ifu.io.flushVec.orR())
    csr.setPerfCnt(MRAWStall, isu.io.rawStall)
    csr.setPerfCnt(MEXUBusy, isu.io.exuBusy)
  }

  // monitor
  val mon = Module(new Monitor)
  mon.io.clk := clock
  mon.io.isNoopTrap := exu.io.in.bits.ctrl.isNoopTrap && exu.io.in.valid
  mon.io.reset := reset
  mon.io.trapCode := exu.io.in.bits.data.src1
  mon.io.trapPC := exu.io.in.bits.pc
  mon.io.cycleCnt := csr.io.sim.cycleCnt
  mon.io.instrCnt := csr.io.sim.instrCnt

  // difftest
  // latch writeback signal to let register files and pc update
  io.difftest.commit := RegNext(wbu.io.writeback)
  isu.io.difftestRegs.zipWithIndex.map { case(r, i) => io.difftest.r(i) := r }
  io.difftest.thisPC := RegNext(wbu.io.in.bits.pc)
}
