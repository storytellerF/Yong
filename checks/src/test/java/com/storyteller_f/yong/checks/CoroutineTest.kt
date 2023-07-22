package com.storyteller_f.yong.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class CoroutineTest {
    private val mainScopeFile = kotlin(
        """
        import java.io.IOException
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
            fun main() {
                MainScope().launch {
                    throw IOException()
                }
            }
            """.trimIndent()
        )
        lint().files(
            KotlinUncaughtExceptionDetectorTest.throwableFile,
            KotlinUncaughtExceptionDetectorTest.ioExceptionFile,
            mainScopeFile,
            requestFile
        ).allowMissingSdk()
            .issues(KotlinUncaughtExceptionDetector.ISSUE)
            .run()
            .expect(
                """
                    src/MainScope.kt:11: Error: uncaught exception java.io.IOException [Yong]
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
            KotlinUncaughtExceptionDetectorTest.throwableFile,
            KotlinUncaughtExceptionDetectorTest.ioExceptionFile,
            mainScopeFile,
            requestFile
        ).allowMissingSdk()
            .issues(KotlinUncaughtExceptionDetector.ISSUE)
            .run()
            .expect("No warnings.")
    }
}