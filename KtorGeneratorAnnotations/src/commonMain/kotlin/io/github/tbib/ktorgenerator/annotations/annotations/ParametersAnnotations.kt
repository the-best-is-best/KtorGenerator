package io.github.tbib.ktorgenerator.annotations.annotations

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Path(val value: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Query(val value: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Body

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Header(val value: String)

