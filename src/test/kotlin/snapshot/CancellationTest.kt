package snapshot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CancellationTest {
    @Test
    fun cancellation_is_rethrown_not_swallowed() = runBlocking {
        assertFailsWith<CancellationException> { runCatchingNonCancellation<Int> { throw CancellationException("cancelled") } }
        Unit
    }

    @Test
    fun other_failures_become_a_failed_result() {
        val r = runCatchingNonCancellation<Int> { error("boom") }
        assertTrue(r.isFailure)
        assertEquals("boom", r.exceptionOrNull()?.message)
    }

    @Test
    fun success_is_passed_through() {
        assertEquals(7, runCatchingNonCancellation { 7 }.getOrNull())
    }
}
