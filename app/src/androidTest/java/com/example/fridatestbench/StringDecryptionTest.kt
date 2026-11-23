package com.example.fridatestbench

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringDecryptionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun displaysAllDecryptedStrings() {
        val stringFog = listOf(
            "Morning sunlight spills across the kitchen floor before breakfast begins.",
            "Small boats drift quietly past the lighthouse after the storm.",
            "Fresh snow covers every garden path beneath the pale moon.",
            "Children collect smooth stones beside the river during summer holidays.",
            "Warm bread fills the bakery with comfort on rainy afternoons.",
        )
        val aes256 = listOf(
            "Silver rain taps softly against the windows before dawn arrives.",
            "A patient gardener waters young seedlings beneath the wooden trellis.",
            "Quiet footsteps echo through empty halls after the library closes.",
            "Bright lanterns lead travelers across the bridge on winter evenings.",
            "Fresh apples tumble from woven baskets onto the market stall.",
        )
        val customXor = listOf(
            "Golden leaves gather beside the fence after a windy afternoon.",
            "Two musicians practice cheerful songs together under the station clock.",
            "A blue kite rises above the field during weekend picnics.",
            "The café serves fresh pastries beside the sunny front steps.",
            "Little waves wash smooth shells onto the beach each morning.",
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(customXor.last()).fetchSemanticsNodes().isNotEmpty()
        }

        val expectedText = listOf("String decryption", "StringFog (XOR)") + stringFog +
            "AES-256 (GCM)" + aes256 + "Custom XOR" + customXor
        for (text in expectedText) {
            composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }
}
