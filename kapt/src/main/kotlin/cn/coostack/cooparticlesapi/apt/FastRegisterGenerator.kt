package cn.coostack.cooparticlesapi.apt

import cn.coostack.cooparticlesapi.apt.annotations.EmittersFastRegister
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("cn.coostack.cooparticlesapi.apt.annotations.EmittersFastRegister")
class FastRegisterGenerator: AbstractProcessor() {
    override fun process(
        elements: Set<TypeElement>,
        env: RoundEnvironment
    ): Boolean {
        val needRegister = HashMap<String, MutableList<String>>() // key mod-id value - annotated class path

        for (element in env.getElementsAnnotatedWith(EmittersFastRegister::class.java)) {
            if (element !is TypeElement) {
                continue
            }
            val anno = element.getAnnotation(EmittersFastRegister::class.java)
            val modID = anno.modID
            needRegister.getOrPut(modID) {
                mutableListOf()
            }.add(element.qualifiedName.toString())
        }

        needRegister.forEach {
            val id = it.key
                .replace("-","_") // 防止出现一些不规范的MOD ID
            val target = it.value
            val genSourceName = "AutoRegister${id}"
            val file = processingEnv.filer.createSourceFile("${FastRegisterUtil.GENERATE_PACK}.$genSourceName")
            file.openWriter().use { writer ->
                writer.apply {
                    appendLine(
                        """
                            package ${FastRegisterUtil.GENERATE_PACK};
                            public final class $genSourceName {
                                public static void registerAll(){
                        """.trimIndent()
                    )
                    // 神人编译器导致我全程只能使用反射 :)
                    // 不知道为什么 clean build 成功
                    // 纯build就会失败
                    target.forEach { path->
                        appendLine("""
                                try{
                                    var clazz = Class.forName("$path");
                                    var refUtilClass = Class.forName("cn.coostack.cooparticlesapi.utils.ReflectUtil");
                                    var vec3Cls = (Class<?>)refUtilClass.getMethod("getVec3Class").invoke(null);
                                    var levelCls = (Class<?>)refUtilClass.getMethod("getLevelClass").invoke(null);
                                    var streamCodecCls = (Class<?>)refUtilClass.getMethod("getStreamCodecClass").invoke(null);
                                    var zero = vec3Cls.getDeclaredField("ZERO").get(null);
                                    Object ins;
                                    try{
                                        var cons = clazz.getDeclaredConstructor(vec3Cls,levelCls); // 尝试获取双参数的构造函数
                                        cons.setAccessible(true);
                                        ins = cons.newInstance(zero,null);
                                    }catch(Exception e) {
                                        var cons = clazz.getDeclaredConstructor();
                                        cons.setAccessible(true);
                                        ins = cons.newInstance();
                                    }
                                    var id = clazz.getMethod("getEmittersID").invoke(ins);
                                    var codec = clazz.getMethod("getCodec").invoke(ins);
                                    System.out.println("try register emitters: " + id + " success");
                                    Class.forName("cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager")
                                        .getMethod("register",String.class,streamCodecCls)
                                        .invoke(null,id,codec);// success
                                }catch(Exception e){
                                    e.printStackTrace(); // error
                                }
                        """.trimIndent())
                    }
                    appendLine("    }")
                    appendLine("}")
                }
            }
        }


        return false // 交给总注册器
    }
}