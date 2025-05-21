package com.example.processor

import com.example.annotations.LogInfo
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class LogInfoProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger, // loggerをクラスのプロパティとして保持
    private val options: Map<String, String>
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(LogInfo::class.qualifiedName!!)
        val unableToProcess = symbols.filterNot { it.validate() }.toMutableList()

        val functionsByInterface = symbols
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.validate() } // 有効な関数のみ
            .filter {
                // 親がインターフェースであるか確認
                val parent = it.parentDeclaration
                parent is KSClassDeclaration && parent.classKind == ClassKind.INTERFACE
            }
            .groupBy { it.parentDeclaration as KSClassDeclaration } // インターフェースでグルーピング

        functionsByInterface.forEach { (interfaceDeclaration, functionsInInterface) ->
            val interfaceName = interfaceDeclaration.simpleName.asString()
            val packageName = interfaceDeclaration.packageName.asString()
            val generatedClassName = "${interfaceName}Logger"

            // インターフェースごとにクラスビルダーを作成
            val classBuilder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(KModifier.PUBLIC)

            var primaryOriginatingFile: KSFile? = null

            functionsInInterface.forEach { function ->
                // アノテーションの引数を取得
                val logInfoAnnotation = function.annotations.find {
                    it.shortName.asString() == LogInfo::class.simpleName
                }
                val infoValue = logInfoAnnotation?.arguments?.find {
                    it.name?.asString() == "info"
                }?.value as? String

                if (infoValue != null) {
                    val funSpec = createLoggerMethodSpec(interfaceDeclaration, function, infoValue)
                    classBuilder.addFunction(funSpec)
                    if (primaryOriginatingFile == null) {
                        primaryOriginatingFile = function.containingFile
                    }
                } else {
                    logger.error(
                        "LogInfo annotation on ${interfaceDeclaration.qualifiedName?.asString()}.${function.simpleName.asString()} " +
                                "is missing 'info' argument or it's not a String.",
                        function
                    )
                    unableToProcess.add(function) // 処理できなかったシンボルを追加
                }
            }

            // このインターフェースに対してメソッドが1つでも生成された場合のみファイルを作成
            if (classBuilder.funSpecs.isNotEmpty()) {
                val fileSpec = FileSpec.builder(packageName, generatedClassName)
                    .addType(classBuilder.build())
                    .build()

                try {
                    // 依存関係には、このインターフェース(とメソッド群)が定義されているファイルを指定
                    // グループ内の最初の関数のファイルを使うか、すべての関数のファイルを集約することも可能
                    // ここでは、インターフェース定義ファイルが主であると仮定
                    val dependencies = Dependencies(true, primaryOriginatingFile ?: functionsInInterface.first().containingFile!!)
                    fileSpec.writeTo(codeGenerator, dependencies)
                    logger.info("Generated $packageName.$generatedClassName for interface $interfaceName")
                } catch (e: FileAlreadyExistsException) {
                    // このロジック変更により、基本的には発生しなくなるはず
                    logger.error("File $generatedClassName already exists. This should not happen with the new logic. ${e.message}", interfaceDeclaration)
                    unableToProcess.addAll(functionsInInterface)
                }
                catch (e: Exception) {
                    logger.error("Error generating file $generatedClassName: ${e.stackTraceToString()}", interfaceDeclaration)
                    unableToProcess.addAll(functionsInInterface)
                }
            }
        }
        return unableToProcess.distinct() // 重複を除いて返す
    }

    // FunSpec を生成するヘルパーメソッド (クラス内に移動またはloggerを渡す)
    private fun createLoggerMethodSpec(
        interfaceDeclaration: KSClassDeclaration,
        function: KSFunctionDeclaration,
        infoValue: String
    ): FunSpec {
        val interfaceName = interfaceDeclaration.simpleName.asString()
        val methodName = function.simpleName.asString()

        val parameterSpecs = function.parameters.mapNotNull { ksValueParameter ->
            val name = ksValueParameter.name?.asString()
            // 型を正しく解決するために KSType.toTypeName() を使う
            val type = ksValueParameter.type.resolve().toClassName().copy(nullable = ksValueParameter.type.resolve().isMarkedNullable)

            if (name != null) {
                ParameterSpec.builder(name, type).build()
            } else {
                // このloggerはクラスのプロパティとして参照
                this.logger.warn("Parameter in ${function.qualifiedName?.asString()} has no name.", ksValueParameter)
                null
            }
        }

        val methodBuilder = FunSpec.builder(methodName)
            .addModifiers(KModifier.PUBLIC)
            .returns(UNIT)

        parameterSpecs.forEach { methodBuilder.addParameter(it) }

        methodBuilder.addStatement(
            "println(\"--- Invoking method: %L.%L ---\")",
            interfaceName,
            methodName
        )
        methodBuilder.addStatement(
            "println(\"Annotation info: '%L'\")",
            infoValue
        )

        if (parameterSpecs.isNotEmpty()) {
            methodBuilder.addStatement("println(\"Arguments:\")")
            parameterSpecs.forEach { paramSpec ->
                methodBuilder.addStatement("println(\"  %L (\${%T}): \${%N}\")", paramSpec.name, paramSpec.type, paramSpec.name)
            }
        } else {
            methodBuilder.addStatement("println(\"Arguments: (No arguments)\")")
        }
        methodBuilder.addStatement("println(\"--- End of invocation ---\")\n")

        return methodBuilder.build()
    }
}