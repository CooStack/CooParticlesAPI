package cn.coostack.cooparticlesapi.apt

import cn.coostack.cooparticlesapi.apt.annotations.TestAnnoReg
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic


@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedAnnotationTypes("cn.coostack.cooparticlesapi.apt.annotations.TestAnnoReg")
class TestAPT : AbstractProcessor() {
    init{
        println("aaa我被构建了")
    }
    companion object {
        @JvmStatic
        fun callFromModID(id: String) {
            val find = runCatching { Class.forName("cn.coostack.cooparticlesapi.generated.${id}AutoRegister") }
                .getOrNull() ?: return
            find.getMethod("registerAll").invoke(null)
        }
    }

    override fun init(processingEnv: ProcessingEnvironment?) {
        super.init(processingEnv)
        processingEnv!!.messager.printMessage(Diagnostic.Kind.NOTE,"初始化APT")
    }

    override fun process(
        annotations: Set<TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {

        val needRegistered = HashMap<String, MutableList<String>>() // key mod-id value - annotated class path

        for (element in roundEnv.getElementsAnnotatedWith(TestAnnoReg::class.java)) {
            if (element !is TypeElement) {
                continue
            }
            val anno = element.getAnnotation(TestAnnoReg::class.java)
            val modID = anno.modID
            needRegistered.getOrPut(modID) {
                mutableListOf()
            }.add(element.qualifiedName.toString())
        }
        val pack = "cn.coostack.cooparticlesapi.generated"
        needRegistered.forEach {
            val id = it.key
            val target = it.value
            val genSourceName = "${id}AutoRegister"
            // 生成对应的java代码
            val file = processingEnv.filer.createSourceFile("${pack}.${genSourceName}")
            file.openWriter().use { it ->
                it.apply {
                    appendLine("package ${pack};")
                    appendLine()
                    appendLine("public final class $genSourceName {")
                    appendLine()
                    appendLine("public static void registerAll(){")
                    target.forEach { path ->
                        appendLine("cn.coostack.cooparticlesapi.apt.TestAPTUtil.print(\"$path\");")
                    }
                    appendLine("}")
                    appendLine("}")
                }
            }
            processingEnv.messager.printMessage(Diagnostic.Kind.NOTE,"gen for $id targets: $target")

        }

        return true
    }
}