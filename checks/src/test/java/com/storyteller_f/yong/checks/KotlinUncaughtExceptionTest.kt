/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.storyteller_f.yong.checks

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test
import java.io.File

class KotlinUncaughtExceptionTest {
    companion object {
        val throwableFile: TestFile = kotlin(
            """
            package java.lang
            abstract class Throwable
            abstract class Exception : Throwable
        """.trimIndent()
        )
        val ioExceptionFile: TestFile = kotlin(
            """
            package java.io
            import java.lang.Exception
            abstract class IoException : Exception

        """.trimIndent()
        )
    }

    private val contextFile = kotlin(
        """
            package android.content
            abstract class Context {
                fun onResume()
            }

        """.trimIndent()
    )


    @Test
    fun testBasic() {
        val testFile: TestFile =
            kotlin(File("src/test/resources/Basic1").readText().replace("\r", "")).indented()
        lint().files(
            throwableFile,
            ioExceptionFile,
            contextFile,
            testFile
        ).allowMissingSdk()
            .issues(KotlinUncaughtExceptionDetector.ISSUE)
            .run()
            .expect(
                """
                    src/test/pkg/MainActivity.kt:9: Error: uncaught exception java.io.IOException [Yong]
                        override fun onResume() {
                        ^
                    1 errors, 0 warnings
                """.trimIndent()
            )
    }

    @Test
    fun testAbstract() {
        val content = kotlin(File("src/test/resources/Abstract").readText().replace("\r", ""))
        lint().files(
            throwableFile,
            ioExceptionFile,
            content
        ).allowMissingSdk()
            .issues(KotlinUncaughtExceptionDetector.ISSUE)
            .run()
            .expect(
                """
                    src/test/hello/Long.kt:26: Error: uncaught exception java.io.IOException [Yong]
                    fun main() {
                    ^
                    1 errors, 0 warnings
                """.trimIndent()
            )
    }
}
