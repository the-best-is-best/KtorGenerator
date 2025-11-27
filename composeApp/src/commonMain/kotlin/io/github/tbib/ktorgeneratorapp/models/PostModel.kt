package io.github.tbib.ktorgeneratorapp.models

import kotlinx.datetime.LocalDate

data class PostModel(
    val id: Int,
    val createdAt: LocalDate,
    val content: String

)
