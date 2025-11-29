package io.github.tbib.ktorgenerator.annotations.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@Target(VALUE_PARAMETER)
annotation class Path(val value: String = "")

@Target(VALUE_PARAMETER)
annotation class Query(val value: String = "")

@Target(VALUE_PARAMETER)
annotation class Body

@Target(VALUE_PARAMETER)
annotation class Header(val value: String)


@Target(FUNCTION)
@Retention(RUNTIME)
annotation class Multipart

@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class Part(val value: String = "")

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class Headers(vararg val value: String)

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class FormUrlEncoded

@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class Field(val value: String)

@Target(VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class FieldMap

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class TextResponse