/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.UastScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.kotlin.utils.addToStdlib.popLast
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.kotlin.KotlinUBlockExpression
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Collections
import java.util.LinkedList

private val UMethod.key: String
    get() {
        return "${containingFile?.containingDirectory}-${containingClass?.qualifiedName}-${hierarchicalMethodSignature}"
    }

/**
 * @param resolved 是否所有的自节点都访问完毕
 */
abstract class Node(var resolved: Boolean) : Debug

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

class RootNode(val activities: MutableList<ActivityNode>, resolved: Boolean) : Node(resolved) {
    override fun debug(): String {
        return "Root"
    }

}

class ActivityNode(
    override val methods: MutableList<MethodNode>, val key: ActivityKey, resolved: Boolean,
    override val name: String
) :
    Node(resolved), Named, MethodContainer {
    override fun debug(): String {
        return "Activity($name)"
    }
}

class MethodNode(
    val tryBlock: MutableList<TryCatchSubstitution>,
    override val methods: MutableList<MethodNode>,
    val throws: MutableList<String>,
    resolved: Boolean,
    override val name: String,
    val key: MethodKey
) : Node(resolved), Named, MethodContainer {
    override fun debug(): String {
        return "Method($name)"
    }

    override fun debugTree(): String {
        return debug() + " " + throws.joinToString()
    }

}

class TryCatchSubstitution(
    val caught: MutableList<String>,
    override val methods: MutableList<MethodNode>,
    resolved: Boolean
) : Node(resolved), MethodContainer {
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

data class ActivityKey(val key: String) {
    constructor(uClass: UClass) : this(uClass.qualifiedName!!)
}

/**
 * Sample detector showing how to analyze Kotlin/Java code. This example
 * flags all string literals in the code that contain the word "lint".
 */
@Suppress("UnstableApiUsage")
class SampleCodeDetector : Detector(), UastScanner {
    private val root = RootNode(mutableListOf(), false)
    private val callStack = LinkedList<Node>(Collections.singleton(root))

    /**
     * 存储访问过的所有method
     */
    private val methodCache = mutableMapOf<MethodKey, MethodNode>()

    override fun getApplicableUastTypes(): List<Class<out UElement?>> {
        return listOf(UClass::class.java)
    }


    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            val visitor = object : AbstractUastVisitor() {
                override fun visitMethod(node: UMethod): Boolean {
                    val key = node.methodKey()
                    if (callStack.any {
                            it is MethodNode && it.key == key
                        }) {
                        return true
                    }
                    val throws = node.throwExceptions().toMutableList()
                    context.client.log(
                        Severity.IGNORE, null,
                        "visitMethod ${node.name} ${throws.size} ${(node.uastBody as? KotlinUBlockExpression)?.expressions?.size} ${
                            callStack.joinToString {
                                it.debug()
                            }
                        }"
                    )
                    val cache = methodCache[key]
                    val methodNode = cache ?: MethodNode(
                        mutableListOf(),
                        mutableListOf(),
                        throws,
                        throws.isNotEmpty(),
                        node.name,
                        key
                    )
                    if (cache == null) {
                        methodCache[key] = methodNode
                    }
                    when (val current = callStack.last) {
                        is ActivityNode -> {
                            current.methods.add(methodNode)
                        }

                        is TryCatchSubstitution -> {
                            current.methods.add(methodNode)
                        }

                        is MethodNode -> {
                            current.methods.add(methodNode)
                        }
                    }
                    //如果返回true，afterVisitMethod 也会跳过
                    if (throws.isNotEmpty() || cache != null) return true
                    callStack.addLast(methodNode)

                    return super.visitMethod(node)
                }

                override fun afterVisitMethod(node: UMethod) {
                    super.afterVisitMethod(node)
                    context.client.log(Severity.IGNORE, null, "out method ${node.name}")
                    val current = callStack.popLast() as MethodNode
                    val throws = current.methods.flatMap {
                        it.throws
                    }.toSet() + current.tryBlock.flatMap {
                        it.methods.flatMap { methodNode ->
                            methodNode.throws
                        }.toSet() - it.caught.toSet()
                    }
                    if (throws.isEmpty()) {
                        val pre = callStack.last
                        if (pre is MethodNode) {
                            pre.methods.remove(current)
                        } else if (pre is TryCatchSubstitution) {
                            pre.methods.remove(current)
                        }
                    } else {
                        current.throws.addAll(throws.toMutableList())
                        current.resolved = true
                        if (callStack.size == 2) {
                            context.report(
                                ISSUE, node, context.getLocation(node),
                                "uncaught exception ${throws.joinToString()}"
                            )
                        }
                    }

                }

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    context.client.log(Severity.IGNORE, null, "\tcall ${node.methodName} $node")
                    val current = callStack.last
                    if (node is KotlinUFunctionCallExpression) {
                        val uElement = node.resolveToUElement()
                        if (uElement != null) {
                            if (uElement is UMethod) {
                                val key = uElement.methodKey()
                                val methodNode = methodCache[key]
                                if (methodNode != null) {
                                    if (current is MethodNode) {
                                        current.methods.add(methodNode)
                                    }
                                } else {
                                    uElement.accept(this)
                                }
                            }
                        }
                    }
                    return super.visitCallExpression(node)
                }

                override fun visitTryExpression(node: UTryExpression): Boolean {
                    val exceptions = node.safeExceptions().toMutableList()
                    context.client.log(Severity.IGNORE, null, "\tenter try $node $exceptions")
                    val tryCatchSubstitution =
                        TryCatchSubstitution(exceptions, mutableListOf(), false)
                    val current = callStack.last
                    if (current is MethodNode) {
                        current.tryBlock.add(tryCatchSubstitution)
                        callStack.addLast(tryCatchSubstitution)
                    }

                    return super.visitTryExpression(node)
                }

                override fun afterVisitTryExpression(node: UTryExpression) {
                    context.client.log(Severity.IGNORE, null, "\tout try $node")
                    val current = callStack.popLast() as TryCatchSubstitution
                    val strings = current.methods.flatMap {
                        it.throws
                    }.toSet() - current.caught.toSet()
                    if (strings.isEmpty()) {
                        val pre = callStack.last
                        if (pre is MethodNode) {
                            pre.tryBlock.remove(current)
                        }
                    }
                    super.afterVisitTryExpression(node)
                }

            }

            override fun visitClass(node: UClass) {
                context.client.log(Severity.IGNORE, null, "visitClass ${node.qualifiedName}")
                val isActivity = node.supers.any {
                    it.qualifiedName == "androidx.appcompat.app.AppCompatActivity"
                }
                if (isActivity) {
                    val activityNode =
                        ActivityNode(mutableListOf(), ActivityKey(node), false, node.name!!)
                    root.activities.add(activityNode)
                    callStack.addLast(activityNode)
                    node.methods.filter {
                        it.name == "onResume"
                    }.forEach {
                        it.accept(visitor)
                    }
                    callStack.removeLast()
                    printTree(root, context, 0)
                }

            }

            private fun printTree(node: Node, context: JavaContext, step: Int) {
                context.client.log(
                    Severity.INFORMATIONAL,
                    null,
                    "${indent(step)}${node.debugTree()}"
                )
                when (node) {
                    is RootNode -> {
                        node.activities.forEach {
                            printTree(it, context, step + 1)
                        }
                    }

                    is ActivityNode -> {
                        node.methods.forEach {
                            printTree(it, context, step + 1)
                        }
                    }

                    is MethodNode -> {
                        node.methods.forEach {
                            printTree(it, context, step + 1)
                        }
                        node.tryBlock.forEach {
                            printTree(it, context, step + 1)
                        }
                    }

                    is TryCatchSubstitution -> {
                        node.methods.forEach {
                            printTree(it, context, step + 1)
                        }
                    }
                }
            }

            private fun indent(step: Int): String {
                return List(step) {
                    "\t"
                }.joinToString("")
            }
        }
    }

    private fun UMethod.methodKey() = MethodKey(this)

    companion object {
        /**
         * Issue describing the problem and pointing to the detector
         * implementation.
         */
        @JvmField
        val ISSUE: Issue = Issue.create(
            // ID: used in @SuppressLint warnings etc
            id = "SampleId",
            // Title -- shown in the IDE's preference dialog, as category headers in the
            // Analysis results window, etc
            briefDescription = "Lint Mentions",
            // Full explanation of the issue; you can use some markdown markup such as
            // `monospace`, *italic*, and **bold**.
            explanation = """
                    This check highlights string literals in code which mentions the word `lint`. \
                    Blah blah blah.

                    Another paragraph here.
                    """, // no need to .trimIndent(), lint does that automatically
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SampleCodeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
