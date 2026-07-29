package com.netgraphqx.engine

import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.ValidationResult
import kotlin.math.abs

/**
 * Lightweight math expression engine wrapping exp4j.
 *
 * Evaluates user-supplied f(x) expressions over a coordinate range,
 * returning discrete [GraphSample] points suitable for Canvas rendering.
 */
class MathEngine {

    /** Result of a single evaluation. */
    data class GraphSample(
        val x: Float,
        val y: Float,
        val valid: Boolean
    )

    /** Expression validation outcome. */
    data class ValidationOutcome(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Validate a mathematical expression string.
     * Returns [ValidationOutcome.isValid] true if the expression parses correctly.
     */
    fun validate(expression: String): ValidationOutcome {
        if (expression.isBlank()) {
            return ValidationOutcome(false, "Expression cannot be empty")
        }

        return try {
            val expr = ExpressionBuilder(expression)
                .variable("x")
                .build()
            val result: ValidationResult = expr.validate()
            if (result.isValid) {
                ValidationOutcome(true)
            } else {
                ValidationOutcome(false, result.errors.joinToString("; "))
            }
        } catch (e: Exception) {
            ValidationOutcome(false, e.message ?: "Unknown parse error")
        }
    }

    /**
     * Evaluate f(x) for a single x value.
     * Returns null if evaluation fails.
     */
    fun evaluate(expression: String, x: Double): Double? {
        return try {
            ExpressionBuilder(expression)
                .variable("x")
                .build()
                .setVariable("x", x)
                .evaluate()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sample f(x) across [xMin, xMax] at a given [step] resolution.
     * Produces a list of [GraphSample] for canvas plotting.
     *
     * @param expression The f(x) string
     * @param xMin Lower bound
     * @param xMax Upper bound
     * @param step Increment between samples (lower = smoother curve)
     */
    fun sample(
        expression: String,
        xMin: Float,
        xMax: Float,
        step: Float = 0.01f
    ): List<GraphSample> {
        if (expression.isBlank() || xMax <= xMin || step <= 0f) return emptyList()

        val samples = mutableListOf<GraphSample>()
        var x = xMin

        while (x <= xMax) {
            val y = evaluate(expression, x.toDouble())
            samples.add(
                if (y != null && y.isFinite()) {
                    GraphSample(x = x, y = y.toFloat(), valid = true)
                } else {
                    GraphSample(x = x, y = 0f, valid = false)
                }
            )
            x += step
        }

        return samples
    }

    /**
     * Adaptive sampling — uses finer steps near steep regions.
     * Falls back to uniform sampling; override point for future enhancement.
     */
    fun adaptiveSample(
        expression: String,
        xMin: Float,
        xMax: Float,
        baseStep: Float = 0.02f
    ): List<GraphSample> {
        // Uniform sample for v1; adaptive subdivision can replace later
        return sample(expression, xMin, xMax, baseStep)
    }

    /**
     * Find the nearest valid sample near a given x (for coordinate tracing).
     */
    fun findNearest(samples: List<GraphSample>, targetX: Float): GraphSample? {
        if (samples.isEmpty()) return null
        return samples
            .filter { it.valid }
            .minByOrNull { abs(it.x - targetX) }
    }

    companion object {
        /** Predefined function shortcuts. */
        val BUILTIN_FUNCTIONS = mapOf(
            "sin" to "sin(x)",
            "cos" to "cos(x)",
            "tan" to "tan(x)",
            "sqrt" to "sqrt(x)",
            "x^2" to "x^2",
            "x^3" to "x^3",
            "1/x" to "1 / x",
            "log10" to "log10(x)",
            "ln" to "log(x)",
            "abs" to "abs(x)"
        )
    }
}
