package cn.coostack.cooparticlesapi.utils

enum class NoiseMode {
    /** xyz 各自均匀随机：立方体噪声 */
    AXIS_UNIFORM,

    /** 单位球内均匀随机：球内噪声 */
    SPHERE_UNIFORM,

    /** 单位球面均匀随机：方向噪声（壳） */
    SHELL_UNIFORM
}