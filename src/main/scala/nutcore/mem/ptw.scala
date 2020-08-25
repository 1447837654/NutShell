/**************************************************************************************
* Copyright (c) 2020 Institute of Computing Technology, CAS
* Copyright (c) 2020 University of Chinese Academy of Sciences
* 
* NutShell is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2. 
* You may obtain a copy of Mulan PSL v2 at:
*             http://license.coscl.org.cn/MulanPSL2 
* 
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER 
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR 
* FIT FOR A PARTICULAR PURPOSE.  
*
* See the Mulan PSL v2 for more details.  
***************************************************************************************/

package nutcore

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import utils._
import bus.simplebus._
import top.Settings

trait HasPtwConst extends HasNBTlbConst{
  val PtwWidth = 2
  val PtwL1EntrySize = 16
  val PtwL2EntrySize = 256
  val TlbL2EntrySize = 512

  def MakeAddr(ppn: UInt, off: UInt) = {
    require(off.getWidth == 9)
    Cat(ppn, off, 0.U(log2Up(XLEN/8).W))(PAddrBits-1, 0)
  }

  def getVpnn(vpn: UInt, idx: Int) = {
    vpn(vpnnLen*(idx+1)-1, vpnnLen*idx)
  }
}

abstract class PtwBundle extends NutCoreBundle with HasPtwConst
abstract class PtwModule extends NutCoreModule with HasPtwConst

class PteBundle extends PtwBundle{
  val reserved  = UInt(pteResLen.W)
  val ppn  = UInt(ppnLen.W)
  val rsw  = UInt(2.W)
  val perm = new Bundle {
    val d    = Bool()
    val a    = Bool()
    val g    = Bool()
    val u    = Bool()
    val x    = Bool()
    val w    = Bool()
    val r    = Bool()
    val v    = Bool()
  }

  def isPf() = {
    !perm.v || (!perm.r && perm.w)
  }

  def isLeaf() = {
    !isPf() && (perm.r || perm.x)
  }

  override def toPrintable: Printable = {
    p"ppn:0x${Hexadecimal(ppn)} perm:b${Binary(perm.asUInt)}"
  }
}

class PtwEntry(tagLen: Int) extends PtwBundle {
  val tag = UInt(tagLen.W)
  val ppn = UInt(ppnLen.W)
  val perm = new PermBundle

  // TODO: add superpage
  def hit(addr: UInt) = {
    require(addr.getWidth >= PAddrBits)
    tag === addr(PAddrBits-1, PAddrBits-tagLen)
  }

  def refill(addr: UInt, pte: UInt) {
    tag := addr(PAddrBits-1, PAddrBits-tagLen)
    ppn := pte.asTypeOf(pteBundle).ppn
    perm := pte.asTypeOf(pteBundle).perm
  }

  def genPtwEntry(addr: UInt, pte: UInt) = {
    val e = Wire(new PtwEntry(tagLen))
    e.tag := addr(PAddrBits-1, PAddrBits-tagLen)
    e.ppn := pte.asTypeOf(pteBundle).ppn
    e.perm := pte.asTypeOf(pteBundle).perm
    e
  }

  override def cloneType: this.type = (new PtwEntry(tagLen)).asInstanceOf[this.type]

  override def toPrintable: Printable = {
    p"tag:0x${Hexadecimal(tag)} ppn:0x${Hexadecimal(ppn)} perm:${perm}"
  }
}

class PtwReq extends PtwBundle {
  val vpn = UInt(vpnLen.W)

  override def toPrintable: Printable = {
    p"vpn:0x${Hexadecimal(vpn)}"
  }
}

class PtwResp extends PtwBundle {
  val entry = new TlbEntry
  val pf  = Bool() // simple pf no matter cmd

  override def toPrintable: Printable = {
    p"entry:${entry} pf:${pf}"
  }
}

class PtwIO extends PtwBundle {
  val tlb = Vec(PtwWidth, Flipped(new TlbPtwIO))
  val mem = new SimpleBusUC()
}

object ValidHold {
  def apply(infire: Bool, outfire: Bool, flush: Bool = false.B ) = {
    val valid = RegInit(false.B)
    when (outfire) { valid := false.B }
    when (infire) { valid := true.B }
    when (flush) { valid := false.B } // NOTE: the flush will flush in & out, is that ok?
    valid
  }
}

object OneCycleValid {
  def apply(fire: Bool, flush: Bool = false.B) = {
    val valid = RegInit(false.B)
    when (valid) { valid := false.B }
    when (fire) { valid := true.B }
    when (false.B) { valid := false.B }
    valid
  }
}

class PTW /*(implicit m: Module)*/ extends PtwModule {

  val io = IO(new PtwIO)

  val arb = Module(new Arbiter(new PtwReq, PtwWidth))
  arb.io.in <> VecInit(io.tlb.map(_.req))
  val arbChosen = RegEnable(arb.io.chosen, arb.io.out.fire())
  val req = RegEnable(arb.io.out.bits, arb.io.out.fire())
  val resp  = VecInit(io.tlb.map(_.resp))

  val valid = ValidHold(arb.io.out.fire(), resp(arbChosen).fire())
  val validOneCycle = OneCycleValid(arb.io.out.fire())
  arb.io.out.ready := !valid || resp(arbChosen).fire()

  val sfence = WireInit(0.U.asTypeOf(new SfenceBundle))
  val csr    = WireInit(0.U.asTypeOf(new TlbCsrBundle))
  val satp   = csr.satp
  val priv   = csr.priv
  // BoringUtils.addSink(sfence, "SfenceBundle")
  // BoringUtils.addSink(csr, "TLBCSRIO")

  // two level: l2-tlb-cache && pde/pte-cache
  // l2-tlb-cache is ram-larger-edition tlb
  // pde/pte-cache is cache of page-table, speeding up ptw

  // may seperate valid bits to speed up sfence's flush
  // Reg/Mem/SyncReadMem is not sure now
  val tagLen1 = PAddrBits - log2Up(XLEN/8)
  val tagLen2 = PAddrBits - log2Up(XLEN/8) - log2Up(PtwL2EntrySize)
  val tlbl2 = SyncReadMem(TlbL2EntrySize, new TlbEntry)
  val tlbv  = RegInit(0.U(TlbL2EntrySize.W)) // valid
  val tlbg  = RegInit(0.U(TlbL2EntrySize.W)) // global
  val ptwl1 = Reg(Vec(PtwL1EntrySize, new PtwEntry(tagLen = tagLen1)))
  val l1v   = RegInit(0.U(PtwL1EntrySize.W)) // valid
  val l1g   = VecInit((ptwl1.map(_.perm.g))).asUInt
  val ptwl2 = SyncReadMem(PtwL2EntrySize, new PtwEntry(tagLen = tagLen2)) // NOTE: the Mem could be only single port(r&w)
  val l2v   = RegInit(0.U(PtwL2EntrySize.W)) // valid
  val l2g   = RegInit(0.U(PtwL2EntrySize.W)) // global

  // fsm
  val state_idle :: state_req :: state_wait_resp :: state_wait_ready :: Nil = Enum(4)
  val state = RegInit(state_idle)
  val level = RegInit(0.U(2.W)) // 0/1/2
  val levelNext = level + 1.U
  val latch = Reg(new PtwResp)

  // mem alias
  val mem = io.mem
  val memRdata = mem.resp.bits.rdata
  val memPte = memRdata.asTypeOf(new PteBundle)
  val memValid = mem.resp.valid
  val memRespFire = mem.resp.fire()
  val memReqReady = mem.req.ready
  val memReqValid = mem.req.valid
  val memReqFire = mem.req.fire()

  /*
   * tlbl2
   */
  val (tlbHit, tlbHitData) = {
    // tlbl2 is by addr
    // TODO: optimize tlbl2'l2 tag len
    val ramData = tlbl2.read(req.vpn(log2Up(TlbL2EntrySize)-1, 0), validOneCycle)
    val vidx = RegEnable(tlbv(req.vpn(log2Up(TlbL2EntrySize)-1, 0)), validOneCycle)
    (ramData.hit(req.vpn) && vidx, ramData) // TODO: optimize tag
    // TODO: add exception and refill
  }

  /*
   * ptwl1
   */
  val l1addr = MakeAddr(satp.ppn, getVpnn(req.vpn, 2))
  val (l1Hit, l1HitData) = { // TODO: add excp
    // 16 terms may casue long latency, so divide it into 2 stage, like l2tlb
    val hitVecT = ptwl1.zipWithIndex.map{case (a,b) => a.hit(l1addr) && l1v(b) }
    val hitVec  = hitVecT.map(RegEnable(_, validOneCycle)) // TODO: could have useless init value
    val hitData = ParallelMux(hitVec zip ptwl1)
    val hit     = ParallelOR(hitVec).asBool
    (hit, hitData)
  }

  /*
   * ptwl2
   */
  val l1MemBack = memRespFire && state===state_wait_resp && level===0.U
  val l1Res = Mux(l1Hit, l1HitData.ppn, RegEnable(memPte.ppn, l1MemBack))
  val l2addr = MakeAddr(l1Res, getVpnn(req.vpn, 1))
  val (l2Hit, l2HitData) = { // TODO: add excp
    val readRam = (l1Hit && level===0.U && state===state_req) || (memRespFire && state===state_wait_resp && level===0.U)
    val ridx = l2addr(log2Up(PtwL2EntrySize)-1+log2Up(XLEN/8), log2Up(XLEN/8))
    val ramData = ptwl2.read(ridx, readRam)
    val vidx = RegEnable(l2v(ridx), readRam)
    (ramData.hit(l2addr), ramData) // TODO: optimize tag
  }

  /* ptwl3
   * ptwl3 has not cache
   * ptwl3 may be functional conflict with l2-tlb
   * if l2-tlb does not hit, ptwl3 would not hit (mostly)
   */
  val l2MemBack = memRespFire && state===state_wait_resp && level===1.U
  val l2Res = Mux(l2Hit, l2HitData.ppn, RegEnable(memPte.ppn, l1MemBack))
  val l3addr = MakeAddr(l2Res, getVpnn(req.vpn, 0))

  /*
   * fsm
   */
  val notFound = WireInit(false.B)
  switch (state) {
    is (state_idle) {
      when (valid) {
        state := state_req
        level := 0.U
      }
    }

    is (state_req) {
      when (tlbHit) {
        when (resp(arbChosen).ready) {
          state := state_idle
        }.otherwise {
          state := state_wait_ready
        }
      } .elsewhen (l1Hit && level===0.U || l2Hit && level===1.U) {
        level := levelNext // TODO: consider superpage
      } .elsewhen (memReqReady) {
        state := state_wait_resp
      }
    }

    is (state_wait_resp) {
      when (memRespFire) {
        when (memPte.isLeaf() || memPte.isPf()) {
          when (resp(arbChosen).ready) {
            state := state_idle
          }.otherwise {
            state := state_wait_ready
            latch.entry := new TlbEntry().genTlbEntry(memRdata, level, req.vpn)
            latch.pf := memPte.isPf()
          }
        }.otherwise {
          level := levelNext
          when (level=/=2.U) {
            state := state_req
          } .otherwise {
            notFound := true.B
            when (resp(arbChosen).ready) {
              state := state_idle
            } .otherwise {
              state := state_wait_ready
            }
          }
        }
      }
    }

    is (state_wait_ready) {
      when (resp(arbChosen).ready) {
        state := state_idle
      }
    }
  }

  /*
   * mem
   */
  val memAddr =  Mux(level===0.U, l1addr/*when l1Hit, DontCare, when l1miss, l1addr*/,
                 Mux(level===1.U, Mux(l2Hit, l3addr, l2addr)/*when l2Hit, l3addr, when l2miss, l2addr*/, l3addr))
  mem.req.bits.apply(
    addr = memAddr,
    cmd = SimpleBusCmd.read,
    size = "b11".U,
    wdata = 0.U,
    wmask = 0.U//,
    // user := 0.U
  )
  mem.req.valid := state === state_req && 
               ((level===0.U && !tlbHit && !l1Hit) ||
                (level===1.U && !l2Hit) ||
                (level===2.U))
  mem.resp.ready := state === state_wait_resp

  /*
   * resp
   */
  val ptwFinish = (state===state_req && tlbHit && level===0.U) || ((memPte.isLeaf() || memPte.isPf() || (!memPte.isLeaf() && level===2.U)) && memRespFire) || state===state_wait_ready
  for(i <- 0 until PtwWidth) {
    resp(i).valid := valid && arbChosen===i.U && ptwFinish // TODO: add resp valid logic
    resp(i).bits.entry := Mux(tlbHit, tlbHitData,
      Mux(state===state_wait_ready, latch.entry, new TlbEntry().genTlbEntry(memRdata, Mux(level===3.U, 2.U, level), req.vpn)))
    resp(i).bits.pf  := Mux(level===3.U || notFound, true.B, Mux(tlbHit, false.B, Mux(state===state_wait_ready, latch.pf, memPte.isPf())))
    // TODO: the pf must not be correct, check it
  }

  /*
   * refill
   */
  assert(!memRespFire || state===state_wait_resp)
  when (memRespFire && !memPte.isPf()) {
    when (level===0.U && !memPte.isLeaf) {
      val refillIdx = LFSR64()(log2Up(PtwL1EntrySize)-1,0) // TODO: may be LRU
      ptwl1(refillIdx).refill(l1addr, memRdata)
      l1v := l1v | UIntToOH(refillIdx)
    }
    when (level===1.U && !memPte.isLeaf) {
      val l2addrStore = RegEnable(l2addr, memReqFire && state===state_req && level===1.U)
      val refillIdx = getVpnn(req.vpn, 1)(log2Up(PtwL2EntrySize)-1, 0)
      ptwl2.write(refillIdx, new PtwEntry(tagLen2).genPtwEntry(l2addrStore, memRdata))
      l2v := l2v | UIntToOH(refillIdx)
      l2g := l2g | Mux(memPte.perm.g, UIntToOH(refillIdx), 0.U)
    }
    when (memPte.isLeaf()) {
      val refillIdx = getVpnn(req.vpn, 0)(log2Up(TlbL2EntrySize)-1, 0)
      tlbl2.write(refillIdx, new TlbEntry().genTlbEntry(memRdata, level, req.vpn))
      tlbv := tlbv | UIntToOH(refillIdx)
      tlbg := tlbg | Mux(memPte.perm.g, UIntToOH(refillIdx), 0.U)
    }
  }

  /* sfence
   * for ram is syncReadMem, so could not flush conditionally
   * l3 may be conflict with l2tlb??, may be we could combine l2-tlb with l3-ptw
   */
  when (sfence.valid) { // TODO: flush optionally
    when (sfence.bits.rs1/*va*/) {
      when (sfence.bits.rs2) {
        // all va && all asid
        tlbv := 0.U
        tlbg := 0.U
        l1v  := 0.U
        l2v  := 0.U
        l2g  := 0.U
      } .otherwise {
        // all va && specific asid except global
        tlbv := tlbv & tlbg
        l1v  := l1v  & l1g
        l2v  := l2v  & l2g
      }
    } .otherwise {
      when (sfence.bits.rs2) {
        // specific leaf of addr && all asid
        tlbv := tlbv & ~UIntToOH(sfence.bits.addr(log2Up(TlbL2EntrySize)-1+offLen, 0+offLen))
        tlbg := tlbg & ~UIntToOH(sfence.bits.addr(log2Up(TlbL2EntrySize)-1+offLen, 0+offLen))
      } .otherwise {
        // specific leaf of addr && specific asid
        tlbv := tlbv & (~UIntToOH(sfence.bits.addr(log2Up(TlbL2EntrySize)-1+offLen, 0+offLen)) | tlbg)
      }
    }
  }
  assert(level=/=3.U)

  Debug(validOneCycle, p"**New Ptw Req from ${arbChosen}: (v:${validOneCycle} r:${arb.io.out.ready}) vpn:0x${Hexadecimal(req.vpn)}\n")
  Debug(resp(arbChosen).fire(), p"**Ptw Resp to ${arbChosen}: (v:${resp(arbChosen).valid} r:${resp(arbChosen).ready}) entry:${resp(arbChosen).bits.entry} pf:${resp(arbChosen).bits.pf}\n")

  Debug(sfence.valid, p"Sfence: sfence instr here ${sfence.bits}\n")
  Debug(valid, p"CSR: ${csr}\n")

  Debug(valid, p"vpn2:0x${Hexadecimal(getVpnn(req.vpn, 2))} vpn1:0x${Hexadecimal(getVpnn(req.vpn, 1))} vpn0:0x${Hexadecimal(getVpnn(req.vpn, 0))}\n")
  Debug(valid, p"state:${state} level:${level} tlbHit:${tlbHit} l1addr:0x${Hexadecimal(l1addr)} l1Hit:${l1Hit} l2addr:0x${Hexadecimal(l2addr)} l2Hit:${l2Hit}  l3addr:0x${Hexadecimal(l3addr)} memReq(v:${memReqValid} r:${memReqReady})\n")

  Debug(memRespFire, p"mem req fire addr:0x${Hexadecimal(memAddr)}\n")
  Debug(memRespFire, p"mem resp fire rdata:0x${Hexadecimal(memRdata)} Pte:${memPte}\n")
}