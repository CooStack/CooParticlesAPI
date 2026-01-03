package cn.coostack.cooparticlesapi.extend

import kotlin.math.PI

const val PIF = 3.1415927F

fun radianF(angle: Float): Float = angle * PIF / 180

fun radianF(angle: Double): Float = angle.toFloat() * PIF / 180

fun radianD(angle: Double): Double = angle * PI / 180

fun radianD(angle: Float): Double = angle * PI / 180

