package com.example.lint.checks

import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UMethod

private val UMethod.key: String
    get() {
        return "${containingFile?.containingDirectory}-${containingClass?.qualifiedName}-${hierarchicalMethodSignature}"
    }

abstract class Node : Debug

interface Named {
    val name: String
}

interface MethodContainer {
    val methods: MutableList<MethodNode>
}

interface Debug {
    fun debug(): String

    fun debugTree(): String = debug()
}

class RootNode(val activities: MutableList<ActivityNode>) : Node() {
    override fun debug(): String {
        return "Root"
    }

}

class ActivityNode(
    override val methods: MutableList<MethodNode>,
    override val name: String
) :
    Node(), Named, MethodContainer {
    override fun debug(): String {
        return "Activity($name)"
    }
}

class MethodNode(
    override val name: String,
    val key: MethodKey,
    val throws: MutableList<String>,
    val tryBlock: MutableList<TryCatchSubstitution>,
    override val methods: MutableList<MethodNode>,
) : Node(), Named, MethodContainer {
    override fun debug(): String {
        return "Method($name)"
    }

    override fun debugTree(): String {
        return debug() + " " + throws.joinToString()
    }

    internal fun replace(
        throws: Set<String>
    ) {
        this.throws.clear()
        this.throws.addAll(throws.toMutableList())
    }

    internal fun throwList() =
        throws.toSet() + methods.flatMap {
            it.throws
        }.toSet() + tryBlock.flatMap {
            it.methods.flatMap { methodNode ->
                methodNode.throws
            }.toSet() - it.caught.toSet()
        }.toSet()
}

/**
 * 临时节点，用于判断当前visitMethod 是不是需要throw
 */
class ThrowNode: Node() {
    override fun debug(): String {
        return "Throw()"
    }
}

class TryCatchSubstitution(
    val caught: MutableList<String>,
    override val methods: MutableList<MethodNode>,
) : Node(), MethodContainer {
    override fun debug(): String {
        return "try(${caught.joinToString()}{${
            methods.joinToString {
                it.debug()
            }
        })"
    }

    override fun debugTree(): String {
        return "try(${caught.joinToString()})"
    }
}

data class MethodKey(val key: String) {
    constructor(uMethod: UMethod) : this(uMethod.key)
}

internal fun printTree(node: Node, context: JavaContext, step: Int) {
    context.client.log(
        Severity.INFORMATIONAL,
        null,
        "${indent(step)}${node.debugTree()}"
    )
    val nextStep = step + 1
    when (node) {
        is RootNode -> {
            node.activities.forEach {
                printTree(it, context, nextStep)
            }
        }

        is ActivityNode -> {
            node.methods.forEach {
                printTree(it, context, nextStep)
            }
        }

        is MethodNode -> {
            node.methods.forEach {
                printTree(it, context, nextStep)
            }
            node.tryBlock.forEach {
                printTree(it, context, nextStep)
            }
        }

        is TryCatchSubstitution -> {
            node.methods.forEach {
                printTree(it, context, nextStep)
            }
        }
    }
}

internal fun indent(step: Int): String {
    return List(step) {
        "\t"
    }.joinToString("")
}