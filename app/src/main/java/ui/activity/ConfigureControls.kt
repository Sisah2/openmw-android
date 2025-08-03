/*
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

package ui.activity

import com.libopenmw.openmw.R

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

import ui.controls.Osc
import ui.controls.OscElement
import ui.controls.OscHiddenButton
import ui.controls.OscImageButton
import ui.controls.OscVisibility
import ui.controls.VIRTUAL_SCREEN_HEIGHT
import ui.controls.VIRTUAL_SCREEN_WIDTH
import utils.Utils.hideAndroidControls

import android.app.AlertDialog
import android.widget.TextView
import android.widget.ImageView
import android.widget.EditText
import android.widget.CheckBox
import android.widget.Button
import android.widget.SeekBar
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ScrollView
import android.widget.Toast
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.graphics.BitmapFactory

import java.io.File
import constants.Constants

import android.content.*
import android.net.Uri

import android.preference.PreferenceManager
import org.jetbrains.anko.defaultSharedPreferences

class ConfigureCallback(activity: Activity) : View.OnTouchListener {

    var currentView: View? = null
    var currentBackground: Drawable? = null

    private var layout: RelativeLayout = activity.findViewById(R.id.controlsContainer)
    private var origX: Float = 0.0f
    private var origY: Float = 0.0f
    private var startX: Float = 0.0f
    private var startY: Float = 0.0f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentView?.setBackground(currentBackground)

                val gradientBackground = v.getBackground() as? GradientDrawable
                currentBackground = v.getBackground()
                currentView = v

                if(gradientBackground != null) {
                    val backgroundRadius = gradientBackground.getCornerRadius()
                    val shape = GradientDrawable()
                    shape.setColor(Color.RED)
                    shape.setCornerRadius(backgroundRadius)
                    v.setBackground(shape)
                }
                else
                    v.setBackgroundColor(Color.RED)

                origX = v.x
                origY = v.y
                startX = event.rawX
                startY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> if (currentView != null) {
                val view = currentView!!
                val x = ((event.rawX - startX) + origX).toInt()
                val y = ((event.rawY - startY) + origY).toInt()

                val el = view.tag as OscElement
                el.changePosition(x * VIRTUAL_SCREEN_WIDTH / layout.width, y * VIRTUAL_SCREEN_HEIGHT / layout.height)
                el.updateView()
            }
        }

        return true
    }
}

class ConfigureControls : Activity() {

    private var callback: ConfigureCallback? = null
    private var osc = Osc()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.configure_controls)

        val cb = ConfigureCallback(this)
        callback = cb

        val container: RelativeLayout = findViewById(R.id.controlsContainer)
        osc.placeConfigurableElements(container, cb)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            hideAndroidControls(this)
        }
    }

    private fun changeOpacity(delta: Float) {
        val view = callback?.currentView ?: return
        val el =  view.tag as OscElement
        el.changeOpacity(delta)
        el.updateView()
    }

    private fun changeSize(delta: Int) {
        val view = callback?.currentView ?: return
        val el = view.tag as OscElement
        el.changeSize(delta)
        el.updateView()
    }

    fun clickRemoveButton(v: View) {
        var buttonList = arrayListOf<String>()

        val view = callback?.currentView ?: return
        val el = view.tag as OscElement

if ((el.uniqueId.length > 1 && el.uniqueId.toIntOrNull() == null)/* || (el.uniqueId.length > 1 && el.uniqueId.toIntOrNull() == null)*/) {
Toast.makeText(this, "Build-in keys cant be removed.", Toast.LENGTH_SHORT).show()
return
}
        with (PreferenceManager.getDefaultSharedPreferences(this).edit()) {
            remove("osc:" + el.uniqueId + ":opacity")
            remove("osc:" + el.uniqueId + ":size")
            remove("osc:" + el.uniqueId + ":x")
            remove("osc:" + el.uniqueId + ":y")

            commit()
        }

        File(Constants.USER_FILE_STORAGE + "/launcher/controls.cfg").readLines().forEach {
            if (!it.startsWith(el.uniqueId))
                buttonList.add(it + "\n")
        }

        var output: String = ""

        buttonList.forEach { output += it}

        File(Constants.USER_FILE_STORAGE + "/launcher/controls.cfg").writeText(output)

        view.visibility = View.GONE
    }

    fun clickAddButton(v: View) {

        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Add new button")

        val containerLayout: RelativeLayout = this.findViewById(R.id.controlsContainer)
        val resScale = containerLayout.height.toFloat()/1440.0f

        val scrollView = ScrollView(this)
        var scrollParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scrollView.layoutParams = scrollParams

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val keyLayout = LinearLayout(this)
        keyLayout.orientation = LinearLayout.HORIZONTAL
        var keyParams = LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT)
        keyParams.weight = 5.0f

        val keyText = TextView(this)
        keyText.setText("Key or Keycode")
        layout.addView(keyText)

        val keyBox = EditText(this)
        keyBox.setSingleLine()
        keyBox.setHint("Key or Keycode")
        keyBox.setImeOptions(EditorInfo.IME_ACTION_DONE)
        keyBox.setLayoutParams(keyParams)
        keyLayout.addView(keyBox)

        val keyButton = Button(this)
        keyButton.setText("Keycodes list")
        keyButton.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {

                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.temblast.com/ref/akeyscode.htm"))
                startActivity(browserIntent)
            }
            true
        }
        keyLayout.addView(keyButton)

        layout.addView(keyLayout)

        val nameText = TextView(this)
        nameText.setText("Button text or icon name")
        layout.addView(nameText)

        val nameLayout = LinearLayout(this)
        nameLayout.orientation = LinearLayout.HORIZONTAL
        var nameParams = LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT)
        nameParams.weight = 5.0f

        val nameBox = EditText(this)
        nameBox.setSingleLine()
        nameBox.setHint("Button text or icon name")
        nameBox.setLayoutParams(nameParams)
        nameLayout.addView(nameBox)

        val nameButton = Button(this)
        nameButton.setText("select")
        nameButton.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {

                val pickerDialog = AlertDialog.Builder(this)

                pickerDialog.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }

                val picker = pickerDialog.create()

                picker.setTitle("Select icon")
                val pickerScrollView = ScrollView(this)
                var pickerScrollParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                pickerScrollView.layoutParams = scrollParams
                val pickerLayout = LinearLayout(this)
                pickerLayout.orientation = LinearLayout.VERTICAL

                File(Constants.USER_FILE_STORAGE + "/launcher/icons/").walkTopDown().forEach {

                    val iconName = it.toString().substringAfterLast("/")
                    if (iconName.contains(".png") || iconName.contains(".jpg") || iconName.contains(".gif")) {
                        val iconNameText = TextView(this)

                        iconNameText.setPadding((50 * resScale).toInt(), (15 * resScale).toInt(), (50 * resScale).toInt(), (15 * resScale).toInt())
                        iconNameText.height = (100 * resScale).toInt()
                        iconNameText.setBackgroundColor(Color.DKGRAY)

                        val pickerHLayout = LinearLayout(this)
                        pickerHLayout.orientation = LinearLayout.HORIZONTAL
                        pickerHLayout.setPadding((50 * resScale).toInt(), (20 * resScale).toInt(), (50 * resScale).toInt(), (20 * resScale).toInt())

                        val previewImage = ImageView(this)
                        val previewParams = LinearLayout.LayoutParams((100 * resScale).toInt(), (100 * resScale).toInt())
                        previewImage.setLayoutParams(previewParams)
                        previewImage.setImageBitmap(BitmapFactory.decodeFile(Constants.USER_FILE_STORAGE + "/launcher/icons/" + iconName))

                        val textParams = LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT)
                        iconNameText.setText(iconName)
                        iconNameText.setLayoutParams(textParams)
                        iconNameText.setOnTouchListener { text, motionEvent ->
                            if (motionEvent.action == MotionEvent.ACTION_UP) {

                            nameBox.setText(iconName)
                            picker.dismiss()
                            }
                            true
                        }

                        pickerHLayout.addView(previewImage)
                        pickerHLayout.addView(iconNameText)
                        pickerLayout.addView(pickerHLayout)
                    }

                }

                pickerLayout.setPadding((50 * resScale).toInt(), (40 * resScale).toInt(), (50 * resScale).toInt(), (10 * resScale).toInt())
                pickerScrollView.addView(pickerLayout)
                picker.setView(pickerScrollView)
                picker.show()
            }
            true
        }
        nameLayout.addView(nameButton)

        layout.addView(nameLayout)

        val showInMenusCheckBox = CheckBox(this)
        showInMenusCheckBox.setText("Show in menus")
        layout.addView(showInMenusCheckBox)

        val togglableCheckBox = CheckBox(this)
        togglableCheckBox.setText("Togglable")
        layout.addView(togglableCheckBox)


        val roundingText = TextView(this)
        roundingText.setText("Background rounding")
        layout.addView(roundingText)

        val roundingSeekBar = SeekBar(this)
        roundingSeekBar.setMin(0)
        roundingSeekBar.setMax(100)
        layout.addView(roundingSeekBar)
        layout.setPadding(50, 40, 50, 10)

        scrollView.addView(layout)
        dialog.setView(scrollView)

        dialog.setPositiveButton("Add") { _, _ ->
            var key = keyBox.text.toString()

            if (key.toIntOrNull() == null && key.length > 1)
                key = key[0].toString()

            val name = if (nameBox.text.toString().length > 0) nameBox.text.toString() else key
            val visibility = if (showInMenusCheckBox.isChecked() == true) OscVisibility.ESSENTIAL else OscVisibility.NORMAL
            val togglable = togglableCheckBox.isChecked()
            var duplicate: Boolean = false

            File(Constants.USER_FILE_STORAGE + "/launcher/controls.cfg").readLines().forEach {
                val customButton: List<String> = it.replace(" ", "").split(";")
                if (customButton.size == 9 && !it.startsWith("//")) {
                    if (customButton[0].toString() == key)
                        duplicate = true
                }
            }

            if (duplicate == true)
                Toast.makeText(this, "Key already exists.", Toast.LENGTH_SHORT).show()
            else if (keyBox.text.toString().length == 0)
                Toast.makeText(this, "Key or Keyname was not specified.", Toast.LENGTH_SHORT).show()
            else {

                val mKeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                val keyEvent = if (key.toIntOrNull() == null || key.toInt() < 10) mKeyCharacterMap.getEvents(key.toLowerCase().toCharArray())[0].getKeyCode().toInt() else key.toInt()

                val container: RelativeLayout = findViewById(R.id.controlsContainer)

                if (File(Constants.USER_FILE_STORAGE + "/launcher/icons/" + name).exists())
                    osc.addElement(OscImageButton(key, name, visibility, R.drawable.inventory, 100, 110, keyEvent, false, 70, 0.4f, togglable), container, callback as View.OnTouchListener)
                else
                osc.addElement(OscHiddenButton(key, visibility, 100, 110, name, keyEvent, 70, 0.4f, togglable, roundingSeekBar.getProgress().toFloat()), container, callback as View.OnTouchListener)


                val showInMenusString = if (showInMenusCheckBox.isChecked() == true) "1" else "0"
                val togglableString = if(togglable == true) "1" else "0"

                File(Constants.USER_FILE_STORAGE + "/launcher/controls.cfg").appendText(
key + "; " 
+ name + "; " 
+ "100; "
+ "110; " 
+ showInMenusString + "; " 
+ "70; " 
+ "0.4; " 
+ togglableString + "; "
+ roundingSeekBar.getProgress().toString() + "\n"
)


            }
        }

        dialog.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        dialog.show()


    }

    fun clickOpacityPlus(v: View) {
        changeOpacity(0.1f)
    }

    fun clickOpacityMinus(v: View) {
        changeOpacity(-0.1f)
    }

    fun clickSizePlus(v: View) {
        changeSize(5)
    }

    fun clickSizeMinus(v: View) {
        changeSize(-5)
    }

    fun clickResetControls(v: View) {
        osc.resetElements(applicationContext)
    }

    fun clickBack(v: View) {
        finish()
    }

}
