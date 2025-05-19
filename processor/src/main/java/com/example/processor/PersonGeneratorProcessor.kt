package com.example.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo

class PersonGeneratorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private var invoked = false // processが複数回呼ばれることがあるため、一度だけ実行するように制御

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) {
            return emptyList()
        }

        val personClassName = options["personClassName"]
        val personPackageName = options["personPackageName"]

        if (personClassName.isNullOrBlank()) {
            logger.error("KSP option 'personClassName' is missing or empty.")
            return emptyList()
        }
        if (personPackageName.isNullOrBlank()) {
            logger.error("KSP option 'personPackageName' is missing or empty.")
            return emptyList()
        }

        logger.info("Generating class: $personPackageName.$personClassName")

        val personClassSpec = TypeSpec.classBuilder(personClassName)
            .addModifiers(KModifier.DATA) // data class にする
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("name", String::class)
                    .addParameter("age", Int::class)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("name", String::class)
                    .initializer("name")
                    .build()
            )
            .addProperty(
                PropertySpec.builder("age", Int::class)
                    .initializer("age")
                    .build()
            )
            .addFunction(
                FunSpec.builder("greet")
                    .returns(String::class)
                    .addStatement("return \"Hello, my name is \$name and I am \$age years old.\"")
                    .build()
            )
            .build()

        val fileSpec = FileSpec.builder(personPackageName, "${personClassName}Generated")
            .addType(personClassSpec)
            .build()

        try {
            fileSpec.writeTo(codeGenerator, Dependencies(true)) // true でインクリメンタルビルドに対応
            logger.info("Successfully generated $personPackageName.$personClassName")
        } catch (e: Exception) {
            logger.error("Error generating class: ${e.message}", null)
        }

        invoked = true
        return emptyList() // 何もリターンしない (処理対象のシンボルがないため)
    }
}