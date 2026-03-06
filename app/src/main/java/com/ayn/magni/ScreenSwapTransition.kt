package com.ayn.magni

internal data class ScreenSwapAnimation(
    val enterAnimRes: Int,
    val exitAnimRes: Int
)

internal object ScreenSwapTransition {
    fun forZoomOnTop(zoomOnTop: Boolean): ScreenSwapAnimation {
        return if (zoomOnTop) {
            ScreenSwapAnimation(
                enterAnimRes = R.anim.screen_swap_in_from_bottom,
                exitAnimRes = R.anim.screen_swap_out_to_top
            )
        } else {
            ScreenSwapAnimation(
                enterAnimRes = R.anim.screen_swap_in_from_top,
                exitAnimRes = R.anim.screen_swap_out_to_bottom
            )
        }
    }
}
