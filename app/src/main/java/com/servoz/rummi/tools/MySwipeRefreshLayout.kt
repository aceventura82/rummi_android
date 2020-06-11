package com.servoz.rummi.tools

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs


class MySwipeRefreshLayout : SwipeRefreshLayout {


    private var mTouchSlop = 0
    private var mPrevX = 0f

    constructor(context: Context?) : super(context!!) {
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }


    @SuppressLint("Recycle")
    override fun onInterceptTouchEvent(event:MotionEvent): Boolean{
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                        mPrevX = MotionEvent.obtain(event).x
                    }
            MotionEvent.ACTION_MOVE -> {
                val eventX = event.x
                val xDiff = abs(eventX - mPrevX)
                if (xDiff > mTouchSlop) {
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }


}