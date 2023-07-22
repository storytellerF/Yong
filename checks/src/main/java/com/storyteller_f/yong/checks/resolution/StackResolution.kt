package com.storyteller_f.yong.checks.resolution

import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.getReceiver
import com.storyteller_f.yong.checks.ContextNode
import com.storyteller_f.yong.checks.EntranceNode
import com.storyteller_f.yong.checks.KotlinUncaughtExceptionDetector
import com.storyteller_f.yong.checks.MethodContainer
import com.storyteller_f.yong.checks.MethodKey
import com.storyteller_f.yong.checks.MethodNode
import com.storyteller_f.yong.checks.Node
import com.storyteller_f.yong.checks.RootNode
import com.storyteller_f.yong.checks.ThrowNode
import com.storyteller_f.yong.checks.Throwable
import com.storyteller_f.yong.checks.ThrowableContainer
import com.storyteller_f.yong.checks.ThrowableDefinition
import com.storyteller_f.yong.checks.TryCatchSubstitution
import com.storyteller_f.yong.checks.indent
import com.storyteller_f.yong.checks.isLogicalContext
import com.storyteller_f.yong.checks.isMainMethod
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
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Collections
import java.util.LinkedList

class StackResolution(val context: JavaContext) {

    private val root = RootNode()
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
            val expressionCount = (node.uastBody as? KotlinUBlockExpression)?.expressions?.size ?: 0
            log(
                "${stackIndent()}visitMethod ${node.name} throwsCount: ${throws.size} expressionsCount: $expressionCount ${node.isConstructor} ${node.getReceiver()} ${node.containingClass?.name} ${
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
            if (last is ContextNode && node.findSuperMethods().isNotEmpty() ||
                last is EntranceNode && node.isMainMethod() ||
                last !is ContextNode && last !is EntranceNode
            )
                (last as MethodContainer).methods.add(methodNode)

            //如果返回true，afterVisitMethod 也会跳过
            //没有抛出异常或者曾经遍历过
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
            log(
                "${stackIndent()}outMethod ${node.name} ${throws.size}"
            )
            val pre = callStack.last
            if (((pre as? MethodContainer)?.methods?.size ?: 0) > 1) {
                log("")
            }
            when {
                throws.isEmpty() -> (pre as MethodContainer).methods.remove(current)
                pre is ContextNode || pre is EntranceNode -> {
                    context.report(
                        KotlinUncaughtExceptionDetector.ISSUE, node, context.getLocation(node),
                        "uncaught exception ${
                            throws.joinToString { it.name }
                        }"
                    )
                }
            }

        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            log(
                "${stackIndent()}call ${node.methodName} ${node.isConstructorCall()} $node"
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
            log(
                "${stackIndent()}visitThrow ${node.thrownExpression}"
            )
            val current = callStack.last
            val throwNode = ThrowNode()
            (current as ThrowableContainer).throwNodes.add(throwNode)
            callStack.addLast(throwNode)
            return super.visitThrowExpression(node)
        }

        override fun afterVisitThrowExpression(node: UThrowExpression) {
            val current = callStack.popLast() as ThrowNode
            log(
                "${stackIndent()}outThrow ${current.throwList()}"
            )
            super.afterVisitThrowExpression(node)
        }

        override fun visitTryExpression(node: UTryExpression): Boolean {
            val exceptions = node.safeExceptions().toMutableList()
            log(
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
            log("${stackIndent()}outTry $node")
            val throws = current.throwList()
            if (throws.isEmpty()) {
                (callStack.last as MethodNode).tryBlock.remove(current)
            }
            super.afterVisitTryExpression(node)
        }

        override fun visitClass(node: UClass): Boolean {
            log("visitClass ${node.qualifiedName}")
            val name = node.name!!
            if (node.isLogicalContext()) {
                val contextNode = ContextNode(mutableListOf(), name)
                root.contextNodes.add(contextNode)
                callStack.addLast(contextNode)
            } else {
                val entranceNode = EntranceNode(mutableListOf(), name)
                root.entranceNodes.add(entranceNode)
                callStack.addLast(entranceNode)
            }
            return super.visitClass(node)
        }

        override fun afterVisitClass(node: UClass) {
            super.afterVisitClass(node)
            val last = callStack.removeLast() as Throwable
            val throws = last.throwList()
            log("outClass ${node.qualifiedName} ${throws.size}")
            if (throws.isEmpty()) {
                if (last is ContextNode) {
                    root.contextNodes.remove(last)
                } else if (last is EntranceNode) {
                    root.entranceNodes.remove(last)
                }
            }
            val loops = callStack.filter {
                it is ContextNode || it is EntranceNode
            }
            if (loops.isEmpty() && !root.isEmpty()) {
                printTree(root, context, 0)
            }

        }

    }

    private fun log(format: String) {
//        context.client.log(Severity.IGNORE, null, format)
    }

    fun start(node: UClass) {
        node.accept(visitor)
    }
}