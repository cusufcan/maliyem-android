package com.mercan.maliyem.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Hareket süreleri (tasarim.md "Hareket ve geri bildirim", ADR-0016).
 * Sistemde animasyonlar kapalıysa tween'ler kendiliğinden anında biter.
 */
object Motion {
    /** Sekme, süzgeç, çip ve segment renkleri. */
    const val FAST = 150

    /** Ay ve Gider/Gelir geçişi, çubuk genişlikleri. */
    const val MEDIUM = 180

    /** Kaydet, sil, geri al; yeni satırın gelişi. */
    const val ENTER = 200

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST, easing = FastOutSlowInEasing)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = FastOutSlowInEasing)
    fun <T> enter(): FiniteAnimationSpec<T> = tween(ENTER, easing = FastOutSlowInEasing)

    fun itemPlacement(): FiniteAnimationSpec<IntOffset> = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    @Composable
    fun contentSize(): FiniteAnimationSpec<IntSize> = if (LocalReducedMotion.current) snap() else medium()

    @Composable
    fun expand(): EnterTransition = if (LocalReducedMotion.current) EnterTransition.None
        else expandVertically(medium()) + fadeIn(medium())

    @Composable
    fun collapse(): ExitTransition = if (LocalReducedMotion.current) ExitTransition.None
        else shrinkVertically(fast()) + fadeOut(fast())
}

/**
 * Yönlü geçiş: [forward] ise yeni içerik sağdan, değilse soldan hafifçe kayarak (genişliğin 1/12'si) ve solarak gelir.
 * Ay ve Gider/Gelir geçişlerinde kullanılır.
 */
fun <S> AnimatedContentTransitionScope<S>.slideFade(forward: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    return (slideInHorizontally(Motion.medium()) { sign * it / 12 } + fadeIn(Motion.medium()))
        .togetherWith(slideOutHorizontally(Motion.fast()) { -sign * it / 12 } + fadeOut(Motion.fast()))
        .using(SizeTransform(clip = false))
}

/** Sayı değişimi: artınca yeni değer aşağıdan, azalınca yukarıdan gelir. */
fun <S> AnimatedContentTransitionScope<S>.countSlide(up: Boolean): ContentTransform {
    val sign = if (up) 1 else -1
    return (slideInVertically(Motion.medium()) { sign * it / 2 } + fadeIn(Motion.medium()))
        .togetherWith(slideOutVertically(Motion.fast()) { -sign * it / 2 } + fadeOut(Motion.fast()))
        .using(SizeTransform(clip = false))
}

/** İçerik değişimi yönsüz: yalnız solma. */
fun <S> AnimatedContentTransitionScope<S>.crossFade(): ContentTransform =
    fadeIn(Motion.medium()).togetherWith(fadeOut(Motion.fast())).using(SizeTransform(clip = false))


internal val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val setting = Settings.Global.ANIMATOR_DURATION_SCALE
    fun disabled() = Settings.Global.getFloat(resolver, setting, 1f) == 0f
    val reducedMotion = remember(resolver) { mutableStateOf(disabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion.value = disabled()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(setting), false, observer)
        reducedMotion.value = disabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reducedMotion.value
}

@Composable
internal fun LazyItemScope.seyirAnimateItem(): Modifier =
    if (LocalReducedMotion.current) Modifier
    else Modifier.animateItem(
        fadeInSpec = Motion.medium(),
        placementSpec = Motion.itemPlacement(),
        fadeOutSpec = Motion.fast(),
    )
