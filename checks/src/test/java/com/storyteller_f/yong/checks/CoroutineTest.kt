package com.storyteller_f.yong.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class CoroutineTest {
    private val mainScopeFile = kotlin(
        """
        class MainScope {
            fun launch(block: () -> Unit) {
                
            }
        }
    """.trimIndent()
    )

    @Test
    fun test() {
        val requestFile = kotlin(
            """
            import java.io.IOException
            fun main() {
                MainScope().launch {
                    throw IOException()
                }
            }
            """.trimIndent()
        )
        lint().files(
            KotlinUncaughtExceptionTest.throwableFile,
            KotlinUncaughtExceptionTest.ioExceptionFile,
            mainScopeFile,
            requestFile
        ).allowMissingSdk()
            .issues(KotlinUncaughtExceptionDetector.ISSUE)
            .run()
            .expect(
                """
                    src/test.kt:2: Error: uncaught exception java.io.IOException [Yong]
                    fun main() {
                    ^
                    1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun testCatchException() {
        val requestFile = kotlin(
            """
            fun main() {
                MainScope().launch {
                    try {
                         throw IOException()
                    } catch (e: Exception) {
                         println(e)
                    }
                }
            }
            """.trimIndent()
        )
        lint().files(
            KotlinUncaughtExceptionTest.throwableFile,
            KotlinUncaughtExceptionTest.ioExceptionFile,
            mainScopeFile,
            requestFile
        ).allowMissingSdk()
            .issues(KotlinUncaughtExceptionDetector.ISSUE)
            .run()
            .expect("No warnings.")
    }
}