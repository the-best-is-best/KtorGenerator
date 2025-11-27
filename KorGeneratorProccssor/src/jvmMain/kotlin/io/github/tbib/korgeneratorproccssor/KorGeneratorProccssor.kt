package io.github.tbib.korgeneratorproccssor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class KorGeneratorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val httpAnnotations = mapOf(
        "io.github.tbib.ktorgenerator.annotations.annotations.GET" to "Get",
        "io.github.tbib.ktorgenerator.annotations.annotations.POST" to "POST",
        "io.github.tbib.ktorgenerator.annotations.annotations.PUT" to "PUT",
        "io.github.tbib.ktorgenerator.annotations.annotations.DELETE" to "Delete",
        "io.github.tbib.ktorgenerator.annotations.annotations.PATCH" to "Patch",
        "io.github.tbib.ktorgenerator.annotations.annotations.OPTIONS" to "Options",
        "io.github.tbib.ktorgenerator.annotations.annotations.HEAD" to "Head"
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val interfaces =
            resolver.getSymbolsWithAnnotation("io.github.tbib.ktorgenerator.annotations.annotations.ApiService")
                .filterIsInstance<KSClassDeclaration>()

        interfaces.forEach { apiInterface ->
            generateImplForInterface(apiInterface)
        }

        return emptyList()
    }

    private fun generateImplForInterface(apiInterface: KSClassDeclaration) {
        val interfaceName = apiInterface.simpleName.asString()
        val pkg = apiInterface.packageName.asString()
        val implName = "${interfaceName}Impl"

        logger.info("Generating API class: $implName")

        val functions = apiInterface.getAllFunctions()
            .filter { func ->
                func.annotations.any { ann ->
                    ann.annotationType.resolve().declaration.qualifiedName?.asString() in httpAnnotations.keys
                }
            }

        functions.forEach { validateFunction(it) }

        val file = codeGenerator.createNewFile(
            Dependencies(false, apiInterface.containingFile!!),
            pkg,
            implName
        )

        file.writer().use { writer ->
            writer.write("package $pkg\n\n")
            writer.write("import io.ktor.client.request.*\n")
            writer.write("import io.github.tbib.ktorgenerator.annotations.engine.KtorGeneratorClient\n")
            writer.write("import io.ktor.http.*\n")
            writer.write("import io.ktor.client.call.body\n")
            writer.write("import io.github.tbib.ktorgenerator.annotations.annotations.*\n")

            writer.write("class $implName : $interfaceName {\n\n")

            for (func in functions) {
                generateFunctionImpl(func, writer)
            }

            writer.write("}\n")
        }
    }

    private fun validateFunction(func: KSFunctionDeclaration) {
        val funcName = func.simpleName.asString()
        val annotation =
            func.annotations.first { it.annotationType.resolve().declaration.qualifiedName!!.asString() in httpAnnotations.keys }
        val path = annotation.arguments.first().value as String
        val httpMethod =
            httpAnnotations[annotation.annotationType.resolve().declaration.qualifiedName!!.asString()]!!

        val pathParams = func.parameters.mapNotNull { p ->
            p.annotations.find { it.shortName.asString() == "Path" }
                ?.let { Triple(p, it, p.name!!.asString()) }
        }

        val urlPlaceholders = Regex("\\{(\\w+)\\}").findAll(path).map { it.groupValues[1] }.toSet()

        pathParams.forEach { (param, pathAnnotation, paramName) ->
            val pathValue = (pathAnnotation.arguments.firstOrNull()?.value as? String
                ?: "").ifEmpty { paramName }
            if (pathValue !in urlPlaceholders) {
                throw RuntimeException("URL placeholder '{$pathValue}' not found in the path for @Path('$pathValue') in function '$funcName'.")
            }
        }

        val pathAnnotationValues = pathParams.map { (_, pathAnnotation, paramName) ->
            (pathAnnotation.arguments.firstOrNull()?.value as? String ?: "").ifEmpty { paramName }
        }.toSet()

        urlPlaceholders.forEach { placeholder ->
            if (placeholder !in pathAnnotationValues) {
                throw RuntimeException("Path parameter '$placeholder' not found in function '$funcName' for URL placeholder '{$placeholder}'.")
            }
        }

        val bodyParams =
            func.parameters.filter { p -> p.annotations.any { it.shortName.asString() == "Body" } }
        if (bodyParams.size > 1) {
            throw RuntimeException("Multiple @Body parameters found in function '$funcName'.")
        }

        if (bodyParams.isNotEmpty() && httpMethod !in listOf("POST", "PUT", "PATCH")) {
            throw RuntimeException("@Body is not supported for $httpMethod requests in function '$funcName'.")
        }
    }

    private fun generateFunctionImpl(func: KSFunctionDeclaration, writer: java.io.Writer) {
        val funcName = func.simpleName.asString()

        val annotation = func.annotations.first {
            it.annotationType.resolve().declaration.qualifiedName!!.asString() in httpAnnotations.keys
        }

        val annotationName =
            annotation.annotationType.resolve().declaration.qualifiedName!!.asString()
        val httpMethod = httpAnnotations[annotationName]!!
        val path = annotation.arguments.first().value as String

        val params = func.parameters.joinToString(", ") { p ->
            val typeName = p.type.resolve().declaration.qualifiedName!!.asString()
            val nullability = if (p.type.resolve().isMarkedNullable) "?" else ""
            p.name!!.asString() + ": " + typeName + nullability
        }

        val returnTypeResolved = func.returnType!!.resolve()
        val returnTypeString = buildString {
            append(returnTypeResolved.declaration.qualifiedName!!.asString())
            if (returnTypeResolved.arguments.isNotEmpty()) {
                append("<")
                append(returnTypeResolved.arguments.joinToString(", ") { arg ->
                    val resolvedType = arg.type?.resolve()
                    val typeName = resolvedType?.declaration?.qualifiedName?.asString() ?: "*"
                    val nullability = if (resolvedType?.isMarkedNullable == true) "?" else ""
                    typeName + nullability
                })
                append(">")
            }
        }

        val returnsUnit = returnTypeString == "kotlin.Unit"

        writer.write("    override suspend fun $funcName($params): $returnTypeString {\n")
        writer.write("        val client = KtorGeneratorClient.ktorClient ?: throw IllegalStateException(\"HttpClient not initialized\")\n")

        var pathTemplate = path
        func.parameters.forEach { param ->
            param.annotations.find { it.shortName.asString() == "Path" }?.let { pathAnnotation ->
                val pathParamName = (pathAnnotation.arguments.firstOrNull()?.value as? String
                    ?: "").ifEmpty { param.name!!.asString() }
                val funcParamName = param.name!!.asString()
                pathTemplate = pathTemplate.replace("{$pathParamName}", "$$funcParamName")
            }
        }
        writer.write("        val urlPath = \"$pathTemplate\"\n")

        val requestBlock = buildString {
            append("client.request(urlPath) {\n")
            append("            method = HttpMethod.$httpMethod\n")

            func.parameters.forEach { param ->
                param.annotations.find { it.shortName.asString() == "Query" }
                    ?.let { queryAnnotation ->
                        val queryParamName =
                            (queryAnnotation.arguments.firstOrNull()?.value as? String
                                ?: "").ifEmpty { param.name!!.asString() }
                        append("            parameter(\"$queryParamName\", ${param.name!!.asString()})\n")
                    }

                param.annotations.find { it.shortName.asString() == "Header" }
                    ?.let { headerAnnotation ->
                        val headerName = headerAnnotation.arguments.first().value as String
                        append("            header(\"$headerName\", ${param.name!!.asString()})\n")
                    }

                if (param.annotations.any { it.shortName.asString() == "Body" }) {
                    append("            contentType(ContentType.Application.Json)\n")
                    append("            setBody(${param.name!!.asString()})\n")
                }
            }
            append("        }")
        }

        if (returnsUnit) {
            writer.write("        $requestBlock\n")
        } else {
            writer.write("        return $requestBlock.body<$returnTypeString>()\n")
        }
        writer.write("    }\n\n")
    }
}