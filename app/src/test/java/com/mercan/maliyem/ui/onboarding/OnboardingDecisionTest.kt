package com.mercan.maliyem.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingDecisionTest {
    @Test fun `fresh install shows intro`() {
        assertTrue(shouldShowOnboarding(completed = null, hasUserData = false))
    }

    @Test fun `existing user with data skips intro`() {
        assertFalse(shouldShowOnboarding(completed = null, hasUserData = true))
    }

    @Test fun `completed intro stays complete with an empty database`() {
        assertFalse(shouldShowOnboarding(completed = true, hasUserData = false))
    }

    @Test fun `unfinished intro is offered again`() {
        assertTrue(shouldShowOnboarding(completed = false, hasUserData = false))
    }
}
