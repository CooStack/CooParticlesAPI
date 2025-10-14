package cn.coostack.cooparticlesapi.apt.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class TestAnnoReg(val modID: String)
