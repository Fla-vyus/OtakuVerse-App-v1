package com.example

import com.example.ui.components.routeHasOwnTopInset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavInsetsTest {

    @Test
    fun testRouteHasOwnTopInset_returnsTrueForTabsAndSettings() {
        // As 5 rotas de abas
        assertTrue(routeHasOwnTopInset("home"))
        assertTrue(routeHasOwnTopInset("explore"))
        assertTrue(routeHasOwnTopInset("schedule"))
        assertTrue(routeHasOwnTopInset("library"))
        assertTrue(routeHasOwnTopInset("profile"))

        // Rota de configurações e image_crop
        assertTrue(routeHasOwnTopInset("settings"))
        assertTrue(routeHasOwnTopInset("image_crop"))
    }

    @Test
    fun testRouteHasOwnTopInset_returnsFalseForDetailsAndNull() {
        // Rota de detalhes de obra
        assertFalse(routeHasOwnTopInset("work_detail/{workId}"))
        assertFalse(routeHasOwnTopInset("work_detail/123"))

        // Rota nula
        assertFalse(routeHasOwnTopInset(null))

        // Rotas desconhecidas
        assertFalse(routeHasOwnTopInset("unknown_route"))
    }
}
