package cn.coostack.cooparticlesapi.renderer.runtime

/**
 * 本地 effect chain 中的一个可执行步骤。
 *
 * 每个步骤通常对应一次局部后处理、一次中间目标拷贝，
 * 或者一次 world pass 后的附加渲染操作。
 */
fun interface LocalEffectStep {
    /**
     * 执行当前这一步本地效果。
     */
    fun execute()
}
