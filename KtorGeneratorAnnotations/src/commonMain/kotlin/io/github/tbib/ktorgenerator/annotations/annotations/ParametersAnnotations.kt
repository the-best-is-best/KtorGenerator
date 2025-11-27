package io.github.tbib.ktorgenerator.annotations.annotations

import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@Target(VALUE_PARAMETER)
annotation class Path(val value: String = "")

@Target(VALUE_PARAMETER)
annotation class Query(val value: String = "")

@Target(VALUE_PARAMETER)
annotation class Body

@Target(VALUE_PARAMETER)
annotation class Header(val value: String)