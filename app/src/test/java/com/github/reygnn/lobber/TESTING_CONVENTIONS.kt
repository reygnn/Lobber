package com.github.reygnn.lobber

/**
 * TESTING CONVENTIONS FOR THIS PROJECT
 * =====================================
 *
 * 1. DISPATCHER
 * Every test class that involves coroutines declares:
 *
 * @get:Rule val mainDispatcherRule = MainDispatcherRule()
 *
 * Do NOT create a separate TestScope, StandardTestDispatcher, or
 * runBlockingTest. Use `runTest { }` from kotlinx-coroutines-test.
 * The single UnconfinedTestDispatcher is shared for everything.
 *
 * 2. VIRTUAL TIME
 * The game loop uses delay(PhysicsConstants.FRAME_DELAY_MS) = delay(16).
 * Advance exactly one frame:
 *
 * advanceTimeBy(PhysicsConstants.FRAME_DELAY_MS + 1)
 *
 * Advance N frames:
 *
 * advanceTimeBy((PhysicsConstants.FRAME_DELAY_MS + 1) * N)
 *
 * 3. MOCKK
 * - Use mockk<T>() for interface mocks.
 * - Use every { } / coEvery { } for stubbing.
 * - Use verify { } / coVerify { } for interaction verification.
 * - Prefer slot<> over capture() for argument inspection.
 *
 * Example:
 * val repo = mockk<HighScoreRepository>()
 * coEvery { repo.getHighScores() } returns flowOf(emptyMap())
 * coEvery { repo.updateHighScore(any(), any()) } just Runs
 *
 * 4. TURBINE & STATE CONFLATION
 * Use app.cash.turbine.test { } to assert on Flow emissions.
 * * CRITICAL: StateFlows summarize (conflate) fast updates. If a ViewModel 
 * updates multiple properties rapidly, awaitItem() might time out 
 * because intermediate states were skipped.
 * * - Use awaitItem() for the initial state or slow, discrete events.
 * - Use expectMostRecentItem() to jump to the final state after an action.
 *
 * Example:
 * viewModel.state.test {
 * awaitItem() // Consume initial state
 * viewModel.loadData()
 * val final = expectMostRecentItem() // Robust against fast loading/error toggles
 * assertThat(final.items).isNotEmpty()
 * }
 *
 * 5. PURE DOMAIN TESTS
 * PhysicsEngine and CollisionDetector have zero Android dependencies and
 * need no rule, no mockk, no coroutines — plain JUnit 4 is enough.
 */
@Suppress("unused")
private object TestingConventions
