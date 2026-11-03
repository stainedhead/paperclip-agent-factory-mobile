@file:OptIn(ExperimentalCoroutinesApi::class)

package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.DomainError
import com.stainedhead.paperclipmobile.domain.DomainResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RetryingReadTest {
    @Test
    fun `reads retry with backoff up to three times `() =
        runTest {
            var attempts = 0
            val result =
                retryingRead {
                    attempts += 1
                    DomainResult.Failure(
                        DomainError.Timeout,
                    )
                }

            attempts shouldBe 4
            (result is DomainResult.Failure) shouldBe true
            testScheduler.currentTime shouldBe 3_500
        }

    @Test
    fun `F-A-5 a read treats a lost connection like an unreachable server`() =
        runTest {
            var attempts = 0
            retryingRead {
                attempts += 1
                DomainResult.Failure(DomainError.ConnectionLost)
            }
            attempts shouldBe 4
        }

    @Test
    fun `a read that recovers stops retrying and a permanent failure is not retried`() =
        runTest {
            var attempts = 0
            val recovered =
                retryingRead {
                    attempts += 1
                    if (attempts < 3) {
                        DomainResult.Failure(
                            DomainError.Unreachable,
                        )
                    } else {
                        DomainResult
                            .Success(1)
                    }
                }
            recovered shouldBe
                DomainResult
                    .Success(1)
            attempts shouldBe 3

            attempts = 0
            retryingRead<Int> {
                attempts += 1
                DomainResult.Failure(
                    DomainError.Forbidden,
                )
            }
            attempts shouldBe 1
        }
}
