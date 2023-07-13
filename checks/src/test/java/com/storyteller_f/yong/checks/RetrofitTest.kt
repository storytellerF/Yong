package com.storyteller_f.yong.checks


import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class RetrofitTest {
    private val apiDefinition = kotlin(
        """
            package $retrofitPackage
            
            annotation class GET(val path: String)
        """.trimIndent()
    )

    @Test
    fun test() {
        val apiGet = kotlin(
            """
                package test.api
                import $retrofitPackage.GET
                interface API {
                    @GET("/repos/{owner}/{repo}/contributors")
                    fun contributors(@Path("owner") owner: String, @Path("repo") repo: String)
                }
            """.trimIndent()
        )
        val plain = kotlin(
            """
                lateinit var api: test.api.API
                fun main() {
                    api.contributors("hello", "world")
                }
            """.trimIndent()
        )
        lint().files(apiDefinition, apiGet, plain).allowMissingSdk()
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