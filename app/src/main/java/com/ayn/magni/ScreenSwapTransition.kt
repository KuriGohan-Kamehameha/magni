package com.ayn.magni

internal data class ScreenSwapAnimation(
    val enterAnimRes: Int,
    val exitAnimRes: Int
)

internal object ScreenSwapTransition {
    fun forCurrentScreenOnTop(isCurrentScreenOnTop: Boolean): ScreenSwapAnimation {
        return if (isCurrentScreenOnTop) {
            // Top screen exits downward while bottom screen enters upward.
            ScreenSwapAnimation(
                enterAnimRes = R.anim.screen_swap_in_from_bottom,
                exitAnimRes = R.anim.screen_swap_out_to_bottom
            )
        } else {
            // Bottom screen exits upward while top screen enters downward.
            ScreenSwapAnimation(
                enterAnimRes = R.anim.screen_swap_in_from_top,
                exitAnimRes = R.anim.screen_swap_out_to_top
            )
        }
    }
}
