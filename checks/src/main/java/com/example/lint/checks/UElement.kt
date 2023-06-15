package com.example.lint.checks

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTryExpression

fun UTryExpression.safeExceptions(): List<@NlsSafe String> {
    val exceptions =
        catchClauses.flatMap<UCatchClause, @NlsSafe String> { clause ->
            clause.types.map<PsiType, @NlsSafe String> {
                it.canonicalText
            }
        }
    return exceptions
}

fun UMethod.throwExceptions(): List<@NlsSafe String> {
    val throws = throwsList.referencedTypes.map {
        it.canonicalText
    }
    return throws
}