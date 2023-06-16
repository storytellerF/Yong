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
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.kotlin.KotlinUBlockExpression
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Collections
import java.util.LinkedList

/**
 * Sample detector showing how to analyze Kotlin/Java code. This example
 * flags all string literals in the code that contain the word "lint".
 */
@Suppress("UnstableApiUsage")
class KotlinUncaughtExceptionDetector : Detector(), UastScanner {
    private val root = RootNode(mutableListOf())
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
                        "${stackIndent()}visitMethod ${node.name} ${throws.size} ${(node.uastBody as? KotlinUBlockExpression)?.expressions?.size} ${
                            callStack.joinToString {
                                it.debug()
                            }
                        }"
                    )
                    val cache = methodCache[key]
                    val methodNode = cache ?: MethodNode(
                        node.name,
                        key,
                        throws,
                        mutableListOf(),
                        mutableListOf(),
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
                        is ThrowNode -> {
                            callStack.popLast()
                            val qualifiedName = node.containingClass?.qualifiedName!!
                            (callStack.last as MethodNode).throws.add(qualifiedName)
                            return true
                        }
                    }
                    //如果返回true，afterVisitMethod 也会跳过
                    if (throws.isNotEmpty() || cache != null) return true
                    callStack.addLast(methodNode)

                    return super.visitMethod(node)
                }

                private fun stackIndent(): String {
                    return indent(callStack.size - 1)
                }

                override fun afterVisitMethod(node: UMethod) {
                    super.afterVisitMethod(node)
                    val current = callStack.popLast() as MethodNode
                    context.client.log(Severity.IGNORE, null, "${stackIndent()}outMethod ${node.name}\n")
                    val throws = current.throwList()
                    if (throws.isEmpty()) {
                        val pre = callStack.last
                        if (pre is MethodContainer) {
                            pre.methods.remove(current)
                        }
                    } else {
                        current.replace(throws)
                        if (callStack.size == 2) {
                            context.report(
                                ISSUE, node, context.getLocation(node),
                                "uncaught exception ${throws.joinToString()}"
                            )
                        }
                    }

                }

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    context.client.log(Severity.IGNORE, null, "${stackIndent()}call ${node.methodName} $node")
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

                override fun visitThrowExpression(node: UThrowExpression): Boolean {
                    context.client.log(Severity.IGNORE, null, "${stackIndent()}visitThrow ${node.thrownExpression}")
                    callStack.addLast(ThrowNode())
                    return super.visitThrowExpression(node)
                }

                override fun afterVisitThrowExpression(node: UThrowExpression) {
                    context.client.log(Severity.IGNORE, null, "${stackIndent()}outThrow")
                    super.afterVisitThrowExpression(node)
                }

                override fun visitTryExpression(node: UTryExpression): Boolean {
                    val exceptions = node.safeExceptions().toMutableList()
                    context.client.log(Severity.IGNORE, null, "${stackIndent()}visitTry $node $exceptions")
                    val tryCatchSubstitution =
                        TryCatchSubstitution(exceptions, mutableListOf())
                    val current = callStack.last
                    if (current is MethodNode) {
                        current.tryBlock.add(tryCatchSubstitution)
                        callStack.addLast(tryCatchSubstitution)
                    }

                    return super.visitTryExpression(node)
                }

                override fun afterVisitTryExpression(node: UTryExpression) {
                    val current = callStack.popLast() as TryCatchSubstitution
                    context.client.log(Severity.IGNORE, null, "${stackIndent()}outTry $node")
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
                val isActivity = node.supers.any {
                    it.qualifiedName == "androidx.appcompat.app.AppCompatActivity"
                }
                if (isActivity) {
                    context.client.log(Severity.IGNORE, null, "visitClass ${node.qualifiedName}")
                    val activityNode =
                        ActivityNode(mutableListOf(), node.name!!)
                    root.activities.add(activityNode)
                    callStack.addLast(activityNode)
                    node.methods.filter {
                        it.findSuperMethods().isNotEmpty()
                    }.forEach {
                        it.accept(visitor)
                    }
                    callStack.removeLast()
                    printTree(root, context, 0)
                }

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
            id = "Yong",
            // Title -- shown in the IDE's preference dialog, as category headers in the
            // Analysis results window, etc
            briefDescription = "Lint kotlin uncaught exceptions",
            // Full explanation of the issue; you can use some markdown markup such as
            // `monospace`, *italic*, and **bold**.
            explanation = """
                    Uncaught exception may cause app crash.\
                    Should use `try-catch`.
                    """, // no need to .trimIndent(), lint does that automatically
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlinUncaughtExceptionDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
