package io.github.tbib.ktor_generator_processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

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

        val file = codeGenerator.createNewFile(
            Dependencies(true, apiInterface.containingFile!!),
            pkg,
            implName
        )

        file.writer().use { writer ->
            writer.write("package $pkg\n\n")
            writer.write("import io.ktor.client.call.*\n")
            writer.write("import io.ktor.client.request.*\n")
            writer.write("import io.ktor.client.request.forms.*\n")
            writer.write("import io.ktor.http.*\n")
            writer.write("import io.github.tbib.ktorgenerator.annotations.engine.KtorGeneratorClient\n")
            writer.write("import io.ktor.http.content.PartData\n")

            val visibility = if (Modifier.INTERNAL in apiInterface.modifiers) "internal" else ""

            writer.write("$visibility fun KtorGeneratorClient.create${interfaceName}(): $interfaceName = $implName()\n\n")
            writer.write("$visibility class $implName : $interfaceName {\n")
            functions.forEach { generateFunctionImpl(it, writer, resolver) }
            writer.write("}\n")
        }
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
        val fieldMapParams =
            func.parameters.filter { it.annotations.any { an -> an.shortName.asString() == "FieldMap" } }
        val isFormUrlEncoded = func.annotations.any { it.shortName.asString() == "FormUrlEncoded" }

        if (bodyParams.isNotEmpty() && (fieldParams.isNotEmpty() || fieldMapParams.isNotEmpty())) {
            throw IllegalStateException("Cannot use @Body with @Field or @FieldMap in the same function: '$funcName'.")
        }

        if (fieldParams.isNotEmpty() && fieldMapParams.isNotEmpty() && !isFormUrlEncoded) {
            throw IllegalStateException("Cannot use @Field and @FieldMap together for a JSON body. Use a single @Body parameter with a custom class or Map. Function: '$funcName'.")
        }

        if (isFormUrlEncoded) {
            if (httpMethod.lowercase() !in listOf("post", "put", "patch")) {
                throw IllegalStateException("@FormUrlEncoded can only be used with POST, PUT, or PATCH methods in function '$funcName'.")
            }
            if (bodyParams.isNotEmpty()) {
                throw IllegalStateException("Cannot use @Body with @FormUrlEncoded in the same function: '$funcName'.")
            }
        } else {
            if ((fieldParams.isNotEmpty() || fieldMapParams.isNotEmpty()) && httpMethod.lowercase() !in listOf(
                    "post",
                    "put",
                    "patch"
                )
            ) {
                throw IllegalStateException("Using @Field or @FieldMap for a JSON body is only supported for POST, PUT, or PATCH methods in function '$funcName'.")
            }
        }

        fieldMapParams.forEach { param ->
            val paramType = param.type.resolve()
            if (paramType.declaration.qualifiedName?.asString() != "kotlin.collections.Map") {
                throw IllegalStateException("@FieldMap parameter must be of type Map in function '$funcName'.")
            }
        }
    }

    private fun generateFunctionImpl(
        func: KSFunctionDeclaration,
        writer: java.io.Writer,
        resolver: Resolver
    ) {
        val httpAnnotation =
            func.annotations.first { it.annotationType.resolve().declaration.qualifiedName?.asString() in httpAnnotations.keys }
        val httpMethod =
            httpAnnotations[httpAnnotation.annotationType.resolve().declaration.qualifiedName!!.asString()]!!
        val path = httpAnnotation.arguments.first().value as String

        writer.write("    override suspend fun ${func.toSignatureString(resolver)} {\n")
        writer.write("        val client = KtorGeneratorClient.ktorClient ?: throw IllegalStateException(\"HttpClient not initialized\")\n")

        var pathTemplate = path
        func.parameters.forEach { param ->
            param.annotations.find { it.shortName.asString() == "Path" }?.let { pathAnnotation ->
                val pathParamName = (pathAnnotation.arguments.firstOrNull()?.value as? String
                    ?: "").ifEmpty { param.name!!.asString() }
                pathTemplate =
                    pathTemplate.replace("{$pathParamName}", "$${param.name!!.asString()}")
            }
        }
        writer.write("        val urlPath = KtorGeneratorClient.baseUrl + \"$pathTemplate\"\n")

        val isFormUrlEncoded = func.annotations.any { it.shortName.asString() == "FormUrlEncoded" }
        val returnsUnit =
            func.returnType!!.resolve().declaration.qualifiedName?.asString() == "kotlin.Unit"

        if (isFormUrlEncoded) {
            writer.write("        val response = client.submitForm(\n")
            writer.write("            url = urlPath,\n")
            writer.write("            formParameters = Parameters.build {\n")
            func.parameters.forEach { param ->
                param.annotations.find { it.shortName.asString() == "Field" }
                    ?.let { fieldAnnotation ->
                        val fieldName = fieldAnnotation.arguments.first().value as String
                        writer.write("                append(\"$fieldName\", ${param.name!!.asString()})\n")
                    }
                param.annotations.find { it.shortName.asString() == "FieldMap" }?.let {
                    writer.write("                ${param.name!!.asString()}.forEach { (key, value) -> append(key, value.toString()) }\n")
                }
            }
            writer.write("            }\n")
            writer.write("        )\n")
            if (!returnsUnit) writer.write("        return response.body()\n")
        } else {
            writer.write("        val response = client.request(urlPath) {\n")
            writer.write("            method = HttpMethod.$httpMethod\n")

            val fieldParams =
                func.parameters.filter { it.annotations.any { an -> an.shortName.asString() == "Field" } }
            val fieldMapParams =
                func.parameters.filter { it.annotations.any { an -> an.shortName.asString() == "FieldMap" } }

            if (fieldParams.isNotEmpty() || fieldMapParams.isNotEmpty()) {
                writer.write("            contentType(ContentType.Application.Json)\n")
                writer.write("            val bodyMap = mutableMapOf<String, Any?>()\n")
                fieldParams.forEach { param ->
                    val fieldName =
                        param.annotations.first { it.shortName.asString() == "Field" }.arguments.first().value as String
                    if (param.type.resolve().isMarkedNullable) {
                        writer.write("            ${param.name!!.asString()}?.let { bodyMap[\"$fieldName\"] = it }\n")
                    } else {
                        writer.write("            bodyMap[\"$fieldName\"] = ${param.name!!.asString()}\n")
                    }
                }
                fieldMapParams.forEach { param ->
                    if (param.type.resolve().isMarkedNullable) {
                        writer.write("           ${param.name!!.asString()}?.let { bodyMap.putAll(it) }\n")
                    } else {
                        writer.write("           bodyMap.putAll(${param.name!!.asString()})\n")
                    }
                }
                writer.write("            setBody(bodyMap)\n")
            } else {
                func.parameters.find { it.annotations.any { an -> an.shortName.asString() == "Body" } }
                    ?.let {
                        writer.write("            contentType(ContentType.Application.Json)\n")
                        writer.write("            setBody(${it.name!!.asString()})\n")
                }
            }

            func.parameters.forEach { param ->
                param.annotations.find { it.shortName.asString() == "Query" }
                    ?.let { queryAnnotation ->
                        val queryParamName =
                            (queryAnnotation.arguments.firstOrNull()?.value as? String
                                ?: "").ifEmpty { param.name!!.asString() }
                        if (param.type.resolve().isMarkedNullable) {
                            writer.write("            ${param.name!!.asString()}?.let { parameter(\"$queryParamName\", it) }\n")
                        } else {
                        writer.write("            parameter(\"$queryParamName\", ${param.name!!.asString()})\n")
                    }
                    }
                param.annotations.find { it.shortName.asString() == "Header" }
                    ?.let { headerAnnotation ->
                        val headerName = headerAnnotation.arguments.first().value as String
                        if (param.type.resolve().isMarkedNullable) {
                            writer.write("            ${param.name!!.asString()}?.let { header(\"$headerName\", it) }\n")
                        } else {
                        writer.write("            header(\"$headerName\", ${param.name!!.asString()})\n")
                    }
                }
            }
            writer.write("        }\n")
            if (!returnsUnit) writer.write("        return response.body()\n")
        }

        writer.write("    }\n")
    }
}

private fun KSFunctionDeclaration.toSignatureString(resolver: Resolver): String {
    val name = simpleName.asString()
    val params = parameters.joinToString(", ") {
        "${it.name!!.asString()}: ${
            it.type.resolve().toTypeNameString(resolver)
        }"
    }
    val returnType = returnType!!.resolve()
    val returnTypeString =
        if (returnType.declaration.qualifiedName?.asString() == "kotlin.Unit") "" else ": ${
            returnType.toTypeNameString(resolver)
        }"
    return "$name($params)$returnTypeString"
}

private fun KSType.toTypeNameString(resolver: Resolver): String {
    val base = declaration.qualifiedName!!.asString()
    val args = if (arguments.isNotEmpty()) "<${
        arguments.joinToString(", ") {
            it.type?.resolve()?.toTypeNameString(resolver) ?: "*"
        }
    }>" else ""
    val nullability = if (isMarkedNullable) "?" else ""
    return "$base$args$nullability"
}

private fun KSClassDeclaration.getAllSuperTypes(): Sequence<KSType> =
    superTypes.map { it.resolve() }
