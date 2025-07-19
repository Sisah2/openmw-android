/*
    Copyright (C) 2015, 2016 sandstranger
    Copyright (C) 2018, 2019 Ilya Zhuravlev

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/


package ui.controls

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.GestureDetector
import android.content.Context

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

import org.libsdl.app.SDLActivity

import java.lang.Math
import kotlin.math.atan2
import android.view.KeyEvent

import java.io.File

class ButtonTouchListener(private val keyCode: Int, private val needEmulateMouse: Boolean, private val togglable: Boolean) : OnTouchListener {

    private var toggled: Boolean = false
    private var currentBackground: Drawable? = null

    enum class Movement {
        KEY_DOWN,
        KEY_UP,
        MOUSE_DOWN,
        MOUSE_UP
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onTouchDown(v)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onTouchUp()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onTouchUp()
                return true
            }
        }
        return false
    }

    private fun onTouchDown(v: View) {
        if (!needEmulateMouse) {
            if (togglable) {
                if (!toggled) {
                    val gradientBackground = v.getBackground() as GradientDrawable
                    currentBackground = v.getBackground()
                    if(gradientBackground != null) {
                        val backgroundRadius = gradientBackground.getCornerRadius()
                        val shape = GradientDrawable()
                        shape.setColor(Color.DKGRAY)
                        shape.setCornerRadius(backgroundRadius)
                        v.setBackground(shape)
                    }

                    eventMovement(Movement.KEY_DOWN)
                    toggled = true
                }
                else {
                    v.setBackground(currentBackground)

                    eventMovement(Movement.KEY_UP)
                    toggled = false
                }
            }
            else
                eventMovement(Movement.KEY_DOWN)
        } else
            eventMovement(Movement.MOUSE_DOWN)
    }

    private fun onTouchUp() {
        if (!needEmulateMouse) {
            if (!togglable)
                eventMovement(Movement.KEY_UP)
        } else {
            eventMovement(Movement.MOUSE_UP)
        }
    }

    private fun eventMovement(event: Movement) {
        when (event) {
            Movement.KEY_DOWN -> SDLActivity.onNativeKeyDown(keyCode)
            Movement.KEY_UP -> SDLActivity.onNativeKeyUp(keyCode)
            Movement.MOUSE_DOWN -> SDLActivity.sendMouseButton(1, keyCode)
            Movement.MOUSE_UP -> SDLActivity.sendMouseButton(0, keyCode)
        }
    }
}

class LongPressGestureDetectorListener() : GestureDetector.SimpleOnGestureListener() {
    override fun onLongPress(event: MotionEvent) {
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_TAB)
        super.onLongPress(event)
    }
}

class GestureDetectorListener(private val mouseScroll: Boolean, private val pressKeyCode: Int, private val leftKeyCode: Int, private val rightKeyCode: Int, private val upKeyCode: Int, private val downKeyCode: Int) : GestureDetector.SimpleOnGestureListener() {

    private var used: Boolean = false

    override fun onScroll(
        event1: MotionEvent,
        event2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (mouseScroll == true) {
            used = true
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_SCROLL, 0f, distanceY / 30f, false)
        }
        return super.onScroll(event1, event2, distanceX, distanceY)
    }

    override fun onFling(
        event1: MotionEvent,
        event2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (mouseScroll == false && used == false){
            if (velocityX < -20.0f && Math.abs(velocityX) > Math.abs(velocityY)) {
                SDLActivity.onNativeKeyDown(leftKeyCode)
                SDLActivity.onNativeKeyUp(leftKeyCode)
            }
            else if (velocityX > 20.0f && Math.abs(velocityX) > Math.abs(velocityY)) {
                SDLActivity.onNativeKeyDown(rightKeyCode)
                SDLActivity.onNativeKeyUp(rightKeyCode)
            }
            else if (velocityY < -20.0f && Math.abs(velocityY) > Math.abs(velocityX)) {
                SDLActivity.onNativeKeyDown(upKeyCode)
                SDLActivity.onNativeKeyUp(upKeyCode)
            }
            else if (velocityY > 20.0f && Math.abs(velocityY) > Math.abs(velocityX)) {
                SDLActivity.onNativeKeyDown(downKeyCode)
                SDLActivity.onNativeKeyUp(downKeyCode)
            }

            used = true
        }
        return super.onFling(event1, event2, velocityX, velocityY)
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        if (mouseScroll == false) {
            SDLActivity.onNativeKeyDown(pressKeyCode)
            SDLActivity.onNativeKeyUp(pressKeyCode)
        }
        else {
            if(used == false) {
                SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_TAB)
                Thread.sleep(50)
                SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_TAB)
                used = true
            }
        }
        return super.onSingleTapUp(event)
    }

    override fun onDown(event: MotionEvent): Boolean {
        used = false
        return super.onDown(event)
    }
}

class GestureButtonTouchListener(ctx: Context, private val mouseScroll: Boolean, pressKeyCode: Int, leftKeyCode: Int, rightKeyCode: Int, upKeyCode: Int, downKeyCode: Int) : OnTouchListener {
    var gestureDetector = GestureDetector(ctx, GestureDetectorListener(mouseScroll, pressKeyCode, leftKeyCode, rightKeyCode, upKeyCode, downKeyCode))

    var longPressGestureDetector = GestureDetector(ctx, LongPressGestureDetectorListener())

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        gestureDetector.setIsLongpressEnabled(false)
        gestureDetector.onTouchEvent(event)

        if (mouseScroll == true)
            longPressGestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_UP -> {
                if (mouseScroll == true)
                    SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_TAB)
                return true
            }
        }
        return true
    }
}

class QuickKeysButtonTouchListener(ctx: Context, private val buttons: ArrayList<OscHiddenButton>) : OnTouchListener {

    private var selectedItem: Int = 0

    private var origX: Float = 0.0f
    private var origY: Float = 0.0f
    private var currentX: Float = 0.0f
    private var currentY: Float = 0.0f
    private var pivot: Float = 0.0f

    val highlightedBackground: GradientDrawable = GradientDrawable()
    val background: GradientDrawable = GradientDrawable()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {

        highlightedBackground.setColor(Color.DKGRAY)
        highlightedBackground.setCornerRadius(100.0f)
        background.setColor(Color.GRAY)
        background.setCornerRadius(100.0f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (button in buttons)
                    button.view?.visibility = View.VISIBLE

                currentX = 0.0f
                currentY = 0.0f
                origX = v.x + v.getPivotX()
                origY = v.y + v.getPivotY()
                pivot = v.getPivotX()

                return true
            }
            MotionEvent.ACTION_UP -> {
                for (button in buttons)
                    button.view?.visibility = View.GONE

                if (Math.abs(event.x - pivot) > pivot || Math.abs(event.y - pivot) > pivot) {
                    SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_0 + selectedItem)
                    SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_0 + selectedItem)
                }
                else {
                    SDLActivity.onNativeKeyDown(131)
                    SDLActivity.onNativeKeyUp(131)
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val x = ((event.rawX - origX) + origX).toFloat()
                val y = ((event.rawY - origY) + origY).toFloat()
                currentX = x
                currentY = y

                var angle = atan2(origX - x, origY - y)

                if (angle < 0.0f)
                    angle = Math.abs(Math.toDegrees(angle.toDouble())).toFloat()
                else
                    angle = (180.0f - Math.toDegrees(angle.toDouble()).toFloat() + 180.0f)

                angle = (angle - 18.0f).toFloat()
                if (angle < 0.0) angle = (360.0f + angle).toFloat()

                selectedItem = Math.floor((angle / 36.0f).toDouble()).toInt() + 1

                if (selectedItem < 0) selectedItem = 9
                if (selectedItem > 9) selectedItem = 0

                for (button in buttons) 
                    button.view?.setBackground(background)

                val button = buttons[selectedItem]
                if (Math.abs(event.x - pivot) > pivot || Math.abs(event.y - pivot) > pivot)
                    button.view?.setBackground(highlightedBackground)
            }
        }
        return true
    }
}


