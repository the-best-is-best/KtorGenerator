<div align="center">
 <h1> Ktor Generator 🚀<h1>
</div>
<div align="center">
<a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
<img src="https://img.shields.io/badge/Platform-Kotlin%20Multiplatform-blueviolet.svg" />
<img src="https://img.shields.io/badge/KSP-Supported-brightgreen.svg" />
<a href="https://github.com/tbib"><img alt="Profile" src="https://img.shields.io/badge/github-%23181717.svg?&style=for-the-badge&logo=github&logoColor=white" height="20"/></a>
</div>

---

**Ktor Generator** is a powerful KSP (Kotlin Symbol Processing) library that automates the creation
of Ktor HTTP client implementations for your Kotlin Multiplatform projects. By simply annotating an
interface, you can eliminate the boilerplate code required for API service definitions.

It includes:

- Automatic generation of Ktor client implementations from a simple `@ApiService` annotation.
- Support for all standard HTTP methods: `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`,
  `@OPTIONS`.
- A comprehensive set of parameter annotations, including `@Path`, `@Query`, `Body`, `@Header`,
  `@Part`, `@Field`, and `@FieldMap`.
- Support for various request body types like `@FormUrlEncoded` and `@Multipart`.
- Generation of a factory extension function to easily create your API service instance.

---

# Versions

<p>
  <!-- <a href="https://search.maven.org/artifact/io.github.tbib/ktorgenerator-annotations">
    <img src="https://img.shields.io/maven-central/v/io.github.tbib/ktorgenerator-annotations" />
  </a> -->
    <a href="https://search.maven.org/artifact/io.github.the-best-is-best/ktor-generator-processor">
      <img src="https://img.shields.io/maven-central/v/io.github.the-best-is-best/ktor-generator-processor" />
    </a>

</p>

# 📦 Installation

**_Note: Please replace `x.y.z` with the latest version._**

## 1. Add the KSP plugin and dependencies

In your module-level `build.gradle.kts` file (e.g., `shared/build.gradle.kts`):

```kotlin
plugins {
    // Make sure you have the KSP plugin applied
    id("com.google.devtools.ksp")
}

// ...

dependencies {
    // Add the annotations library
    implementation("io.github.tbib:ktorgenerator-annotations:x.y.z")

    // Add the KSP processor
    ksp("io.github.tbib:ktor-generator-processor:x.y.z")
}
```

## 2. Include Generated Code

Ensure the generated code is included in your source sets. This allows your project to see the code
created by the KSP processor.

```kotlin
kotlin {
    sourceSets.all {
        kotlin.srcDir("build/generated/ksp/$name/kotlin")
    }
}
```

*Note: The path might need to be adjusted based on your project structure, but
`build/generated/ksp/$name/kotlin` covers most standard KMP setups._

---

# 🧩 Available Annotations

## Core

- `@ApiService`: Marks an `interface` to be processed. Ktor Generator will create an implementation
  for it.

## HTTP Methods

- `@GET(path: String)`
- `@POST(path: String)`
- `@PUT(path: String)`
- `@DELETE(path: String)`
- `@PATCH(path: String)`
- `@HEAD(path: String)`
- `@OPTIONS(path: String)`

## Function-level Annotations

- `@Headers(vararg val value: String)`: Add multiple static headers to a request.
- `@Multipart`: Denotes a multipart request.
- `@FormUrlEncoded`: Denotes a URL-encoded form request.
- `@TextResponse`: Makes the function return the raw response body as a `String`.

## Parameter Annotations

- `@Path(value: String)`: Replace a placeholder in the request URL path (e.g., `users/{id}`).
- `@Query(value: String)`: Add a query parameter to the URL.
- `@Body`: Use the annotated parameter as the request body.
- `@Header(value: String)`: Add a request header.
- `@Field(value: String)`: A single field in a URL-encoded form.
- `@FieldMap`: A `Map` of key-value pairs for a URL-encoded form.
- `@Part(value: String)`: A single part in a multipart request.

---

# 🧪 Example Usage

### 1. Define Your API Interface

Create an interface and annotate it with `@ApiService`. Define your API endpoints as functions with
HTTP method and parameter annotations.

```kotlin
// commonMain/kotlin/com/example/api/MyApiService.kt
package com.example.api

import io.github.tbib.ktorgenerator.annotations.annotations.*

// Assume User and Post data classes exist
// data class User(...)
// data class Post(...)

@ApiService
interface MyApiService {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") userId: String): User

    @POST("users")
    suspend fun createUser(@Body user: User)

    @GET("posts")
    suspend fun getPostsByAuthor(@Query("author") authorId: String): List<Post>
}
```

### 2. Configure the KtorGeneratorClient

In your application's setup code (e.g., in your DI setup or application entry point), configure the
`KtorGeneratorClient` with your base URL and a Ktor `HttpClient` instance.

```kotlin
// commonMain/kotlin/com/example/di/NetworkModule.kt
package com.example.di

import io.github.tbib.ktorgenerator.annotations.engine.KtorGeneratorClient
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun initializeNetwork() {
    // Configure the Ktor client
    val ktorClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        // Add other plugins like Logging, Auth, etc.
    }

    // Assign the base URL and the client to the generator
    KtorGeneratorClient.baseUrl = "https://api.example.com/v1/"
    KtorGeneratorClient.ktorClient = ktorClient
}
```

### 3. Create and Use Your Service

After building the project, KSP will generate the implementation and a `create<YourInterfaceName>`
extension function. You can then get your API service instance directly from `KtorGeneratorClient`.

```kotlin
// After initialization in your ViewModel or Presenter
val myApi: MyApiService = KtorGeneratorClient.createMyApiService()

// Now you can make API calls
suspend fun fetchSomeUser() {
    val user = myApi.getUser("123")
    println("Fetched user: $user")
}
```
