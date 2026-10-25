package cashi.payment.oways.shared.domain.result

import kotlin.test.Test
import kotlin.test.assertEquals

class OutcomeTest {

    private val success: Outcome<Int, String> = Outcome.Success(2)
    private val failure: Outcome<Int, String> = Outcome.Failure("boom")

    @Test
    fun map_transformsSuccess() {
        assertEquals(Outcome.Success(4), success.map { it * 2 })
    }

    @Test
    fun map_keepsFailure() {
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun flatMap_chainsSuccess() {
        assertEquals(Outcome.Success("2"), success.flatMap { Outcome.Success(it.toString()) })
        assertEquals(Outcome.Failure("nope"), success.flatMap { Outcome.Failure("nope") })
    }

    @Test
    fun flatMap_keepsFailure_withoutCallingTransform() {
        val result = failure.flatMap<Int, String, Int> { error("transform must not run") }

        assertEquals(failure, result)
    }
}