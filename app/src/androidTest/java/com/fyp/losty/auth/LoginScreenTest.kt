package com.fyp.losty.auth

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fyp.losty.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginForm_acceptsInput_and_loginButtonEnabled() {
        // Wait for UI to settle
        composeTestRule.waitForIdle()

        // Enter credential and password
        composeTestRule.onNodeWithTag("credential_field").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("password_field").performTextInput("password123")

        // Verify login button is present and enabled
        composeTestRule.onNodeWithTag("login_button").assertIsDisplayed().assertIsEnabled()

        // Click login (this will call viewModel; in instrumentation this will run)
        composeTestRule.onNodeWithTag("login_button").performClick()
    }

    @Test
    fun googleButton_isClickable() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_button").assertIsDisplayed().performClick()
    }
}

