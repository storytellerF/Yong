package com.example.lint.checks

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType

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
                if (cache == null) {
                    val new = ThrowableDefinition(clazz)
                    resolvedDefinition[clazz] = new
                    p.addChild(new)
                    new
                } else cache
            }
            parent.addChild(node)
            resolvedDefinition[node.name] = node
        }

        fun throwableDefinition(psiClassType: PsiClassType) = throwableDefinition(psiClassType.resolve()!!) { it ->
            val list = mutableListOf<String>()
            var su: PsiType = it.superTypes.first()
            while (true) {
                list.add(su.canonicalText)
                val superClass = su.superTypes.firstOrNull() ?: break
                su = superClass
            }
            list.subList(0, list.size - 2)
        }

        fun throwableDefinition(psiClass: PsiClass) = throwableDefinition(psiClass) {
            val list = mutableListOf<String>()
            var su = psiClass.containingClass?.superClass!!
            while (true) {
                list.add(su.qualifiedName!!)
                val superClass = su.superClass ?: break
                su = superClass
            }
            list.subList(0, list.size - 2)
        }

        private fun throwableDefinition(psiClass: PsiClass, supers: (PsiClass) -> List<String>): ThrowableDefinition {
            val unrecognized = psiClass.qualifiedName!!
            val recognized = resolvedDefinition[unrecognized]
            return if (recognized == null) {
                val node = ThrowableDefinition(unrecognized)
                val parents = supers(psiClass)
                addChild(node, parents)
                node
            } else recognized
        }

    }
}

