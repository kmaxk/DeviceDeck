package snapshot

import kotlinx.coroutines.CancellationException

/** Like [runCatching], but a cancelled coroutine stays cancelled instead of turning into a failed result. */
inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
