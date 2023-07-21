package com.storyteller_f.yong.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class CoroutineTest {

    @Test
    fun test() {
        val requestFile = kotlin(
            """
            import java.io.IOException
            class MainScope {
                fun launch(block: () -> Unit) {
                    
                }
            }
            @Throws(IOException)
            fun test(){
                
            }
            fun main() {
                MainScope().launch {
                    test()
                    throw IOException()
                }
            }
            
        """.trimIndent()
        )
        lint().files(
            KotlinUncaughtExceptionDetectorTest.throwableFile,
            KotlinUncaughtExceptionDetectorTest.ioExceptionFile,
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
}