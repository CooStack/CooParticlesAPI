package cn.coostack.cooparticlesapi.apt

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

/**
 * 事件自动注册
 */
class ObserverAutoRegisterGenerator : AbstractProcessor() {
    override fun process(
        annotations: Set<TypeElement>,
        env: RoundEnvironment
    ): Boolean {


        return true
    }
}