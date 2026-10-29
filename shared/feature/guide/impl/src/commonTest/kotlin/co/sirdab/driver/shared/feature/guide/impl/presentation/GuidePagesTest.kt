package co.sirdab.driver.shared.feature.guide.impl.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test

class GuidePagesTest {

    @Test
    fun `a driver who bids is shown every page`() {
        assertThat(guidePages(canBid = true)).isEqualTo(GuidePage.entries)
    }

    @Test
    fun `a driver whose company bids for them is shown nothing about the board`() {
        assertThat(guidePages(canBid = false)).containsExactly(
            GuidePage.WELCOME,
            GuidePage.TRIPS,
            GuidePage.STOPS,
            GuidePage.PROOF,
            GuidePage.PROBLEM,
            GuidePage.PROFILE,
        )
    }
}
