package io.github.tbib.ktorgeneratorapp.network

import io.github.tbib.ktorgenerator.annotations.annotations.ApiService
import io.github.tbib.ktorgenerator.annotations.annotations.GET
import io.github.tbib.ktorgenerator.annotations.annotations.Path
import io.github.tbib.ktorgeneratorapp.models.PostModel

@ApiService
interface KtorApiServices {

    @GET("/posts")
    suspend fun getPosts(): List<PostModel>

    @GET("/posts/{id}")
    suspend fun getPostById(@Path("id") id: Int): PostModel
}
