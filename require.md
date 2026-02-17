# 需求
## 快捷生成Composition
1. 使用类似pointsbuilder卡片的方式编辑
2. 左边是卡片框，右边是预览框， 代码生成在该页面的第二子页面
3. 可选是否实时生成代码， 要有pointsbuilder同款的 设置页面和快捷键页面

## 项目选项
可以修改项目名字 （代表生成的是项目名.kt）
可以选择类型 
如果选择ParticleComposition 那么就继承 AutoParticleComposition
如果选择 SequencedParticleComposition 那么就继承 AutoSequencedParticleComposition

如果是SequencedParticleComposition 可以添加animate
animate格式如下
```kotlin
init{
    // 第一个参数数字代表生成的点个数 （只能选择常数整数）
    // 第二个是条件表达式
    // 条件表达式里的可选变量是 composition的全局变量 + composition默认提供的变量
    animate.addAnimate(1) { age > 1 }
        .addAnimate(1) { age > 6 }
        .addAnimate(2) { age > 11 }
        .addAnimate(1) { age > 16 }
}
```

可以设置消散延迟 单位tick:
```kotlin
init{
    setDisabledInterval(常数)
}

```
## 卡片设置
卡片是这样设置的
1. 绑定一个pointsbuilder 或者point
2. 设置点

## 点设置
1. 可以设置点类型 ParticleShapeComposition 或者 SequencedParticleShapeComposition 或者 single
2. 如果是single， 点选项改为如下
   1. `addParticleInstanceInit` 对应粒子选项(color，size，age，alpha，textureSheet)等
   2. `addParticleControlerInstanceInit` 可以设置粒子controler的tick变化
      设置方式如下（kotlin)
      ```kotlin
            data.addParticleControlerInstanceInit{
                it.addPreTickAction{
                    // 可以获取和修改如下参数
                    // color，size，age，alpha，textureSheet
                }
            }
      ```
3. 卡片可以设置如下内容
    1. 添加PointsBuilder， 编辑PointsBuilder时，会跳转到pointsbuilder相似的编辑器
       > 注意 这里的pointsbuilder不是直接跳转到pointsbuilder.html 而是复制pointsbuilder.html相同的页面
       >
       > 然后加入一些标识（防止和pointsbuilder数据搞混） 然后这个 "builder" 可以直接返回到主页面
       >
       > 所有的操作手感应该和pointsbuilder完全一致
    2. 可以加入绕轴旋转，rotateToWithAngle
       > 如果设置了rotateToWithAngle，那么就要指定to， 可以指定设定的全局变量
    3. 可以设置生长动画
    4. 如果是SequencedParticleShapeComposition 那么可以设置animate
    
## 一些补充
ControlableParticle对象的实现和 single要输入的ParticleDisplayer.withSingle 要输入的effect我都放在了参考/particle 里面
ParticleDisplayer我也放在了参考里面， 只考虑withComposition和withSingle

依旧是 中键旋转，滚轮缩放，右键平移
然后左键用来框选预览的点到对应的卡片


# 使用 / 生成代码 举例
## 项目设置
项目名字: TestComposition
## 项目卡片设置
类型: ParticleComposition
### display行为
#### display action 1
rotateToWithAngle卡片

设定

rotateSpeed= 10

to = direction.asRelative()

### 全局变量列表
color - Vec3 - Vector3f(0,1,1)

size - Float - 1

radius - Double - 2

countPow - int - 4

direction - Vec3 // to只接收RelativeLocation和Vec3 如果写Vec3 那么上面就要变成xxx.asRelative()
### 全局非同步常量列表
options - Int - 4

## 卡片编辑
### PointsBuilder <- 导入了一个builder（这里比如只有一个ball）
    PointsBuilder的addBall卡片的radius参数设置为 这里的全局radius
### Data设置
Effect -> Single (ControlableEnchantmentEffect)
### Single设置
#### particle init
currentAge = Random.nextInt(lifetime) // 可以选择定值 也可以选择随机值
color = this@TestComposition.color
#### controler init
定义局部变量 tick - boolean - true
##### tick action 1
写表达式
```javascript
if (particle.particleAlpha >= 0.9) {
    tick = false
}
if (particle.particleAlpha <= 0.2) {
    tick = true
}
particle.particleAlpha += tick ? 0.05 : -0.05
```

## 最后生成的代码如下
```kotlin

@CooAutoRegister
class TestComposition(position: Vec3, world: Level? = null) : AutoParticleComposition(position, world) {
    @CodecField
    var color: Vector3f = Vector3f(0f,1f,1f)

    @CodecField
    var size: Float = 1f

    @CodecField
    var r: Double = 2.0

    @CodecField
    var countPow: Int = 4
    val options: Int = 4

    override fun getParticles(): Map<CompositionData, RelativeLocation> {
        return PointsBuilder().addBall(r, countPow * options / 2)
            .createWithCompositionData {
                CompositionData().setDisplayerSupplier {
                    ParticleDisplayer.withSingle(
                        ControlableEnchantmentEffect(it)
                    )
                }.addParticleInstanceInit {
                    color = this@TestComposition.color 
                    size = this@TestComposition.size
                    currentAge = Random.nextInt(lifetime)
                }.addParticleControlerInstanceInit {
                    var tick = true
                    addPreTickAction { // 注意下面是由js翻译过来的
                        if (particle.particleAlpha >= 0.9f) {
                            tick = false
                        }
                        if (particle.particleAlpha <= 0.2f) {
                            tick = true
                        }
                        particle.particleAlpha += if (tick) 0.05f else -0.05f
                    }
                }
            }
    }

    override fun onDisplay() {
        addPreTickAction { // 如果是卡片就直接加， 如果是表达式就翻译成kotlin
            rotateToWithAngle(direction,speed / 180 * PI)
        }
    }
}
```
