package cn.coostack.cooparticlesapi.compat

/**
 * 不依赖图形上下文的 Iris program 能力门禁与单次提交决策。
 */
internal object IrisProgramDrawPolicy {
    /**
     * 在任何 GL 绑定改变前排除不支持的 image 或缺少原拓扑回退的细分程序。
     * @return 可安全执行的单次绘制路线，不可用时保留后续提交机会
     */
    fun route(
        usesTessellation: Boolean,
        hasActiveImages: Boolean,
        imageLoadStoreSupported: Boolean,
        fallbackAvailable: Boolean,
    ): IrisProgramDrawRoute = when {
        hasActiveImages && !imageLoadStoreSupported -> IrisProgramDrawRoute.UNAVAILABLE
        usesTessellation && !fallbackAvailable -> IrisProgramDrawRoute.UNAVAILABLE
        usesTessellation -> IrisProgramDrawRoute.OWN_SHADER
        else -> IrisProgramDrawRoute.NATIVE
    }

    /**
     * 同步执行已选路线；只在对应回调正常返回后报告提交成功。
     *
     * @param route 绑定 Iris 目标前选定的路线
     * @param fallback 保留本库原始几何拓扑的回调，不得在返回后继续绘制
     * @param draw 使用 Iris program 的普通几何回调
     * @return 恰有一个回调正常完成时为 true；不可用时为 false，异常原样传播
     */
    fun submit(
        route: IrisProgramDrawRoute,
        fallback: (() -> Unit)?,
        draw: () -> Unit,
    ): Boolean {
        when (route) {
            IrisProgramDrawRoute.NATIVE -> draw()
            IrisProgramDrawRoute.OWN_SHADER -> (fallback ?: return false).invoke()
            IrisProgramDrawRoute.UNAVAILABLE -> return false
        }
        return true
    }
}
