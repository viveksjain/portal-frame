package com.portalhacks.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshStateTest {
    @Test fun changingCityInvalidatesTheOldRequest() {
        val state = WeatherRefreshState(WeatherSettings("London", fahrenheit = false))
        val oldRequest = state.beginRequest()

        assertTrue(state.update(WeatherSettings("Singapore", fahrenheit = false)))
        assertFalse(state.isCurrent(oldRequest))
        assertEquals("Singapore", state.beginRequest().settings.city)
    }

    @Test fun changingUnitInvalidatesTheOldRequest() {
        val state = WeatherRefreshState(WeatherSettings("London", fahrenheit = false))
        val oldRequest = state.beginRequest()

        assertTrue(state.update(WeatherSettings("London", fahrenheit = true)))
        assertFalse(state.isCurrent(oldRequest))
        assertTrue(state.beginRequest().settings.fahrenheit)
    }

    @Test fun clearingCityInvalidatesTheOldRequest() {
        val state = WeatherRefreshState(WeatherSettings("London", fahrenheit = false))
        val oldRequest = state.beginRequest()

        assertTrue(state.update(WeatherSettings("", fahrenheit = false)))
        assertFalse(state.isCurrent(oldRequest))
        assertEquals("", state.beginRequest().settings.city)
    }
}
