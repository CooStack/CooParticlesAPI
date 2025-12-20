package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.apt.annotations.TestAnnoReg
import io.github.classgraph.ScanResult

@TestAnnoReg(CooParticlesConstants.MOD_ID)
class TestA {
    fun test(){
    }
}