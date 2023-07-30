package com.storyteller_f.yong.definition

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Throws(@Suppress("unused") vararg val exceptionClasses: KClass<out Throwable>)