package app.brokoli5191.picket.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.brokoli5191.picket.R

/*
 * All motion goes through the theme's MotionScheme: Expressive springs (a light bounce) by default,
 * the standard scheme for "Reduced" and instant changes for "Off". Movement uses the default
 * spatial spring; only collapsing uses the fast one, so closing feels immediate. Fades use the
 * effects springs, which never overshoot. Nothing moves further than it needs to.
 */

/**
 * Item appearance, removal and reordering in lazy lists. A LazyItemScope extension rather than a
 * Modifier one, because animateItem only exists in that scope.
 */
@SuppressLint("ModifierFactoryExtensionFunction")
@Composable
fun LazyItemScope.itemMotion(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

/** Content below a row that opens and closes, such as a group's processes. */
@Composable
fun ExpandableContent(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec<IntSize>()),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec<IntSize>()),
    ) { content() }
}

/**
 * Expand/collapse chevron that turns instead of swapping icons. [decorative] drops the spoken label,
 * for a chevron inside a button whose text already says what it does.
 */
@Composable
fun ExpandIcon(
    expanded: Boolean,
    decorative: Boolean = false,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "expand",
    )
    Icon(
        painterResource(R.drawable.ic_expand_more),
        contentDescription =
            if (decorative) {
                null
            } else {
                stringResource(
                    if (expanded) R.string.action_collapse else R.string.action_expand,
                )
            },
        modifier = Modifier.rotate(rotation),
    )
}

/** A small element such as a status badge: it pops in on the spatial spring and fades out quickly. */
@Composable
fun PopContent(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.8f),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f),
    ) { content() }
}

/** Opening a screen: it slides in a quarter of the width on a spring while the old one steps back. */
fun <S> AnimatedContentTransitionScope<S>.forwardTransition(motion: MotionScheme): ContentTransform =
    (
        slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) {
            it / 4
        } + fadeIn(motion.defaultEffectsSpec())
    ) togetherWith
        (slideOutHorizontally(motion.defaultSpatialSpec<IntOffset>()) { -it / 8 } + fadeOut(motion.fastEffectsSpec()))

/** Going back, the reverse of [forwardTransition]; also drives predictive back. */
fun <S> AnimatedContentTransitionScope<S>.backTransition(motion: MotionScheme): ContentTransform =
    (
        slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) {
            -it / 8
        } + fadeIn(motion.defaultEffectsSpec())
    ) togetherWith
        (slideOutHorizontally(motion.defaultSpatialSpec<IntOffset>()) { it / 4 } + fadeOut(motion.fastEffectsSpec()))

/** Switching tabs: no direction, a fade-through with a slight grow on the spring. */
fun <S> AnimatedContentTransitionScope<S>.tabTransition(motion: MotionScheme): ContentTransform =
    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)) togetherWith
        fadeOut(motion.fastEffectsSpec())
