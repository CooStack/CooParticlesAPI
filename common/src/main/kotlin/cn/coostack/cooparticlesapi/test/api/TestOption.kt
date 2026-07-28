package cn.coostack.cooparticlesapi.test.api

enum class TestReviewMode {
    AUTO,
    MANUAL_VISUAL
}

/**
 * 一个可由测试组调度的测试项。
 *
 * [T] 是 [applyTo] 接收的目标类型。实现类必须让 [paramTarget] 返回同一类型，不能用无关对象代替。
 *
 * @param T 参数应用目标的类型
 */
interface TestOption<T : Any> {
    fun start()

    fun stop()

    /**
     * 该测试是否已经完成 （不管报错还是正常完成）
     *
     * @return false 代表该测试项已经无效 则已经完成
     */
    fun isValid(): Boolean

    fun onFailed()

    fun onSuccess()

    fun optionID(): String

    fun doTick()

    fun reviewMode(): TestReviewMode {
        return TestReviewMode.AUTO
    }

    fun reviewDescription(): String? {
        return null
    }

    /**
     * 给当前测试项声明一个可配置参数，并返回当前测试项以便继续链式调用。
     *
     * [type] 决定参数 ID、显示名称、输入控件、文本解析方式和运行时类型；[defaultValue]
     * 是控制器没有提供覆盖值时使用的值。连续调用本方法即可添加多个参数。同一个 ID
     * 再次声明时，后一次声明会替换之前的类型和默认值。
     *
     * 参数声明不会直接修改测试对象。方块控制器创建 Option 后，会先调用
     * [applyOptionParams] 写入保存的参数文本，再执行通过 [applyTo] 注册的应用逻辑，最后才启动测试。
     *
     * 基本用法：
     * ```kotlin
     * option
     *     .applyParam(FloatTestOptionValue("radius", "半径"), 1.0f)
     *     .applyParam(Vector3fTestOptionValue("color", "颜色").asColor(), Vector3f(1f))
     * ```
     *
     * @param P 参数值类型
     * @param type 参数类型与编辑信息
     * @param defaultValue 没有覆盖值时使用的默认值
     * @return 当前测试项
     */
    fun <P : Any> applyParam(type: TestOptionParamType<P>, defaultValue: P): TestOption<T> {
        return TestOptionParamSupport.applyParam(this, type, defaultValue)
    }

    /**
     * 注册参数应用逻辑，并返回当前测试项以便继续链式调用。
     *
     * 此处的 lambda 接收者是当前 [TestOption]，因此可以用 [getParam] 读取已经完成解析的参数。
     * lambda 参数是 [paramTarget] 的返回值。例如，`SimpleRendererEntityOption` 返回它持有的
     * `RenderEntity`。回调只在 [applyOptionParams] 执行后运行，不会在声明参数或注册回调时提前运行。
     *
     * 对 `SimpleRendererEntityOption` 应用实体属性时，可以这样写：
     * ```kotlin
     * SimpleRendererEntityOption(MyRenderEntity(level, position), -1, "示例")
     *     .applyParam(FloatTestOptionValue("radius", "半径"), 1.0f)
     *     .applyTo { entity ->
     *         entity.radius = getParam<Float>("radius") ?: 1.0f
     *     }
     * ```
     *
     * @param action 参数应用逻辑
     * @return 当前测试项
     */
    fun applyTo(action: TestOption<T>.(T) -> Unit): TestOption<T> {
        return TestOptionParamSupport.applyTo(this, action)
    }

    /**
     * 应用一组由参数 ID 到文本值的覆盖，并返回当前测试项。
     *
     * 每个文本值会按 [applyParam] 声明的类型解析；缺失或解析失败的值回退到默认值。
     * 所有值准备完成后，本方法会依次执行此前通过 [applyTo] 注册的回调，并返回当前对象，
     * 因此可以写成 `option.applyOptionParams(values).start()`。[applyTo] 必须在本方法之前注册；
     * 调用完成后新增的回调不会自动补执行。
     *
     * @param values 参数 ID 到文本值的映射
     * @return 当前测试项
     */
    fun applyOptionParams(values: Map<String, String>): TestOption<T> {
        return TestOptionParamSupport.applyOptionParams(this, values)
    }

    fun optionParamSpecs(): List<TestOptionParamSpec<*>> {
        return TestOptionParamSupport.optionParamSpecs(this)
    }

    fun optionParamValues(): Map<String, String> {
        return TestOptionParamSupport.optionParamValues(this)
    }

    fun <P : Any> getParam(id: String): P? {
        return TestOptionParamSupport.getParam(this, id)
    }

    fun <P : Any> getParamOrThrow(id: String) = getParam<P>(id)!!

    /**
     * 返回 [applyTo] 使用的目标。
     *
     * 返回值必须与 [T] 一致。禁止返回临时替代对象，否则已注册的参数应用逻辑会修改错误实例。
     *
     * @return 当前测试项持有的参数应用目标
     */
    fun paramTarget(): T
}
