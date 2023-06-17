package com.storyteller_f.yong.checks

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

interface Throwable {
    fun throwList(): Set<ThrowableDefinition>
}

interface Debug {
    fun debug(): String

    fun debugTree(): String = debug()
}

class RootNode(val contextNodes: MutableList<ContextNode> = mutableListOf(), val entranceNodes: MutableList<EntranceNode> = mutableListOf()) : Node() {
    override fun debug(): String {
        return "Root"
    }

    fun isEmpty(): Boolean {
        return contextNodes.isEmpty() && entranceNodes.isEmpty()
    }

}

class EntranceNode(override val methods: MutableList<MethodNode>, override val name: String): Node(), Named, MethodContainer, Throwable {
    override fun debug(): String {
        return "Entrance($name)"
    }

    override fun throwList(): Set<ThrowableDefinition> {
        return methods.flatMap {
            it.throwList()
        }.toSet()
    }

}

class ContextNode(
    override val methods: MutableList<MethodNode>,
    override val name: String
) :
    Node(), Named, MethodContainer, Throwable {
    override fun debug(): String {
        val label = when {
            name.endsWith("Activity") -> "Activity"
            name.endsWith("Fragment") -> "Fragment"
            else -> "Context"
        }
        return "$label($name)"
    }

    override fun throwList(): Set<ThrowableDefinition> {
        return methods.flatMap {
            it.throwList()
        }.toSet()
    }
}

class MethodNode(
    override val name: String,
    val key: MethodKey,
    private val declareThrows: MutableList<ThrowableDefinition> = mutableListOf(),
    val throwNodes: MutableList<ThrowNode> = mutableListOf(),
    val tryBlock: MutableList<TryCatchSubstitution> = mutableListOf(),
    override val methods: MutableList<MethodNode> = mutableListOf(),
) : Node(), Named, MethodContainer, Throwable{
    override fun debug(): String {
        return "Method($name)"
    }

    override fun debugTree(): String {
        return debug() + " " + declareThrows.joinToString {
            it.name
        }
    }

    override fun throwList(): Set<ThrowableDefinition> {
        val nodeThrows = throwNodes.flatMap {
            it.throwList()
        }.toSet()
        val childMethodThrows = methods.flatMap {
            it.throwList()
        }.toSet()
        val tryLeak = tryBlock.flatMap {
            it.throwList()
        }.toSet()
        return declareThrows.toSet() + nodeThrows + childMethodThrows + tryLeak
    }
}

/**
 * 临时节点，用于判断当前visitMethod 是不是需要throw
 * 虽然是methodNode，但是执行的是初始化操作。
 */
class ThrowNode(override val methods: MutableList<MethodNode> = mutableListOf()): Node(),
    MethodContainer, Throwable {
    override fun debug(): String {
        return "Throw()"
    }

    override fun throwList(): Set<ThrowableDefinition> {
        return methods.flatMap { node ->
            node.throwList()
        }.toSet()
    }
}

class TryCatchSubstitution(
    private val caught: MutableList<ThrowableDefinition>,
    override val methods: MutableList<MethodNode> = mutableListOf(),
) : Node(), MethodContainer, Throwable {
    override fun debug(): String {
        return "Caught(${
            caught.joinToString {
                it.name
            }
        }{${
            methods.joinToString {
                it.debug()
            }
        })"
    }

    override fun debugTree(): String {
        return "Caught(${caught.joinToString {
            it.name
        }})"
    }

    override fun throwList(): Set<ThrowableDefinition> {
        val throws = methods.flatMap { methodNode ->
            methodNode.throwList()
        }.toSet()
        val caught = caught.toSet()
        return throws.filter {
            !caught.any { parent ->
                parent.name == it.name || parent.hasChild(it)
            }
        }.toSet()
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
    if (node is MethodContainer) {
        node.methods.forEach {
            printTree(it, context, nextStep)
        }
    }
    when (node) {
        is RootNode -> {
            node.contextNodes.forEach {
                printTree(it, context, nextStep)
            }
            node.entranceNodes.forEach {
                printTree(it, context, nextStep)
            }
        }

        is MethodNode -> {
            node.tryBlock.forEach {
                printTree(it, context, nextStep)
            }
            node.throwNodes.forEach {
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