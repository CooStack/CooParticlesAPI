package cn.coostack.cooparticlesapi.enums

enum class DistType {
    /**
     * Listener is valid on both client and dedicated server.
     */
    BOTH,

    /**
     * 对标 Dist.CLIENT
     * 对标 EnvType.CLIENT
     */
    CLIENT,

    /**
     * 对标 Dist.DEDICATED_SERVER
     * 对标 EnvType.SERVER
     */
    SERVER
}
