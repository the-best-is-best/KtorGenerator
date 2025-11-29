package io.github.tbib.ktor_generator_processor

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
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

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val interfaces = resolver
            .getSymbolsWithAnnotation("io.github.tbib.ktorgenerator.annotations.annotations.ApiService")
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        if (interfaces.isEmpty()) return emptyList()

        val deps = safeDependencies(
            symbols = interfaces.asSequence()
        )

        interfaces.forEach { api ->
            generateImplForInterface(api, resolver, deps)
        }

        return emptyList()
    }

    private fun generateImplForInterface(
        apiInterface: KSClassDeclaration,
        resolver: Resolver,
        deps: Dependencies
    ) {
        val pkg = apiInterface.packageName.asString()
        val name = apiInterface.simpleName.asString()
        val impl = "${name}Impl"

        // هل الواجهة internal ؟
        val isInternal = Modifier.INTERNAL in apiInterface.modifiers

        val functions = apiInterface.getAllFunctions().filter { f ->
            f.annotations.any { ann ->
                safeAnnotationQualifiedName(ann) in httpAnnotations.keys
            }
        }.toList()

        functions.forEach { validateFunction(it) }

        val fileBuilder = FileSpec.builder(pkg, impl)
            .addImport("io.ktor.client.request", "request")
            .addImport("io.ktor.client.request", "parameter")
            .addImport("io.ktor.client.request.forms", "submitFormWithBinaryData")
            .addImport("io.ktor.client.request", "header")
            .addImport("io.ktor.http", "HttpMethod")
            .addImport("io.ktor.http", "Headers")
            .addImport("io.ktor.http", "ContentType")
            .addImport("io.ktor.client.call", "body")
            .addImport("io.ktor.client.statement", "bodyAsText")
            .addImport("io.ktor.client.request.forms", "formData")

        val classBuilder = TypeSpec.classBuilder(impl)
            // حدد visibility بناء على واجهة الـ API
            .addModifiers(if (isInternal) KModifier.INTERNAL else KModifier.PUBLIC)
            .addSuperinterface(apiInterface.asType(emptyList()).toTypeName())

        functions.forEach { fn ->
            classBuilder.addFunction(generateFunctionImpl(fn, resolver))
        }

        fileBuilder.addType(classBuilder.build())

        // دائمًا public fun createXXX()
        val clientCls = ClassName(
            "io.github.tbib.ktorgenerator.annotations.engine",
            "KtorGeneratorClient"
        )

        val createFn = FunSpec.builder("create${name}")
            .receiver(clientCls)
            .addModifiers(if (isInternal) KModifier.INTERNAL else KModifier.PUBLIC)
            .returns(apiInterface.asType(emptyList()).toTypeName())
            .addStatement("return %L()", impl)
            .build()

        fileBuilder.addFunction(createFn)

        fileBuilder.build().writeTo(codeGenerator, deps)

    }


    private fun generateFunctionImpl(
        func: KSFunctionDeclaration,
        resolver: Resolver
    ): FunSpec {
        val httpAnn = func.annotations.first {
            safeAnnotationQualifiedName(it) in httpAnnotations
        }
        val httpMethod = httpAnnotations[safeAnnotationQualifiedName(httpAnn)]!!
        val path = httpAnn.arguments.first().value as String

        val builder = FunSpec.builder(func.simpleName.asString())
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)

        func.parameters.forEach { p ->
            builder.addParameter(p.name!!.asString(), p.type.toTypeName())
        }

        func.returnType?.let { builder.returns(it.toTypeName()) }

        builder.addStatement(
            "val client = KtorGeneratorClient.ktorClient " +
                    "?: throw IllegalStateException(%S)",
            "HttpClient not initialized"
        )

        builder.addCode(createFunctionBody(func, httpMethod, path))

        return builder.build()
    }

    private fun createFunctionBody(
        func: KSFunctionDeclaration,
        httpMethod: String,
        path: String
    ): CodeBlock {
        val cb = CodeBlock.builder()

        cb.addStatement("var urlPath = %S", path)

        func.parameters.forEach { p ->
            p.annotations.find { it.shortName.asString() == "Path" }?.let { ann ->
                val name = p.name!!.asString()
                val key = ann.arguments.first().value as? String ?: name
                cb.addStatement("urlPath = urlPath.replace(%S, %L.toString())", "{$key}", name)
            }
        }

        cb.addStatement(
            "urlPath = KtorGeneratorClient.baseUrl + urlPath"
        )

        val isMultipart = func.parameters.any { p ->
            val qn = p.type.resolve().declaration.qualifiedName?.asString()
            qn == "PartData" ||
                    (qn == "kotlin.collections.List" &&
                            p.type.resolve().arguments.first().type?.resolve()
                                ?.declaration?.qualifiedName?.asString() == "PartData")
        }

        if (!isMultipart) {
            cb.beginControlFlow("val response = client.request(urlPath) {")
            cb.addStatement("method = HttpMethod.%L", httpMethod)
        } else {
            cb.addStatement("val response = client.submitFormWithBinaryData(")
            cb.addStatement("    url = urlPath,")
            cb.addStatement("    formData = formData {")
        }

        // Query
        func.parameters.forEach { p ->
            p.annotations.find { it.shortName.asString() == "Query" }?.let { ann ->
                val name = p.name!!.asString()
                val q = ann.arguments.first().value as? String ?: name
                cb.addStatement("parameter(%S, %L)", q, name)
            }
        }

        // Headers
        func.annotations.find { it.shortName.asString() == "Headers" }
            ?.arguments?.first()?.value.let { headers ->
                if (headers is List<*>) {
                    headers.forEach { h ->
                        val v = h as String
                        val parts = v.split(":", limit = 2)
                        cb.addStatement("header(%S, %S)", parts[0].trim(), parts[1].trim())
                    }
                }
            }

        // Multipart
        if (isMultipart) {
            func.parameters.forEach { p ->
                val code = buildMultipart(p.name!!.asString(), p)
                cb.add(code)
            }

            cb.addStatement("    }")
            cb.addStatement(")")
        } else {
            cb.endControlFlow()
        }

        val isUnit =
            func.returnType?.resolve()?.declaration?.qualifiedName?.asString() == "kotlin.Unit"
        if (!isUnit) cb.addStatement("return response.body()")

        return cb.build()
    }

    private fun validateFunction(func: KSFunctionDeclaration) {
        val httpAnn = func.annotations.firstOrNull {
            safeAnnotationQualifiedName(it) in httpAnnotations
        } ?: error("Missing HTTP annotation on ${func.simpleName.asString()}")

        val method = httpAnnotations[safeAnnotationQualifiedName(httpAnn)]!!
        val hasBody = func.parameters.any { p ->
            p.annotations.any { it.shortName.asString() == "Body" || it.shortName.asString() == "Field" }
        }

        if (method == "Get" && hasBody) {
            error("GET cannot contain a body (${func.simpleName.asString()})")
        }
    }
}

// ----------------------------- Helpers ------------------------------------

private fun safeAnnotationQualifiedName(ann: KSAnnotation): String? =
    try {
        ann.annotationType.resolve().declaration.qualifiedName?.asString()
    } catch (_: Throwable) {
        null
    }

fun buildMultipart(name: String, variable: KSValueParameter): String {
    val type = variable.type.resolve()
    val qn = type.declaration.qualifiedName?.asString()

    return when {
        // Single file PartData
        qn == "PartData" -> """
            append($name)
        """.trimIndent()

        // List<PartData>
        qn == "kotlin.collections.List" &&
                type.arguments.first().type?.resolve()?.declaration?.qualifiedName?.asString() ==
                "PartData" -> """
            $name?.forEach { append(it) }
        """.trimIndent()

        // String → FormItem
        qn == "kotlin.String" -> """
            if ($name != null)
                append(
                    FormItem(
                        value = $name,
                        key = "$name"
                    )
                )
        """.trimIndent()

        else -> error("Unsupported Multipart Parameter: $name (type=$qn)")
    }
}

fun safeDependencies(
    symbols: Sequence<KSAnnotated>
): Dependencies {
    val files = symbols.mapNotNull { it.containingFile }.toList()
    return if (files.isNotEmpty()) {
        Dependencies(true, *files.toTypedArray())
    } else {
        Dependencies(false)
    }
}
