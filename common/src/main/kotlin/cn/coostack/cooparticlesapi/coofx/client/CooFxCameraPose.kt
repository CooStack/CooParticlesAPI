package cn.coostack.cooparticlesapi.coofx.client

import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledCamera
import org.joml.Matrix4f

/** 客户端采样后的资产 camera 世界姿态；投影参数保留在 [camera] 中。 */
data class CooFxCameraPose(
    val camera: CooFxCompiledCamera,
    val worldMatrix: Matrix4f,
) {
    init {
        require(worldMatrix.isFinite) { "CooFX camera world matrix must be finite" }
    }
}
