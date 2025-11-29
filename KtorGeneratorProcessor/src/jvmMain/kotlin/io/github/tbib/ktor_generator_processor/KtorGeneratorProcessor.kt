package io.github.tbib.ktor_generator_processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class KtorGeneratorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val httpAnnotations = mapOf(
        "io.github.tbib.ktorgenerator.annotations.annotations.GET" to "Get",
        "io.github.tbib.ktorgenerator.annotations.annotations.POST" to "Post",
        "io.github.tbib.ktorgenerator.annotations.annotations.PUT" to "Put",
        "io.github.tbib.ktorgenerator.annotations.annotations.DELETE" to "Delete",
        "io.github.tbib.ktorgenerator.annotations.annotations.PATCH" to "Patch",
        "io.github.tbib.ktorgenerator.annotations.annotations.OPTIONS" to "Options",
        "io.github.tbib.ktorgenerator.annotations.annotations.HEAD" to "Head"
    )

    override fun process(resolver: Resolver): List<KSClassDeclaration> {
        val interfaces =
            resolver.getSymbolsWithAnnotation("io.github.tbib.ktorgenerator.annotations.annotations.ApiService")
            .filterIsInstance<KSClassDeclaration>()

        interfaces.forEach { apiInterface ->
            generateImplForInterface(apiInterface, resolver)
        }

        return emptyList()
    }

    private fun generateImplForInterface(apiInterface: KSClassDeclaration, resolver: Resolver) {
        val interfaceName = apiInterface.simpleName.asString()
        val pkg = apiInterface.packageName.asString()
        val implName = "${interfaceName}Impl"

        val functions = apiInterface.getAllFunctions().filter { func ->
            func.annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() in httpAnnotations.keys }
        }

        functions.forEach { validateFunction(it) }

        val internalApiAnnotation =
            AnnotationSpec.builder(com.squareup.kotlinpoet.ClassName("kotlin", "OptIn"))
                .addMember("InternalAPI::class")
                .build()

        val fileSpec = FileSpec.builder(pkg, implName)
            .addAnnotation(internalApiAnnotation)
            .addImport("io.ktor.utils.io", "InternalAPI")
            .addImport("io.ktor.client.request", "request")
            .addImport("io.ktor.http", "HttpMethod")
            .addImport("io.ktor.client.call", "body")
            .addImport("io.ktor.client.request", "header")
            .addImport("io.ktor.http", "contentType")
            .addImport("io.ktor.http", "ContentType")
            .addImport("io.ktor.client.request", "setBody")
            .addImport("io.ktor.client.statement", "bodyAsText")
            .addImport("io.ktor.client.request.forms", "submitFormWithBinaryData")
            .addImport("io.ktor.client.request.forms", "formData")
            .addImport("io.ktor.client.request.forms", "submitForm")
            .addImport("io.ktor.http", "Parameters")
            .addImport("io.ktor.client.request", "parameter")
            .addType(
                TypeSpec.classBuilder(implName)
                .addSuperinterface(apiInterface.asType(emptyList()).toTypeName())
                .apply {
                    if (Modifier.INTERNAL in apiInterface.modifiers) {
                        addModifiers(KModifier.INTERNAL)
                    }
                    functions.forEach { addFunction(generateFunctionImpl(it, resolver)) }
                }
                .build())
            .addFunction(
                FunSpec.builder("create${interfaceName}")
                    .receiver(
                        com.squareup.kotlinpoet.ClassName(
                            "io.github.tbib.ktorgenerator.annotations.engine",
                            "KtorGeneratorClient"
                        )
                    )
                .returns(apiInterface.asType(emptyList()).toTypeName())
                .addStatement("return $implName()")
                .apply {
                    if (Modifier.INTERNAL in apiInterface.modifiers) {
                        addModifiers(KModifier.INTERNAL)
                    }
                }
                .build())
            .build()

        fileSpec.writeTo(codeGenerator, Dependencies(true, apiInterface.containingFile!!))
    }

    private fun validateFunction(func: KSFunctionDeclaration) {
        val funcName = func.simpleName.asString()
        val httpAnnotation =
            func.annotations.first { it.annotationType.resolve().declaration.qualifiedName?.asString() in httpAnnotations.keys }
        val httpMethod =
            httpAnnotations[httpAnnotation.annotationType.resolve().declaration.qualifiedName!!.asString()]!!
        val bodyParams =
            func.parameters.filter { it.annotations.any { an -> an.shortName.asString() == "Body" } }
        val fieldParams =
            func.parameters.filter { it.annotations.any { an -> an.shortName.asString() == "Field" } }
        val hasBody = bodyParams.isNotEmpty() || fieldParams.isNotEmpty()

        if (httpMethod.lowercase() == "get" && hasBody) {
            throw IllegalStateException("""@Body and @Field are not allowed on GET requests. Use @Query for GET request parameters. In function '$funcName'.""")
        }
    }

    private fun generateFunctionImpl(func: KSFunctionDeclaration, resolver: Resolver): FunSpec {
        val httpAnnotation =
            func.annotations.first { it.annotationType.resolve().declaration.qualifiedName?.asString() in httpAnnotations.keys }
        val httpMethod =
            httpAnnotations[httpAnnotation.annotationType.resolve().declaration.qualifiedName!!.asString()]!!
        val path = httpAnnotation.arguments.first().value as String

        return FunSpec.builder(func.simpleName.asString())
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .returns(func.returnType!!.toTypeName())
            .apply {
                func.parameters.forEach {
                    addParameter(
                        ParameterSpec.builder(
                            it.name!!.asString(),
                            it.type.toTypeName()
                        ).build()
                    )
                }
            }
            .addStatement("val client = KtorGeneratorClient.ktorClient ?: throw IllegalStateException(\"\"\"HttpClient not initialized\"\"\")")
            .addCode(createFunctionBody(func, httpMethod, path, resolver))
            .build()
    }

    private fun createFunctionBody(
        func: KSFunctionDeclaration,
        httpMethod: String,
        path: String,
        resolver: Resolver
    ): CodeBlock {
        val builder = CodeBlock.builder()
        builder.addStatement("var urlPath = %S", path)
        func.parameters.forEach { param ->
            param.annotations.find { it.shortName.asString() == "Path" }?.let {
                val paramName = param.name!!.asString()
                val pathParamName =
                    (it.arguments.firstOrNull()?.value as? String ?: "").ifEmpty { paramName }
                builder.addStatement(
                    "urlPath = urlPath.replace(%S, %L.toString())",
                    "{$pathParamName}",
                    paramName
                )
            }
        }
        builder.addStatement("urlPath = KtorGeneratorClient.baseUrl + urlPath")

        val returnsUnit =
            func.returnType!!.resolve().declaration.qualifiedName?.asString() == "kotlin.Unit"
        val isTextResponse = func.annotations.any { it.shortName.asString() == "TextResponse" }
        val isMultipart = func.annotations.any { it.shortName.asString() == "Multipart" }

        val returnStatement = when {
            isTextResponse -> "return response.bodyAsText()"
            !returnsUnit -> "return response.body()"
            else -> ""
        }

        if (isMultipart) {
            // هل هناك براميتر MultiPartFormDataContent مباشر؟
            val multiPartParam = func.parameters.find {
                it.type.resolve().declaration.qualifiedName?.asString() == "io.ktor.client.request.forms.MultiPartFormDataContent"
            }

            if (multiPartParam != null) {
                val paramName = multiPartParam.name!!.asString()
                // استدعاء client.request مع setBody (لأن param هو MultiPartFormDataContent)
                builder.addStatement(
                    """
                    val response = client.request(urlPath) {
                        method = HttpMethod.Post
                        setBody(%L)
                    }
                """.trimIndent(), paramName
                )
            } else {
                // بناء formData التقليدي من List<PartData> أو غيره
                builder.add("val multipartData = formData {\n")

                func.parameters.forEach { param ->
                    val paramName = param.name!!.asString()
                    val paramType = param.type.resolve()
                    val isList =
                        paramType.declaration.qualifiedName?.asString() == "kotlin.collections.List"

                    val listGeneric = paramType.arguments.firstOrNull()?.type?.resolve()
                    val partDataType =
                        resolver.getClassDeclarationByName("io.ktor.http.content.PartData")!!
                            .asStarProjectedType()

                    val isListOfPartData =
                        isList && listGeneric != null && listGeneric.isAssignableFrom(partDataType)
                    val isSinglePartData = paramType.isAssignableFrom(partDataType)

                    when {
                        isListOfPartData -> {
                            builder.addStatement(
                                "%L?.forEach { append(%S, it) }",
                                paramName,
                                paramName
                            )
                        }

                        isSinglePartData -> {
                            builder.addStatement("%L?.let { append(%S, it) }", paramName, paramName)
                        }

                        else -> {
                            builder.addStatement(
                                "%L?.let { append(%S, it.toString()) }",
                                paramName,
                                paramName
                            )
                        }
                    }
                }
                builder.add("}\n")
                builder.addStatement("val response = client.submitFormWithBinaryData(url = urlPath, formData = multipartData)")
            }
        } else {
            // معالجة الطلبات الأخرى (GET، POST json ...) كما هي
            builder.beginControlFlow("val response = client.request(urlPath)")
            builder.addStatement("method = HttpMethod.%L", httpMethod)

            func.annotations.find { it.shortName.asString() == "Headers" }?.let { headersAnn ->
                (headersAnn.arguments.firstOrNull()?.value as? List<*>)?.forEach { headerValue ->
                    if (headerValue is String && ":" in headerValue) {
                        val (key, value) = headerValue.split(":", limit = 2).map { it.trim() }
                        builder.addStatement("header(%S, %S)", key, value)
                    }
                }
            }

            func.parameters.forEach { param ->
                val paramName = param.name!!.asString()
                param.annotations.find { it.shortName.asString() == "Query" }?.let {
                    val queryParamName =
                        (it.arguments.firstOrNull()?.value as? String ?: "").ifEmpty { paramName }
                    if (param.type.resolve().isMarkedNullable) {
                        builder.addStatement(
                            "%L?.let { parameter(%S, it) }",
                            paramName,
                            queryParamName
                        )
                    } else {
                        builder.addStatement("parameter(%S, %L)", queryParamName, paramName)
                    }
                }
                param.annotations.find { it.shortName.asString() == "Header" }?.let {
                    val headerName = it.arguments.firstOrNull()?.value as String
                    if (param.type.resolve().isMarkedNullable) {
                        builder.addStatement("%L?.let { header(%S, it) }", paramName, headerName)
                    } else {
                        builder.addStatement("header(%S, %L)", headerName, paramName)
                    }
                }
            }

            val fieldParams =
                func.parameters.filter { it.annotations.any { an -> an.shortName.asString() == "Field" } }
            if (fieldParams.isNotEmpty()) {
                builder.addStatement("contentType(ContentType.Application.Json)")
                builder.addStatement("val bodyMap = mutableMapOf<String, String>()")
                fieldParams.forEach { param ->
                    val fieldName =
                        param.annotations.first { it.shortName.asString() == "Field" }.arguments.first().value as String
                    if (param.type.resolve().isMarkedNullable) {
                        builder.addStatement(
                            "%L?.let { bodyMap[%S] = it.toString() }",
                            param.name!!.asString(),
                            fieldName
                        )
                    } else {
                        builder.addStatement(
                            "bodyMap[%S] = %L.toString()",
                            fieldName,
                            param.name!!.asString()
                        )
                    }
                }
                builder.addStatement("setBody(bodyMap)")
            } else {
                func.parameters.find { it.annotations.any { an -> an.shortName.asString() == "Body" } }
                    ?.let {
                    builder.addStatement("contentType(ContentType.Application.Json)")
                    builder.addStatement("setBody(${it.name!!.asString()})")
                }
            }
            builder.endControlFlow()
        }

        builder.addStatement(returnStatement)
        return builder.build()
    }

}

