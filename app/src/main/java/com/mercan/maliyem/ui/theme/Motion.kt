package com.mercan.maliyem.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

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
