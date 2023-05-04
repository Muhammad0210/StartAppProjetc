package com.fyspring.stepcounter.utils

import android.content.Context


/**
 * Created by fySpring
 * Date: 2020/4/21
 * To do:
 */
class ScreenUtil {
    companion object {
        fun getScreenWidth(mContext: Context): Int {

            val displayMetrics = mContext.resources.displayMetrics
            val widthPixels = displayMetrics.widthPixels
            val heightPixels = displayMetrics.heightPixels
            val density = displayMetrics.density

            return widthPixels
        }
    }
}