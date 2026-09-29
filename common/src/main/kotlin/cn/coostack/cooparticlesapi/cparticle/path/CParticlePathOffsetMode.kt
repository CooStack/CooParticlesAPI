package cn.coostack.cooparticlesapi.cparticle.path

/**
 * 路径对逐粒子出生偏移的处理方式，与朝向开关 faceMotion 独立。
 *
 * [NONE] 保留既有严格路径约束，所有粒子使用命令指定的半径与相位。
 * [BIRTH_POSITION] 保留出生点相对出生进度处路径的平移偏移，适合平行分散的轨迹。
 * [BIRTH_FRAME] 将出生偏移投影到路径的法线、副法线和切线基，沿路径搬运；
 * 横向偏移按命令角速度旋转，因此随机出生位置自然产生不同半径和相位，纵向偏移也保留。
 *
 * 出生状态由粒子独立持有，不在每 tick 重新随机。出生年龄处的位置保持不变，
 * offsetRadius 的显式环绕从该参考位置叠加；修改路径会重新解释出生偏移。
 * 这些模式都是位置约束，不是吸引力、碰撞响应或 Blender 物理模拟的复刻。
 * [wireValue] 属于 CPU/GPU 命令 ABI，不能依赖枚举顺序或重新编号。
 */
enum class CParticlePathOffsetMode(
    /** 命令中偏移模式的固定数值标识。 */
    val wireValue: Int,
) {
    NONE(0),
    BIRTH_POSITION(1),
    BIRTH_FRAME(2),
}
