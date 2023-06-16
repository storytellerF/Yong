package com.example.lint.checks

import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTryExpression

fun UTryExpression.safeExceptions(): List<String> {
    val exceptions =
        catchClauses.flatMap { clause ->
            clause.types.map {
                it.canonicalText
            }
        }
    return exceptions
}

fun UMethod.throwExceptions(): List<String> {
    val throws = throwsList.referencedTypes.map {
        it.canonicalText
    }
    return throws
}

internal fun UMethod.methodKey() = MethodKey(this)
