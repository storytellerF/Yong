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

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test
import java.io.File

@Suppress("UnstableApiUsage")
class KotlinUncaughtExceptionDetectorTest {
    @Test
    fun testBasic() {
        val home = System.getProperty("user.home")
        val sdkHome = File(home, "Library/Android/sdk")
        lint().files(
            kotlin(
                """
                    package test.pkg
                    
                    class MainActivity : AppCompatActivity() {
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
        ).sdkHome(sdkHome)
            .issues(KotlinUncaughtExceptionDetector.ISSUE)
            .run()
            .expect(
                "No warnings."
            )
    }
}
