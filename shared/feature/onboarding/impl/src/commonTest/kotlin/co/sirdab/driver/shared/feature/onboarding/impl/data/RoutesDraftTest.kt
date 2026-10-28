package co.sirdab.driver.shared.feature.onboarding.impl.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import co.sirdab.driver.shared.feature.onboarding.api.domain.City
import co.sirdab.driver.shared.feature.onboarding.api.domain.PreferredRoute
import co.sirdab.driver.shared.feature.onboarding.api.domain.RoutesDraft
import kotlin.test.Test

class RoutesDraftTest {

    private val riyadh = City("ruh", "Riyadh", "الرياض")
    private val jeddah = City("jed", "Jeddah", "جدة")

    @Test
    fun `a home city is all saving needs`() {
        assertThat(RoutesDraft().canSubmit).isFalse()
        assertThat(RoutesDraft(homeCity = riyadh).canSubmit).isTrue()
    }

    @Test
    fun `a route already on the list is not added twice, but its return trip is`() {
        val draft = RoutesDraft(homeCity = riyadh)
            .withRoute(PreferredRoute(riyadh, jeddah))
            // Same two cities, named differently: still the same route.
            .withRoute(PreferredRoute(riyadh.copy(nameAr = null), jeddah))
            .withRoute(PreferredRoute(jeddah, riyadh))

        assertThat(draft.routes).isEqualTo(listOf(PreferredRoute(riyadh, jeddah), PreferredRoute(jeddah, riyadh)))
    }

    @Test
    fun `the list stops at the contract's cap`() {
        val full = (1..RoutesDraft.MAX_PREFERRED_ROUTES).fold(RoutesDraft(homeCity = riyadh)) { draft, n ->
            draft.withRoute(PreferredRoute(riyadh, City("c$n", "City $n", null)))
        }

        assertThat(full.isFull).isTrue()
        // One more would be a 400 for the whole save, not just for the extra route.
        assertThat(full.withRoute(PreferredRoute(jeddah, riyadh)).routes).hasSize(RoutesDraft.MAX_PREFERRED_ROUTES)
    }
}
