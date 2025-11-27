package io.github.tbib.ktor_generator_processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType

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

            writer.write("fun KtorGeneratorClient.create${interfaceName}(): $interfaceName = $implName()\n\n")
            writer.write("internal class $implName : $interfaceName {\n")
            functions.forEach { generateFunctionImpl(it, writer, resolver) }
            writer.write("}\n")
        }
    }

    private fun validateFunction(func: KSFunctionDeclaration) {
        val funcName = func.simpleName.asString()
        val isMultipart = func.annotations.any { it.shortName.asString() == "Multipart" }
        if (!isMultipart) return

        val partParams =
            func.parameters.filter { it.annotations.any { an -> an.shortName.asString() == "Part" } }
        val bodyParams =
            func.parameters.filter { it.annotations.any { an -> an.shortName.asString() == "Body" } }

        if (partParams.isNotEmpty() && bodyParams.isNotEmpty()) {
            throw IllegalStateException("Cannot use @Part and @Body in the same multipart function: '$funcName'.")
        }
        if (partParams.isEmpty() && bodyParams.isEmpty()) {
            throw IllegalStateException("A @Multipart function must have at least one @Part parameter or a @Body parameter of type MultiPartFormDataContent: '$funcName'.")
        }
        bodyParams.firstOrNull()?.let {
            if (it.type.resolve().declaration.qualifiedName?.asString() != "io.ktor.client.request.forms.MultiPartFormDataContent") {
                throw IllegalStateException("In a @Multipart function, a @Body parameter must be of type MultiPartFormDataContent: '$funcName'.")
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

        val isMultipart = func.annotations.any { it.shortName.asString() == "Multipart" }
        val returnsUnit =
            func.returnType!!.resolve().declaration.qualifiedName?.asString() == "kotlin.Unit"

        if (isMultipart) {
            val bodyParam =
                func.parameters.firstOrNull { it.annotations.any { an -> an.shortName.asString() == "Body" } }
            if (bodyParam != null) {
                writer.write("        val response = client.request(urlPath) { method = HttpMethod.$httpMethod; setBody(${bodyParam.name!!.asString()}) }\n")
            } else {
                writer.write("        val multipartData = mutableListOf<PartData>()\n")
                func.parameters.forEach { param ->
                    param.annotations.find { it.shortName.asString() == "Part" }
                        ?.let { partAnnotation ->
                            val partName = (partAnnotation.arguments.firstOrNull()?.value as? String
                                ?: "").ifEmpty { param.name!!.asString() }
                            val paramType = param.type.resolve()
                            val isList =
                                paramType.declaration.qualifiedName?.asString() == "kotlin.collections.List"
                            val isPartData =
                                (paramType.declaration as? KSClassDeclaration)?.getAllSuperTypes()
                                    ?.any { it.declaration.qualifiedName?.asString() == "io.ktor.http.content.PartData" } == true

                            if (isList) {
                                writer.write("        multipartData.addAll(${param.name!!.asString()})\n")
                            } else if (isPartData) {
                                writer.write("        multipartData.add(${param.name!!.asString()})\n")
                            } else {
                                writer.write("        multipartData.add(PartData.FormItem(${param.name!!.asString()}.toString(), { }, Headers.build { append(HttpHeaders.ContentDisposition, \"form-data; name=$partName\") }))\n")
                            }
                        }
                }
                writer.write("        val response = client.submitFormWithBinaryData(url = urlPath, formData = multipartData)\n")
            }
            if (!returnsUnit) writer.write("        return response.body()\n")
        } else {
            writer.write("        val response = client.request(urlPath) {\n")
            writer.write("            method = HttpMethod.$httpMethod\n")
            func.parameters.forEach { param ->
                param.annotations.find { it.shortName.asString() == "Query" }
                    ?.let { queryAnnotation ->
                        val queryParamName =
                            (queryAnnotation.arguments.firstOrNull()?.value as? String
                                ?: "").ifEmpty { param.name!!.asString() }
                        writer.write("            parameter(\"$queryParamName\", ${param.name!!.asString()})\n")
                    }
                param.annotations.find { it.shortName.asString() == "Header" }
                    ?.let { headerAnnotation ->
                        val headerName = headerAnnotation.arguments.first().value as String
                        writer.write("            header(\"$headerName\", ${param.name!!.asString()})\n")
                    }
                param.annotations.find { it.shortName.asString() == "Body" }?.let {
                    writer.write("            contentType(ContentType.Application.Json)\n")
                    writer.write("            setBody(${param.name!!.asString()})\n")
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
    val returnType = returnType!!.resolve().toTypeNameString(resolver)
    return if (returnType == "kotlin.Unit") "$name($params)" else "$name($params): $returnType"
}

private fun KSType.toTypeNameString(resolver: Resolver): String {
    val base = declaration.qualifiedName!!.asString()
    return if (arguments.isEmpty()) base else "$base<${
        arguments.joinToString(", ") {
            it.type!!.resolve().toTypeNameString(resolver)
        }
    }>"
}

private fun KSClassDeclaration.getAllSuperTypes(): Sequence<KSType> =
    superTypes.map { it.resolve() }
