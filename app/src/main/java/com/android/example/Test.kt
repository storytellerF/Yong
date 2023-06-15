package com.android.example

import java.io.IOException
import kotlin.jvm.Throws

class Test {
    // We have a custom lint check bundled with :library
    // that this module depends on. The lint check looks
    // for mentions of "lint", which should trigger an
    // error
//    val s = "lint"
//    fun lint() { }

    @Throws(IOException::class)
    fun throwException() {

    }

    /**
     * 这里应该略过检查
     */
    fun middle() {
        throwException()
    }

    /**
     * 只有这里才需要判断
     */
    fun hello() {
        try {
            middle()
        } catch (_: IOException) {

        }
    }
//
//    /**
//     * 需要提供一个错误
//     */
//    fun test() {
//        middle()
//    }
//
//    fun test(e: Int) {
//        println(e)
//        World().world()
//    }
}
//
//fun from() {
//
//}
//
//class World {
//    fun world() {
//
//    }
//}