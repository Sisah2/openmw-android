/*
    Copyright (C) 2015-2017 sandstranger
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

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.preference.PreferenceManager
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.view.WindowManager
import android.widget.RelativeLayout
import com.libopenmw.openmw.R

import org.libsdl.app.SDLActivity

import constants.Constants
import cursor.MouseCursor
import parser.CommandlineParser
import ui.controls.Osc

import utils.Utils.hideAndroidControls

import android.util.DisplayMetrics
import android.os.AsyncTask
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Typeface
import android.graphics.Rect

/**
 * Enum for different mouse modes as specified in settings
 */
enum class MouseMode {
    Hybrid,
    Joystick,
    Touch;

    companion object {
        fun get(s: String): MouseMode {
            return when (s) {
                "joystick" -> Joystick
                "touch" -> Touch
                else -> Hybrid
            }
        }
    }
}

class GameActivity : SDLActivity() {

    private var prefs: SharedPreferences? = null

    val layout: RelativeLayout
        get() = SDLActivity.mLayout as RelativeLayout

    override fun loadLibraries() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val graphicsLibrary = prefs!!.getString("pref_graphicsLibrary_v2", "")
        val physicsFPS = prefs!!.getString("pref_physicsFPS2", "")
        if (!physicsFPS!!.isEmpty()) {
            try {
                Os.setenv("OPENMW_PHYSICS_FPS", physicsFPS, true)
                Os.setenv("OSG_TEXT_SHADER_TECHNIQUE", "NO_TEXT_SHADER", true)
            } catch (e: ErrnoException) {
                Log.e("OpenMW", "Failed setting environment variables.")
                e.printStackTrace()
            }
        }

        System.loadLibrary("c++_shared")
        System.loadLibrary("openal")
        System.loadLibrary("SDL2")
        if (graphicsLibrary != "gles1") {
            try {
                Os.setenv("OPENMW_GLES_VERSION", "2", true)
                Os.setenv("LIBGL_ES", "2", true)
            } catch (e: ErrnoException) {
                Log.e("OpenMW", "Failed setting environment variables.")
                e.printStackTrace()
            }

        }

        val textureShrinkingOption = prefs!!.getString("pref_textureShrinking_v2", "")
        if (textureShrinkingOption == "low") Os.setenv("LIBGL_SHRINK", "2", true)
        if (textureShrinkingOption == "medium") Os.setenv("LIBGL_SHRINK", "7", true)
        if (textureShrinkingOption == "high") Os.setenv("LIBGL_SHRINK", "6", true)

        val shaderDirOption = prefs!!.getString("pref_shadersDir_v2", "")
        if (shaderDirOption == "modified") Os.setenv("OPENMW_SHADERS", "modified", true)

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_nohighp", false) && shaderDirOption == "modified") {
            Os.setenv("LIBGL_NOHIGHP", "1", true)
        }

        Os.setenv("OSG_VERTEX_BUFFER_HINT", "VBO", true)
        Os.setenv("OPENMW_USER_FILE_STORAGE", Constants.USER_FILE_STORAGE + "/", true)
        //Os.setenv("OSG_NOTIFY_LEVEL", "FATAL", true) //hide osg errors for now, gl4es bug.
        
        val envline: String = PreferenceManager.getDefaultSharedPreferences(this).getString("envLine", "").toString()
        if (envline.length > 0) {
            val envs: List<String> = envline.split(" ", "\n")
            var i = 0

            repeat(envs.count())
            {
                val env: List<String> = envs[i].split("=")
                if (env.count() == 2) Os.setenv(env[0], env[1], true)
                i = i + 1
            }
        }

        System.loadLibrary("GL")
        System.loadLibrary("openmw")
    }

    override fun getMainSharedObject(): String {
        return "libopenmw.so"
    }


    private fun showProgressBar() {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)

        val progressBarBackground = ImageView(layout.context)
        progressBarBackground.setImageResource(R.drawable.progressbarbackground)
        progressBarBackground.setScaleType(ImageView.ScaleType.FIT_XY)
        progressBarBackground.setX(((dm.widthPixels / 2) - 405).toFloat())
        progressBarBackground.setY(((dm.heightPixels / 2) - 105).toFloat())
        layout.addView(progressBarBackground)
        progressBarBackground.getLayoutParams().width = 810
        progressBarBackground.getLayoutParams().height = 60


        val progressBar = ImageView(layout.context)
        progressBar.setImageResource(R.drawable.progressbar)
        progressBar.setScaleType(ImageView.ScaleType.FIT_XY)
        progressBar.setX(((dm.widthPixels / 2) - 400).toFloat())
        progressBar.setY(((dm.heightPixels / 2) - 100).toFloat())
        layout.addView(progressBar)
        progressBar.getLayoutParams().width = 0
        progressBar.getLayoutParams().height = 50

        val message = "GENERATING NAVMESH CACHE"
        val text = TextView(this)
        text.setText(message)
        val bounds = Rect()
        text.getPaint().getTextBounds(message!!.toString(), 0, message!!.length, bounds)
        text.setX(((dm.widthPixels / 2) - (bounds.width() / 2)) .toFloat())
        text.setY(((dm.heightPixels / 2) - 200).toFloat())
        text.setTypeface(null, Typeface.BOLD)
        layout.addView(text)

        val percentageText = TextView(this)
        percentageText.setX((dm.widthPixels / 2).toFloat())
        percentageText.setY(((dm.heightPixels / 2) + 50).toFloat())
        layout.addView(percentageText)

        Os.setenv("NAVMESHTOOL_MESSAGE", "0.0", true)
        ProgressBarUpdater(percentageText, progressBar, dm.widthPixels, dm.heightPixels).execute()
    }

    class ProgressBarUpdater(val percentageText: TextView, val progressBar: ImageView, val screenWidth: Int, val screenHeight: Int) : AsyncTask<Void, String, String>() {
        override fun doInBackground(vararg params: Void?): String {

            while(Os.getenv("NAVMESHTOOL_MESSAGE") != "Done") {
                publishProgress(Os.getenv("NAVMESHTOOL_MESSAGE"))
                Thread.sleep(50)
            }

            return "DONE"
        }
/*
        override fun onPreExecute() {
            super.onPreExecute()
        }

        override fun onPostExecute() {
            super.onPostExecute()
        }
*/
        override fun onProgressUpdate(vararg progress: String?) {
            super.onProgressUpdate()

            progressBar.requestLayout()
            progressBar.getLayoutParams().width = (8.0 * progress[0]!!.toFloat()).toInt()

            val bounds = Rect()
            percentageText.getPaint().getTextBounds(progress[0]!!.toString(), 0, progress[0]!!.length, bounds)

            percentageText.setX(((screenWidth / 2) - (bounds.width() / 2)).toFloat())
            percentageText.setText(progress[0])
        }

    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeepScreenOn()
        getPathToJni(filesDir.parent, Constants.USER_FILE_STORAGE)
        if(Os.getenv("OPENMW_GENERATE_NAVMESH_CACHE") == "1")
            showProgressBar()
        else
            showControls()
    }

    private fun showControls() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        mouseMode = MouseMode.get((prefs.getString("pref_mouse_mode",
            getString(R.string.pref_mouse_mode_default))!!))

        val pref_hide_controls = prefs.getBoolean(Constants.HIDE_CONTROLS, false)
        var osc: Osc? = null
        if (!pref_hide_controls) {
            val layout = layout
            osc = Osc()
            osc.placeElements(layout)
        }
        MouseCursor(this, osc)
    }

    private fun KeepScreenOn() {
        val needKeepScreenOn = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_screen_keeper", false)
        if (needKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    public override fun onDestroy() {
        finish()
        Process.killProcess(Process.myPid())
        super.onDestroy()
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            hideAndroidControls(this)
        }
    }

    override fun getArguments(): Array<String> {
        val cmd = PreferenceManager.getDefaultSharedPreferences(this).getString("commandLine", "")
        val commandlineParser = CommandlineParser("--resources " + Constants.USER_FILE_STORAGE + "/resources " + cmd!!)
        return commandlineParser.argv
    }

    private external fun getPathToJni(path_global: String, path_user: String)

    companion object {
        var mouseMode = MouseMode.Hybrid
    }
}
