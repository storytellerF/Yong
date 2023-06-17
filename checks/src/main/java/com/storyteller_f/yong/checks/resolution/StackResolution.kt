package com.storyteller_f.yong.checks.resolution

import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Severity
import com.storyteller_f.yong.checks.ActivityNode
import com.storyteller_f.yong.checks.KotlinUncaughtExceptionDetector
import com.storyteller_f.yong.checks.MethodContainer
import com.storyteller_f.yong.checks.MethodKey
import com.storyteller_f.yong.checks.MethodNode
import com.storyteller_f.yong.checks.Node
import com.storyteller_f.yong.checks.RootNode
import com.storyteller_f.yong.checks.ThrowNode
import com.storyteller_f.yong.checks.ThrowableDefinition
import com.storyteller_f.yong.checks.TryCatchSubstitution
import com.storyteller_f.yong.checks.indent
import com.storyteller_f.yong.checks.methodKey
import com.storyteller_f.yong.checks.printTree
import com.storyteller_f.yong.checks.safeExceptions
import com.storyteller_f.yong.checks.throwExceptions
import org.jetbrains.kotlin.utils.addToStdlib.popLast
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.kotlin.KotlinUBlockExpression
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Collections
import java.util.LinkedList

class StackResolution(val context: JavaContext) {

    private val root = RootNode(mutableListOf())
    private val callStack = LinkedList<Node>(Collections.singleton(root))

    /**
     * 存储访问过的所有method
     */
    private val resolvedMethodCache = mutableMapOf<MethodKey, MethodNode>()

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
            /*
             * 判断是否是调用了Exception 构造函数
             */
            val constructorExceptions = if (last is ThrowNode) {
                listOf(
                    ThrowableDefinition.throwableDefinition(node.containingClass!!)
                )
            } else emptyList()

            val keywordExceptions = node.throwExceptions()

            /**
             * 通过注解或者关键字指定的异常
             */
            val throws = keywordExceptions + constructorExceptions
            context.client.log(
                Severity.IGNORE, null,
                "${stackIndent()}visitMethod ${node.name} throws count: ${throws.size} expressions count: ${(node.uastBody as? KotlinUBlockExpression)?.expressions?.size ?: 0} ${
                    callStack.joinToString {
                        it.debug()
                    }
                }"
            )
            val cache = resolvedMethodCache[key]
            val methodNode = cache ?: MethodNode(
                node.name,
                key,
                throws.toMutableList()
            )
            if (cache == null) {
                resolvedMethodCache[key] = methodNode
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
            val throws = current.throwList()
            context.client.log(
                Severity.IGNORE,
                null,
                "${stackIndent()}outMethod ${node.name} ${throws.size}"
            )
            if ((callStack.last as? MethodContainer)?.methods?.isNotEmpty() == true) {
                context.client.log(Severity.IGNORE, null, "")
            }
            if (throws.isEmpty()) {
                val pre = callStack.last
                (pre as MethodContainer).methods.remove(current)
            } else {
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
                        val methodNode = resolvedMethodCache[key]
                        if (methodNode != null) {
                            (current as MethodNode).methods.add(methodNode)
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
            callStack.popLast()
            context.client.log(Severity.IGNORE, null, "${stackIndent()}outThrow")
            super.afterVisitThrowExpression(node)
        }

        override fun visitTryExpression(node: UTryExpression): Boolean {
            val exceptions = node.safeExceptions().toMutableList()
            context.client.log(
                Severity.IGNORE,
                null,
                "${stackIndent()}visitTry $node ${exceptions.joinToString { it.name }}"
            )
            val tryCatchSubstitution = TryCatchSubstitution(exceptions)
            val current = callStack.last
            (current as MethodNode).tryBlock.add(tryCatchSubstitution)
            callStack.addLast(tryCatchSubstitution)

            return super.visitTryExpression(node)
        }

        override fun afterVisitTryExpression(node: UTryExpression) {
            val current = callStack.popLast() as TryCatchSubstitution
            context.client.log(Severity.IGNORE, null, "${stackIndent()}outTry $node")
            val throws = current.throwList()
            if (throws.isEmpty()) {
                (callStack.last as MethodNode).tryBlock.remove(current)
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
            printTree(ThrowableDefinition.rootDefinition, context, 0)
        }

    }

    fun start(node: UClass) {
        node.accept(visitor)
    }
}