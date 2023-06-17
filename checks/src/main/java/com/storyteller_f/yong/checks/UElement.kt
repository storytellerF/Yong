package com.storyteller_f.yong.checks

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass
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

internal fun UMethod.isMainMethod() =
    name == "main" && isStatic && returnType == PsiType.VOID

internal fun PsiClass.supers(): MutableList<String> {
    val list = mutableListOf<String>()
    var su: PsiType = superTypes.first()
    while (true) {
        list.add(su.canonicalText)
        val superClass = su.superTypes.firstOrNull() ?: break
        su = superClass
    }
    return list
}

internal fun UClass.isLogicalContext(): Boolean {
    val supers = supers()
    val list = listOf(
        "android.content.Context",
        "androidx.fragment.app.Fragment",
        "android.content.BroadcastReceiver",
        "android.content.ContentProvider"
    )
    return supers.any {
        list.contains(it)
    }
}