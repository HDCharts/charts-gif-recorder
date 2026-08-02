package io.github.hdcodedev.composegif.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.hdcodedev.composegif.annotations.GifSwipeSpeed
import io.github.hdcodedev.composegif.annotations.RecordGif

private const val GENERATED_PACKAGE = "io.github.hdcodedev.composegif.generated"
private const val GENERATED_OBJECT_NAME = "GeneratedGifScenarioRegistry"
private const val GENERATED_METADATA_FILE_NAME = "GeneratedGifScenarioMetadata"
private const val METADATA_SCHEMA_VERSION = 1
private const val DEFAULT_DURATION_MS = 3000
private val VALID_SCENARIO_NAME_REGEX = Regex("[a-zA-Z0-9_\\-]+")

/** KSP provider that registers the `@RecordGif` symbol processor. */
public class RecordGifSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        RecordGifSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
}

private class RecordGifSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var generated: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val annotationName = RecordGif::class.qualifiedName ?: return emptyList()
        val symbols = resolver.getSymbolsWithAnnotation(annotationName).toList()

        val deferred = symbols.filterNot { it.validate() }
        val functions = symbols.filterIsInstance<KSFunctionDeclaration>()

        val validEntries = mutableListOf<ScenarioEntry>()
        val duplicateCheck = mutableSetOf<String>()
        var hasErrors = false

        functions.forEach { function ->
            val qualifiedName = function.qualifiedName?.asString()
            if (qualifiedName == null) {
                logger.error("@RecordGif can only be used on named functions.", function)
                hasErrors = true
                return@forEach
            }
            if (function.parameters.isNotEmpty()) {
                logger.error("@RecordGif supports only parameterless composable functions.", function)
                hasErrors = true
                return@forEach
            }
            if (function.parentDeclaration != null) {
                logger.error("@RecordGif currently supports only top-level composable functions.", function)
                hasErrors = true
                return@forEach
            }
            if (Modifier.PRIVATE in function.modifiers) {
                logger.error(
                    "@RecordGif scenarios must be public or internal; private top-level functions cannot be referenced from the generated registry.",
                    function,
                )
                hasErrors = true
                return@forEach
            }
            val isComposable =
                function.annotations.any {
                    val declaration = it.annotationType.resolve().declaration
                    declaration.simpleName.asString() == "Composable" &&
                        declaration.qualifiedName?.asString() == "androidx.compose.runtime.Composable"
                }
            if (!isComposable) {
                logger.error("@RecordGif target must also be annotated with @Composable.", function)
                hasErrors = true
                return@forEach
            }

            val args = function.extractArguments()
            val scenarioName =
                args.name.takeIf { it.isNotBlank() } ?: ScenarioNaming.defaultName(function.simpleName.asString())
            if (!scenarioName.matches(VALID_SCENARIO_NAME_REGEX)) {
                logger.error(
                    "Invalid @RecordGif scenario name '$scenarioName'. Use only [a-zA-Z0-9_-], or set an explicit name.",
                    function,
                )
                hasErrors = true
                return@forEach
            }
            if (!duplicateCheck.add(scenarioName)) {
                logger.error("Duplicate @RecordGif scenario name '$scenarioName'.", function)
                hasErrors = true
                return@forEach
            }

            validEntries +=
                ScenarioEntry(
                    name = scenarioName,
                    qualifiedFunctionName = qualifiedName,
                    durationMs = args.durationMs,
                    fps = args.fps,
                    widthPx = args.widthPx,
                    heightPx = args.heightPx,
                    theme = args.theme,
                    interactionNodeTag = args.interactionNodeTag,
                    gestures = args.gestures,
                )
        }

        if (hasErrors) return deferred
        if (validEntries.isEmpty()) return deferred

        generateRegistry(validEntries)
        generated = true
        return deferred
    }

    private fun generateRegistry(entries: List<ScenarioEntry>) {
        val scenarioSpec = ClassName("io.github.hdcodedev.composegif.core", "GifScenarioSpec")
        val captureConfig = ClassName("io.github.hdcodedev.composegif.core", "GifCaptureConfig")
        val coreTheme = ClassName("io.github.hdcodedev.composegif.core", "GifTheme")
        val coreGestureType = ClassName("io.github.hdcodedev.composegif.core", "GifGestureType")
        val coreGestureStep = ClassName("io.github.hdcodedev.composegif.core", "GifGestureStep")
        val coreFractionPoint = ClassName("io.github.hdcodedev.composegif.core", "GifFractionPoint")
        val scenarioRegistry = ClassName("io.github.hdcodedev.composegif.core", "GifScenarioRegistry")
        val validator = ClassName("io.github.hdcodedev.composegif.core", "GifCaptureValidator")

        val scenariosProperty =
            PropertySpec
                .builder(
                    "scenarios",
                    List::class.asClassName().parameterizedBy(scenarioSpec),
                    KModifier.PRIVATE,
                ).initializer(
                    CodeBlock
                        .builder()
                        .apply {
                            add("listOf(\n")
                            entries.forEachIndexed { index, entry ->
                                val gesturesCode =
                                    buildGestureListCode(
                                        gestures = entry.gestures,
                                        gestureStepClass = coreGestureStep,
                                        gestureTypeClass = coreGestureType,
                                        fractionPointClass = coreFractionPoint,
                                    )
                                add(
                                    "  %T(name = %S, capture = %T(durationMs = %L, fps = %L, widthPx = %L, heightPx = %L, theme = %T.%L, interactionNodeTag = %S, gestures = %L))",
                                    scenarioSpec,
                                    entry.name,
                                    captureConfig,
                                    entry.durationMs,
                                    entry.fps,
                                    entry.widthPx,
                                    entry.heightPx,
                                    coreTheme,
                                    entry.theme,
                                    entry.interactionNodeTag,
                                    gesturesCode,
                                )
                                if (index != entries.lastIndex) {
                                    add(",\n")
                                } else {
                                    add("\n")
                                }
                            }
                            add(")")
                        }.build(),
                ).build()

        val namesProperty =
            PropertySpec
                .builder(
                    "SCENARIO_NAMES",
                    List::class.asClassName().parameterizedBy(String::class.asClassName()),
                ).initializer(
                    CodeBlock
                        .builder()
                        .apply {
                            add("listOf(")
                            entries.forEachIndexed { index, entry ->
                                if (index > 0) add(", ")
                                add("%S", entry.name)
                            }
                            add(")")
                        }.build(),
                ).build()

        val scenariosFun =
            FunSpec
                .builder("scenarios")
                .addModifiers(KModifier.OVERRIDE)
                .returns(List::class.asClassName().parameterizedBy(scenarioSpec))
                .addStatement("return scenarios")
                .build()

        val renderFun =
            FunSpec
                .builder("Render")
                .addModifiers(KModifier.OVERRIDE)
                .addAnnotation(ClassName("androidx.compose.runtime", "Composable"))
                .addParameter("name", String::class)
                .beginControlFlow("when (name)")
                .apply {
                    entries.forEach { entry ->
                        addStatement("%S -> %L()", entry.name, entry.qualifiedFunctionName)
                    }
                    addStatement("else -> error(%P)", "Unknown GIF scenario: \$name")
                }.endControlFlow()
                .build()

        val initBlock =
            CodeBlock
                .builder()
                .beginControlFlow("for (scenario in scenarios)")
                .addStatement("%T.validate(scenario)", validator)
                .endControlFlow()
                .build()

        val type =
            TypeSpec
                .objectBuilder(GENERATED_OBJECT_NAME)
                .addSuperinterface(scenarioRegistry)
                .addProperty(scenariosProperty)
                .addProperty(namesProperty)
                .addInitializerBlock(initBlock)
                .addFunction(scenariosFun)
                .addFunction(renderFun)
                .build()

        FileSpec
            .builder(GENERATED_PACKAGE, GENERATED_OBJECT_NAME)
            .addType(type)
            .build()
            .writeTo(codeGenerator = codeGenerator, aggregating = true)

        codeGenerator
            .createNewFile(
                dependencies = Dependencies(aggregating = true),
                packageName = GENERATED_PACKAGE,
                fileName = GENERATED_METADATA_FILE_NAME,
                extensionName = "properties",
            ).bufferedWriter()
            .use { writer ->
                writer.appendLine("schema=$METADATA_SCHEMA_VERSION")
                writer.appendLine("count=${entries.size}")
                entries.forEachIndexed { index, entry ->
                    writer.appendLine("scenario.$index.name=${entry.name}")
                    writer.appendLine("scenario.$index.fps=${entry.fps}")
                }
            }
    }

    private fun KSFunctionDeclaration.extractArguments(): AnnotationArgs {
        val args = annotations.first { it.shortName.asString() == "RecordGif" }.arguments
        val fps = args.valueAsInt("fps") ?: 50
        val interactionStartDelayMs = args.valueAsInt("interactionStartDelayMs") ?: DEFAULT_INTERACTION_START_DELAY_MS
        val explicitGestures =
            args.valueAsAnnotations("gestures").map { gesture ->
                val gestureArgs = gesture.arguments
                GestureSpec(
                    type = gestureArgs.valueAsEnumName("type") ?: "PAUSE",
                    frames = gestureArgs.valueAsInt("frames") ?: 0,
                    xFraction = gestureArgs.valueAsFloat("xFraction") ?: 0.5f,
                    yFraction = gestureArgs.valueAsFloat("yFraction") ?: 0.5f,
                    framesAfter = gestureArgs.valueAsInt("framesAfter") ?: 0,
                    points =
                        gestureArgs.valueAsAnnotations("points").map { point ->
                            val pointArgs = point.arguments
                            PointSpec(
                                x = pointArgs.valueAsFloat("x") ?: 0f,
                                y = pointArgs.valueAsFloat("y") ?: 0f,
                            )
                        },
                    holdStartFrames = gestureArgs.valueAsInt("holdStartFrames") ?: 0,
                    framesPerWaypoint = gestureArgs.valueAsInt("framesPerWaypoint") ?: 0,
                    releaseFrames = gestureArgs.valueAsInt("releaseFrames") ?: 0,
                )
            }
        val interactionGestures =
            args.valueAsAnnotations("interactions").flatMap { interaction ->
                val interactionArgs = interaction.arguments
                InteractionGestureExpander.expand(
                    InteractionSpec(
                        type = interactionArgs.valueAsEnumName("type") ?: "PAUSE",
                        frames = interactionArgs.valueAsInt("frames") ?: 0,
                        framesAfter = interactionArgs.valueAsInt("framesAfter") ?: 0,
                        target = interactionArgs.valueAsEnumName("target") ?: "CENTER",
                        direction = interactionArgs.valueAsEnumName("direction") ?: "LEFT_TO_RIGHT",
                        distance = interactionArgs.valueAsEnumName("distance") ?: "MEDIUM",
                        speed =
                            interactionArgs
                                .valueAsEnumName("speed")
                                ?.let { enumName -> runCatching { GifSwipeSpeed.valueOf(enumName) }.getOrNull() }
                                ?: GifSwipeSpeed.CUSTOM,
                        travelFrames = interactionArgs.valueAsInt("travelFrames") ?: 8,
                        holdStartFrames = interactionArgs.valueAsInt("holdStartFrames") ?: 0,
                        releaseFrames = interactionArgs.valueAsInt("releaseFrames") ?: 0,
                    ),
                )
            }
        val gestures =
            applyInteractionStartDelay(
                gestures = interactionGestures + explicitGestures,
                interactionStartDelayMs = interactionStartDelayMs,
                fps = fps,
            )
        val durationMs =
            ensureDurationMsAtLeastGestureBudget(
                durationMs = args.valueAsInt("durationMs") ?: DEFAULT_DURATION_MS,
                fps = fps,
                gestures = gestures,
            )
        return AnnotationArgs(
            name = args.valueAsString("name") ?: "",
            durationMs = durationMs,
            fps = fps,
            widthPx = args.valueAsInt("widthPx") ?: 540,
            heightPx = args.valueAsInt("heightPx") ?: 0,
            theme = (args.valueAsEnumName("theme") ?: "DARK"),
            interactionNodeTag = args.valueAsString("interactionNodeTag") ?: "",
            gestures = gestures,
        )
    }

    private fun buildGestureListCode(
        gestures: List<GestureSpec>,
        gestureStepClass: ClassName,
        gestureTypeClass: ClassName,
        fractionPointClass: ClassName,
    ): CodeBlock =
        CodeBlock
            .builder()
            .apply {
                if (gestures.isEmpty()) {
                    add("emptyList()")
                    return@apply
                }
                add("listOf(\n")
                gestures.forEachIndexed { index, gesture ->
                    val pointsCode = buildPointListCode(gesture.points, fractionPointClass)
                    add(
                        "      %T(type = %T.%L, frames = %L, xFraction = %L.toFloat(), yFraction = %L.toFloat(), framesAfter = %L, points = %L, holdStartFrames = %L, framesPerWaypoint = %L, releaseFrames = %L)",
                        gestureStepClass,
                        gestureTypeClass,
                        gesture.type,
                        gesture.frames,
                        gesture.xFraction,
                        gesture.yFraction,
                        gesture.framesAfter,
                        pointsCode,
                        gesture.holdStartFrames,
                        gesture.framesPerWaypoint,
                        gesture.releaseFrames,
                    )
                    if (index != gestures.lastIndex) {
                        add(",\n")
                    } else {
                        add("\n")
                    }
                }
                add("    )")
            }.build()

    private fun buildPointListCode(
        points: List<PointSpec>,
        fractionPointClass: ClassName,
    ): CodeBlock =
        CodeBlock
            .builder()
            .apply {
                if (points.isEmpty()) {
                    add("emptyList()")
                    return@apply
                }
                add("listOf(\n")
                points.forEachIndexed { index, point ->
                    add(
                        "          %T(x = %L.toFloat(), y = %L.toFloat())",
                        fractionPointClass,
                        point.x,
                        point.y,
                    )
                    if (index != points.lastIndex) {
                        add(",\n")
                    } else {
                        add("\n")
                    }
                }
                add("      )")
            }.build()

    private data class AnnotationArgs(
        val name: String,
        val durationMs: Int,
        val fps: Int,
        val widthPx: Int,
        val heightPx: Int,
        val theme: String,
        val interactionNodeTag: String,
        val gestures: List<GestureSpec>,
    )

    private data class ScenarioEntry(
        val name: String,
        val qualifiedFunctionName: String,
        val durationMs: Int,
        val fps: Int,
        val widthPx: Int,
        val heightPx: Int,
        val theme: String,
        val interactionNodeTag: String,
        val gestures: List<GestureSpec>,
    )
}

private fun List<KSValueArgument>.valueAsString(name: String): String? =
    firstOrNull { it.name?.asString() == name }?.value as? String

private fun List<KSValueArgument>.valueAsInt(name: String): Int? =
    firstOrNull { it.name?.asString() == name }?.value as? Int

private fun List<KSValueArgument>.valueAsFloat(name: String): Float? {
    val value = firstOrNull { it.name?.asString() == name }?.value ?: return null
    return when (value) {
        is Float -> value
        is Double -> value.toFloat()
        else -> null
    }
}

private fun List<KSValueArgument>.valueAsAnnotations(name: String): List<KSAnnotation> {
    val value = firstOrNull { it.name?.asString() == name }?.value ?: return emptyList()
    return when (value) {
        is List<*> -> value.mapNotNull { it as? KSAnnotation }
        is KSAnnotation -> listOf(value)
        else -> emptyList()
    }
}

private fun List<KSValueArgument>.valueAsEnumName(name: String): String? {
    val value = firstOrNull { it.name?.asString() == name }?.value ?: return null
    return value.toString().substringAfterLast('.')
}
