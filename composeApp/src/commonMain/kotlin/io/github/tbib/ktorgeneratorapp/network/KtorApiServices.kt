package io.github.tbib.ktorgeneratorapp.network

import io.github.tbib.ktorgenerator.annotations.annotations.ApiService
import io.github.tbib.ktorgenerator.annotations.annotations.Field
import io.github.tbib.ktorgenerator.annotations.annotations.FieldMap
import io.github.tbib.ktorgenerator.annotations.annotations.FormUrlEncoded
import io.github.tbib.ktorgenerator.annotations.annotations.GET
import io.github.tbib.ktorgenerator.annotations.annotations.Headers
import io.github.tbib.ktorgenerator.annotations.annotations.Multipart
import io.github.tbib.ktorgenerator.annotations.annotations.POST
import io.github.tbib.ktorgenerator.annotations.annotations.Part
import io.github.tbib.ktorgenerator.annotations.annotations.Path
import io.github.tbib.ktorgenerator.annotations.annotations.TextResponse
import io.github.tbib.ktorgeneratorapp.responses.PostsResponse
import io.ktor.http.content.PartData

@ApiService
internal interface KtorApiServices {

    @GET("/posts")
    suspend fun getPosts(): List<PostsResponse>

    @GET("/posts/{id}")
    suspend fun getPostById(@Path("id") id: Int): PostsResponse

    @Multipart
    @POST("/post")
    suspend fun uploadPhoto(@Part name: String, @Part email: String, @Part file: List<PartData>?)

    //    @FormUrlEncoded
    @POST("your/endpoint")
    @Headers("Authorization: Bearer <token>", "Content-Type: application/json")
    suspend fun sendSomeData(@Field("test") someData: String)

    @GET("endPoint/{test}")
    @TextResponse
    @Headers("Accept: text/html")
    suspend fun getText(@Path("test") test: String): String

    @FormUrlEncoded
    @POST("/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @FieldMap extra: Map<String, Any?>?
    )

}