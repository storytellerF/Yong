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

class KotlinUncaughtExceptionDetectorTest {
    private val throwableFile = kotlin(
        """
            package java.lang
            abstract class Throwable
            abstract class Exception : Throwable
        """.trimIndent()
    )
    private val ioExceptionFile = kotlin(
        """
            package java.io
            import java.lang.Exception
            abstract class IoException : Exception

        """.trimIndent()
    )
    private val contextFile = kotlin(
        """
            package android.content
            abstract class Context {
                fun onResume()
            }

        """.trimIndent()
    )
    private val testFile: TestFile = kotlin(
        """
                    package test.pkg
                    import java.io.IOException

                    class MainActivity : android.content.Context() {
                        override fun onResume() {
                            super.onResume()
                            hello()
                            middle()
                            throwException()
                        }
                        
                        @Throws(IOException::class)
                        fun throwException() = Unit

                        fun middle() = throwException()
                    
                        fun hello() = try {
                            middle()
                        } catch (_: IOException) { }
                        
                    }
                    """
    ).indented()

    @Test
    fun testBasic() {
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
                    src/test/pkg/MainActivity.kt:5: Error: uncaught exception java.io.IOException [Yong]
                        override fun onResume() {
                        ^
                    1 errors, 0 warnings
                """.trimIndent()
            )
    }
}
