@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.hypertrophy.core.designsystem.component

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Composition local exposing the active [SharedTransitionScope] so child
 * composables (workout cards, session screens) can declare shared elements.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Shared-element morph wrapper. Hosts a [SharedTransitionLayout] and exposes
 * its scope through [LocalSharedTransitionScope] so navigation from a workout
 * card into an active session can morph the card bounds.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FireTransitionContainer(
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            content()
        }
    }
}
