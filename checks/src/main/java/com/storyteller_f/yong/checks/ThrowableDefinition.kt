package com.storyteller_f.yong.checks

import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType

class ThrowableDefinition(val name: String, val children: MutableList<ThrowableDefinition> = mutableListOf()) {

    fun hasChild(node: ThrowableDefinition): Boolean {
        return children.any {
            it.name == node.name || it.children.any { definition ->
                definition.hasChild(node)
            }
        }
    }

    fun addChild(node: ThrowableDefinition) {
        children.add(node)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ThrowableDefinition

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        val rootDefinition = ThrowableDefinition("java.lang.throwable")

        private val resolvedDefinition = mutableMapOf(rootDefinition.name to rootDefinition)
        private fun addChild(node: ThrowableDefinition, parents: List<String>) {
            val parent = parents.reversed().fold(rootDefinition) { p, clazz ->
                val cache = p.children.find {
                    it.name == clazz
                }
                if (cache != null) cache
                else {
                    val new = ThrowableDefinition(clazz)
                    resolvedDefinition[clazz] = new
                    p.addChild(new)
                    new
                }
            }
            parent.addChild(node)
            resolvedDefinition[node.name] = node
        }

        fun throwableDefinition(psiClassType: PsiClassType) = throwableDefinition(psiClassType.resolve()!!) {
            it.supers().subList(0, it.supers().size - 2)
        }

        fun throwableDefinition(psiClass: PsiClass) = throwableDefinition(psiClass) {
            it.supers().subList(0, it.supers().size - 2)
        }

        private fun throwableDefinition(psiClass: PsiClass, supers: (PsiClass) -> List<String>): ThrowableDefinition {
            val unrecognized = psiClass.qualifiedName!!
            val recognized = resolvedDefinition[unrecognized]
            return if (recognized != null) recognized
            else {
                val node = ThrowableDefinition(unrecognized)
                val parents = supers(psiClass)
                addChild(node, parents)
                node
            }
        }

        internal fun printTree(node: ThrowableDefinition, context: JavaContext, step: Int) {
            context.client.log(
                Severity.INFORMATIONAL,
                null,
                "${indent(step)}${node.name}"
            )
            val nextStep = step + 1
            node.children.forEach {
                printTree(it, context, nextStep)
            }
        }

        fun printTree(context: JavaContext) {
            if (rootDefinition.children.isNotEmpty())
                printTree(rootDefinition, context, 0)
        }



    }
}

