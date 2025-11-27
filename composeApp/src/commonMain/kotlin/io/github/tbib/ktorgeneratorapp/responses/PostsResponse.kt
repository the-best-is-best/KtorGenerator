package io.github.tbib.ktorgeneratorapp.responses

import io.github.tbib.automapper.automapperannotations.AutoMapper
import io.github.tbib.ktorgeneratorapp.models.PostModel
import kotlinx.datetime.LocalDate

@AutoMapper(PostModel::class)
data class PostsResponse(
    val id: Int,
    val createdAt: LocalDate,
    val content: String
)
