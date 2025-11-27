package io.github.tbib.ktorgenerator.annotations.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class GET(val path: String)

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class POST(val path: String)

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class PUT(val path: String)

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class DELETE(val path: String)

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class OPTIONS(val path: String)

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class HEAD(val path: String)

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class PATCH(val path: String)

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class Multipart

@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class Part(val value: String = "")