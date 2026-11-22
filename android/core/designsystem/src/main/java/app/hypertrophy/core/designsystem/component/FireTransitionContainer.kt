@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.hypertrophy.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSharedContentState
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Composition local exposing the active [SharedTransitionScope] so child
 * composables (workout cards, session screens) can declare shared elements.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Composition local exposing the active [AnimatedContentScope]. */
val LocalAnimatedContentScope = compositionLocalOf<AnimatedContentScope?> { null }

/**
 * Shared-element morph wrapper. Hosts a [SharedTransitionLayout] and an
 * [AnimatedContent] keyed by navigation state so cards can morph into screens.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FireTransitionContainer(
    transitionKey: Any,
    content: @Composable () -> Unit,
) {
    SharedTransitionLayout {
        AnimatedContent(
            targetState = transitionKey,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "fire-transition",
        ) {
            val animatedScope = this
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalAnimatedContentScope provides animatedScope,
            ) {
                content()
            }
        }
    }
}

/** Applies a shared-element morph when the composable is inside [FireTransitionContainer]. */
@Composable
fun Modifier.sharedFireElement(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedContentScope.current
    return if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            this@sharedFireElement.sharedElement(
                rememberSharedContentState(key = key),
                animatedVisibilityScope = animatedScope,
            )
        }
    } else {
        this
    }
}
