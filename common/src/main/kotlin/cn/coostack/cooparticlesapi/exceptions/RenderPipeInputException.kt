package cn.coostack.cooparticlesapi.exceptions

class RenderPipeInputException(inputFBO: Int, inputChannel: Int) :
    Exception("渲染管线 fbo: $inputFBO 的颜色通道$inputChannel 只能有一个输入") {
}