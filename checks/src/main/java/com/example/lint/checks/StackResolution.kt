package com.example.lint.checks

import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.kotlin.utils.addToStdlib.popLast
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.kotlin.KotlinUBlockExpression
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Collections
import java.util.LinkedList

class StackResolution(val context: JavaContext) {

    private val root = RootNode(mutableListOf())
    private val callStack = LinkedList<Node>(Collections.singleton(root))

    /**
     * 存储访问过的所有method
     */
    private val methodCache = mutableMapOf<MethodKey, MethodNode>()

    @Suppress("UnstableApiUsage")
    private val visitor = object : AbstractUastVisitor() {
        override fun visitMethod(node: UMethod): Boolean {
            val key = node.methodKey()
            if (callStack.any {
                    it is MethodNode && it.key == key
                }) {
                return true
            }
            val last = callStack.last
            if (last is ActivityNode && node.findSuperMethods().isEmpty()) return true
            val throws = node.throwExceptions().toMutableList()
            context.client.log(
                Severity.IGNORE, null,
                "${stackIndent()}visitMethod ${node.name} throws count: ${throws.size} expressions count: ${(node.uastBody as? KotlinUBlockExpression)?.expressions?.size} ${
                    callStack.joinToString {
                        it.debug()
                    }
                }"
            )
            val cache = methodCache[key]
            val methodNode = cache ?: MethodNode(
                node.name,
                key,
                throws
            )
            if (cache == null) {
                methodCache[key] = methodNode
            }
            (last as MethodContainer).methods.add(methodNode)

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
            if (node.isConstructorCall()) {
                val pre = callStack.last
                if (pre is ThrowNode) {
                    assert(node.isConstructorCall())
                    val qualifiedName = node.containingClass?.qualifiedName!!
                    current.throws.add(qualifiedName)
                }
            }
            context.client.log(
                Severity.IGNORE,
                null,
                "${stackIndent()}outMethod ${node.name}\n"
            )
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
                        KotlinUncaughtExceptionDetector.ISSUE, node, context.getLocation(node),
                        "uncaught exception ${throws.joinToString()}"
                    )
                }
            }

        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            context.client.log(
                Severity.IGNORE,
                null,
                "${stackIndent()}call ${node.methodName} $node"
            )
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
            context.client.log(
                Severity.IGNORE,
                null,
                "${stackIndent()}visitThrow ${node.thrownExpression}"
            )
            val current = callStack.last
            val throwNode = ThrowNode()
            (current as MethodNode).throwNodes.add(throwNode)
            callStack.addLast(throwNode)
            return super.visitThrowExpression(node)
        }

        override fun afterVisitThrowExpression(node: UThrowExpression) {
            context.client.log(Severity.IGNORE, null, "${stackIndent()}outThrow")
            callStack.popLast()
            super.afterVisitThrowExpression(node)
        }

        override fun visitTryExpression(node: UTryExpression): Boolean {
            val exceptions = node.safeExceptions().toMutableList()
            context.client.log(
                Severity.IGNORE,
                null,
                "${stackIndent()}visitTry $node $exceptions"
            )
            val tryCatchSubstitution =
                TryCatchSubstitution(exceptions)
            val current = callStack.last
            (current as MethodNode).tryBlock.add(tryCatchSubstitution)
            callStack.addLast(tryCatchSubstitution)

            return super.visitTryExpression(node)
        }

        override fun afterVisitTryExpression(node: UTryExpression) {
            val current = callStack.popLast() as TryCatchSubstitution
            context.client.log(Severity.IGNORE, null, "${stackIndent()}outTry $node")
            val throws = current.methods.flatMap {
                it.throws
            }.toSet() - current.caught.toSet()
            if (throws.isEmpty()) {
                val pre = callStack.last
                if (pre is MethodNode) {
                    pre.tryBlock.remove(current)
                }
            }
            super.afterVisitTryExpression(node)
        }

        override fun visitClass(node: UClass): Boolean {
            context.client.log(Severity.IGNORE, null, "visitClass ${node.qualifiedName}")
            val activityNode = ActivityNode(mutableListOf(), node.name!!)
            root.activities.add(activityNode)
            callStack.addLast(activityNode)
            return super.visitClass(node)
        }

        override fun afterVisitClass(node: UClass) {
            super.afterVisitClass(node)
            callStack.removeLast()
            printTree(root, context, 0)
        }

    }

    fun start(node: UClass) {
        node.accept(visitor)
    }
}