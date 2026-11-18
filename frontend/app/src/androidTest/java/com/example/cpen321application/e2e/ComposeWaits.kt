package com.example.cpen321application.e2e

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode

// how long the E2E tests wait for the server's answer or the phone's location
const val LOAD_TIMEOUT_MILLIS = 15_000L

// the screen's scrolling list: the only one not inside another, like the row of sport chips is
val screenList: SemanticsMatcher = hasScrollToNodeAction() and !hasAnyAncestor(hasScrollToNodeAction())

// waits until a node matching matcher is on the screen
fun ComposeTestRule.waitForNode(matcher: SemanticsMatcher, timeoutMillis: Long = LOAD_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = LOAD_TIMEOUT_MILLIS) {
    waitForNode(hasText(text), timeoutMillis)
}

// clicks a tab in the bottom bar, once the app has opened its main screen
fun ComposeTestRule.openTab(label: String) {
    val tab = hasText(label) and hasClickAction()
    waitForNode(tab)
    onNode(tab).performClick()
}

// scrolls the screen's list until a node matching matcher is shown
fun ComposeTestRule.scrollTo(matcher: SemanticsMatcher) {
    onNode(screenList).performScrollToNode(matcher)
}
