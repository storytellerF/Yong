package com.storyteller_f.yong.checks

import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTryExpression

fun UTryExpression.safeExceptions(): List<ThrowableDefinition> {
    return catchClauses.flatMap { clause ->
        clause.types.map {
            ThrowableDefinition.throwableDefinition(it as PsiClassType)
        }
    }
}

fun UMethod.throwExceptions(): List<ThrowableDefinition> {
    return throwsList.referencedTypes.map {
        ThrowableDefinition.throwableDefinition(it)
    }
}

internal fun UMethod.methodKey() = MethodKey(this)
