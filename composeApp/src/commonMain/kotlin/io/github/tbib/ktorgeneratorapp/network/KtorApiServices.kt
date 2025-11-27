package io.github.tbib.ktorgeneratorapp.network

import io.github.tbib.ktorgenerator.annotations.annotations.ApiService
import io.github.tbib.ktorgenerator.annotations.annotations.GET
import io.github.tbib.ktorgenerator.annotations.annotations.Multipart
import io.github.tbib.ktorgenerator.annotations.annotations.POST
import io.github.tbib.ktorgenerator.annotations.annotations.Part
import io.github.tbib.ktorgenerator.annotations.annotations.Path
import io.github.tbib.ktorgeneratorapp.responses.PostsResponse
import io.ktor.http.content.PartData

@ApiService
interface KtorApiServices {

    @GET("/posts")
    suspend fun getPosts(): List<PostsResponse>

    @GET("/posts/{id}")
    suspend fun getPostById(@Path("id") id: Int): PostsResponse

    @Multipart
    @POST("/post")
    suspend fun uploadPhoto(@Part name: String?, @Part email: String?, @Part file: List<PartData>)

}
