package com.mercan.maliyem.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.mercan.maliyem.R
import com.mercan.maliyem.ui.components.SeyirBackdrop
import com.mercan.maliyem.ui.components.SeyirButton
import com.mercan.maliyem.ui.components.SeyirTextButton
import com.mercan.maliyem.ui.theme.LocalReducedMotion
import com.mercan.maliyem.ui.theme.Motion
import com.mercan.maliyem.ui.theme.Spacing
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal enum class IntroPage(val title: Int, val body: Int) {
    Welcome(R.string.intro_welcome_title, R.string.intro_welcome_body),
    QuickAdd(R.string.intro_quick_add_title, R.string.intro_quick_add_body),
    Breakdown(R.string.intro_breakdown_title, R.string.intro_breakdown_body),
    Goals(R.string.intro_goals_title, R.string.intro_goals_body),
}

@Composable
internal fun OnboardingScreen(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val pages = IntroPage.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val settledPage = pager.settledPage
    val isLast = settledPage == pages.lastIndex

    fun goTo(index: Int) {
        scope.launch {
            if (reducedMotion) pager.scrollToPage(index)
            else pager.animateScrollToPage(index, animationSpec = Motion.introPageScroll())
        }
    }

    BackHandler(enabled = settledPage > 0) {
        goTo(settledPage - 1)
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SeyirBackdrop(seed = 34, Modifier.matchParentSize())
        Column(
            Modifier.align(Alignment.Center).safeDrawingPadding().widthIn(max = 480.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).heightIn(min = 48.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (!isLast) {
                    SeyirTextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.intro_skip))
                    }
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) { index ->
                IntroPageContent(
                    page = pages[index],
                    pager = pager,
                    index = index,
                )
            }

            PageDots(settledPage, pages.size)
            SeyirButton(
                text = stringResource(if (isLast) R.string.intro_start else R.string.intro_next),
                onClick = {
                    if (!pager.isScrollInProgress) {
                        if (isLast) onExit() else goTo(settledPage + 1)
                    }
                },
                modifier = Modifier.fillMaxWidth()
                    .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg)
                    .heightIn(min = 52.dp),
            )
        }
    }
}

@Composable
private fun PageDots(selected: Int, count: Int) {
    val reducedMotion = LocalReducedMotion.current
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.intro_page_number, selected + 1, count)
    Row(
        Modifier.height(Spacing.xxl).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == selected
            val width by animateDpAsState(
                targetValue = if (active) Spacing.xl else Spacing.sm,
                animationSpec = if (reducedMotion) snap() else Motion.medium(),
                label = "Intro dot width",
            )
            Box(
                Modifier.layout { measurable, constraints ->
                    val widthPx = constraints.constrainWidth(width.roundToPx())
                    val dot = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
                    layout(dot.width, dot.height) { dot.placeRelative(0, 0) }
                }.height(Spacing.sm).background(
                    if (active) colors.primary else colors.outline,
                    CircleShape,
                ),
            )
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage, pager: PagerState, index: Int) {
    val reducedMotion = LocalReducedMotion.current
    val art = remember { Animatable(0f) }
    val copy = remember { Animatable(0f) }
    val float = remember { Animatable(0f) }

    LaunchedEffect(pager, index, reducedMotion) {
        if (reducedMotion) {
            art.snapTo(1f)
            copy.snapTo(1f)
            float.snapTo(0f)
            return@LaunchedEffect
        }

        // Pager pages occupy the full viewport. Reset only after a page has left it.
        snapshotFlow { abs(pager.getOffsetDistanceInPages(index)) < 1f }
            .collectLatest { visible ->
                if (visible) {
                    coroutineScope {
                        launch { art.animateTo(1f, Motion.introIllustration()) }
                        launch { copy.animateTo(1f, Motion.introText()) }
                        if (page == IntroPage.Welcome) {
                            launch { float.animateTo(1f, Motion.introFloat()) }
                        }
                    }
                } else {
                    art.snapTo(0f)
                    copy.snapTo(0f)
                    float.snapTo(0f)
                }
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val artSize = minOf(maxWidth - Spacing.xxl, (viewportHeight * .58f).coerceIn(112.dp, 288.dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                .semantics { if (index != pager.settledPage) hideFromAccessibility() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        ) {
            IntroIllustration(
                page, artSize,
                progress = { if (reducedMotion) 1f else art.value },
                phase = { if (reducedMotion) 0f else float.value },
                modifier = Modifier.graphicsLayer {
                    if (!reducedMotion) {
                        val offset = pager.getOffsetDistanceInPages(index).coerceIn(-1f, 1f)
                        translationX = offset * Spacing.xl.toPx()
                        val scale = .96f + .04f * art.value
                        scaleX = scale
                        scaleY = scale
                    }
                },
            )
            Column(
                Modifier.widthIn(max = 352.dp).graphicsLayer {
                    if (!reducedMotion) {
                        alpha = copy.value
                        translationY = (1f - copy.value) * Spacing.md.toPx()
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    stringResource(page.title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(page.body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
