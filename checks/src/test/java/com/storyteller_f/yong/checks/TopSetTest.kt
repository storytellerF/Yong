package com.storyteller_f.yong.checks

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test
import java.io.File

class TopSetTest {

    @Test
    fun test() {
        val throwsFile = TestFiles.kotlin(File("../definition/src/main/java/com/storyteller_f/yong/definition/Throws.kt").readText())
        val apiGet = TestFiles.kotlin(
            """
                package test.api
                import com.storyteller_f.yong.definition.Throws
                
                @Throws(java.io.IOException)
                interface API {
                    fun contributors()
                }
            """.trimIndent()
        )
        val plain = TestFiles.kotlin(
            """
                lateinit var api: test.api.API
                fun main() {
                    api.contributors()
                }
            """.trimIndent()
        )
        TestLintTask.lint().files(
            throwsFile,
            KotlinUncaughtExceptionTest.throwableFile,
            KotlinUncaughtExceptionTest.ioExceptionFile,
            apiGet,
            plain
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
}