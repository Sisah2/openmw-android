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
import android.os.Build.VERSION
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

import java.io.File

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

private fun patchShadersLinking() {
    val vertex = File(Constants.USER_FILE_STORAGE + "/resources/shaders/lib/core/vertex.h.glsl")
    var content = File(Constants.USER_FILE_STORAGE + "/resources/shaders/lib/core/vertex.glsl").readText()
    if (!vertex.readText().contains("#pragma CONVERTED")) {
        content = content.replace("#version 120" ,"vec4 modelToView(vec4 pos);")
        content = content.replace("uniform vec2 screenRes;" ,"//uniform vec2 screenRes;")
        content = content.replace("lib/core/vertex.h.glsl", "lib/core/lighting_vertex_impl.glsl")
        vertex.writeText(content + "\n#pragma CONVERTED\n")
    }

    val fragment = File(Constants.USER_FILE_STORAGE + "/resources/shaders/lib/core/fragment.h.glsl")
    if (!fragment.readText().contains("#pragma CONVERTED")) {
        content = File(Constants.USER_FILE_STORAGE + "/resources/shaders/lib/core/fragment.glsl").readText()
        content = content.replace("#version 120" ,"")
        content = content.replace("lib/core/fragment.h.glsl", "lib/core/lighting_fragment_impl.glsl")
        fragment.writeText(content + "\n#pragma CONVERTED\n")
    }

    val objectsFrag = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/objects.frag")
    content = objectsFrag.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("uniform vec2 screenRes;" ,"//uniform vec2 screenRes;")
        content = content.replace("uniform float near;" ,"//uniform float near;")
        objectsFrag.writeText(content + "\n#pragma CONVERTED\n")
    }

    val objectsVert = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/objects.vert")
    content = objectsVert.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("uniform vec2 screenRes;" ,"//uniform vec2 screenRes;")
        objectsVert.writeText(content + "\n#pragma CONVERTED\n")
    }

    val terrainFrag = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/terrain.frag")
    content = terrainFrag.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("uniform vec2 screenRes;" ,"//uniform vec2 screenRes;")
        content = content.replace("uniform float near;" ,"//uniform float near;")
        terrainFrag.writeText(content + "\n#pragma CONVERTED\n")
    }

    val groundcoverFrag = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/groundcover.frag")
    content = groundcoverFrag.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("uniform vec2 screenRes;" ,"//uniform vec2 screenRes;")
        groundcoverFrag.writeText(content + "\n#pragma CONVERTED\n")
    }

    val groundcoverVert = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/groundcover.vert")
    content = groundcoverVert.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#include " + '"' + "lib/light/clamp.glsl" + '"' + "\n#include", "#include")
        groundcoverVert.writeText(content + "\n#pragma CONVERTED\n")
    }

    val waterFrag = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/water.frag")
    content = waterFrag.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("uniform vec2 screenRes;" ,"//uniform vec2 screenRes;")
        content = content.replace("uniform float near;" ,"//uniform float near;")
        content = content.replace("uniform DirectionalLight sun;" ,"//uniform DirectionalLight sun;")
        waterFrag.writeText(content + "\n#pragma CONVERTED\n")
    }

    val lightutil = File(Constants.USER_FILE_STORAGE + "/resources/shaders/lib/light/util.glsl")
    content = lightutil.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("* int(gridSize.z)) /", "* gridSize.z) /")
        lightutil.writeText(content + "\n#pragma CONVERTED\n")
    }

    val fog = File(Constants.USER_FILE_STORAGE + "/resources/shaders/compatibility/fog.glsl")
    content = fog.readText()
    if (!content.contains("#pragma CONVERTED")) {
        content = content.replace("#include", "//")
        fog.writeText(content + "\n#pragma CONVERTED\n")
    }

}


private fun addLineToHeader(content: String, new_line: String): String {
    return content.replace("//HEADER_END\n", new_line + "\n//HEADER_END\n")
}

private fun patchShadersToGLES() {

    File(Constants.USER_FILE_STORAGE + "/resources/shaders").walkTopDown().forEach {
        if (it.isFile()) {
        var content = File(it.toString()).readText()
        if (!content.contains("#pragma GLES")) {
           // Replace version string and add default precisions
           content = content.replace("#version 120", "#version 320 es\nprecision highp float;\nprecision highp int;\n\n//HEADER_END\n")

           // Comment out all extensions
           content = content.replace("#extension", "//extension")

           // Replace all gl_* build-ins with osg_* variants except gl_Position, gl_FragCoord and gl_Fog
           content = content.replace("gl_", "osg_")
           content = content.replace("osg_Position", "gl_Position")
           content = content.replace("osg_FragCoord", "gl_FragCoord")

           content = content.replace("osg_Fog.start", "omw_Fog_start")
           content = content.replace("osg_Fog.end", "omw_Fog_end")
           content = content.replace("osg_Fog.scale", "(1.0 / (omw_Fog_end - omw_Fog_start))")
           content = content.replace("osg_Fog.color", "omw_Fog_color")

           // gl_ClipVertex not supported on GLES
           content = content.replace("osg_ClipVertex", "//osg_ClipVertex")

           // Add material uniforms
           content = content.replace("osg_FrontMaterial.emission", "osg_FrontMaterial_emission")
           content = content.replace("osg_FrontMaterial.ambient", "osg_FrontMaterial_ambient")
           content = content.replace("osg_FrontMaterial.diffuse", "osg_FrontMaterial_diffuse")
           content = content.replace("osg_FrontMaterial.specular", "osg_FrontMaterial_specular")
           content = content.replace("osg_FrontMaterial.shininess", "osg_FrontMaterial_shininess")

           content = addLineToHeader(content, "uniform vec4 osg_FrontMaterial_emission;")
           content = addLineToHeader(content, "uniform vec4 osg_FrontMaterial_ambient;")
           content = addLineToHeader(content, "uniform vec4 osg_FrontMaterial_diffuse;")
           content = addLineToHeader(content, "uniform vec4 osg_FrontMaterial_specular;")
           content = addLineToHeader(content, "uniform float osg_FrontMaterial_shininess;")


           // gl_TextureMatrix not provided by OSG, need osg::TexMat de-ffping
           content = content.replace("osg_TextureMatrix[0] * ", "")
           content = content.replace("osg_TextureMatrix[1] * ", "")
           content = content.replace("osg_TextureMatrix[2] * ", "")
           content = content.replace("osg_TextureMatrix[3] * ", "")
           content = content.replace("osg_TextureMatrix[4] * ", "")
           content = content.replace("osg_TextureMatrix[5] * ", "")
           content = content.replace("osg_TextureMatrix[6] * ", "")
           content = content.replace("osg_TextureMatrix[7] * ", "")
           content = content.replace("osg_TextureMatrix[@diffuseMapUV] * ", "")
           content = content.replace("osg_TextureMatrix[@darkMapUV] * ", "")
           content = content.replace("osg_TextureMatrix[@detailMapUV] * ", "")
           content = content.replace("osg_TextureMatrix[@decalMapUV] * ", "")
           content = content.replace("osg_TextureMatrix[@emissiveMapUV] * ", "")
           content = content.replace("osg_TextureMatrix[@normalMapUV] * ", "")
           content = content.replace("osg_TextureMatrix[@bumpMapUV] * ", "")
           content = content.replace("osg_TextureMatrix[@specularMapUV] * ", "")
           content = content.replace("osg_TextureMatrix[@glossMapUV] * ", "")

           // Remove default values from uniforms (not supported on es)
           content = content.replace("uniform bool useAdvancedShader = false;", "uniform bool useAdvancedShader;")
           content = content.replace("uniform vec2 scaling = vec2(1.0, 1.0);", "uniform vec2 scaling;")
           content = content.replace("uniform bool useDiffuseMapForShadowAlpha = true;", "uniform bool useDiffuseMapForShadowAlpha;")
           content = content.replace("uniform bool alphaTestShadows = true;", "uniform bool alphaTestShadows;")

           if (it.extension == "frag") {
               // Add osg build-in uniforms
               content = addLineToHeader(content, "uniform mat4 osg_ModelViewMatrix;")
               content = addLineToHeader(content, "uniform mat3 osg_NormalMatrix;")

               // slow gl_ModelViewMatrixInverse replacement
               content = content.replace("osg_ModelViewMatrixInverse", "inverse(osg_ModelViewMatrix)")

               // Replace gl_Fog with custom uniforms
               content = addLineToHeader(content, "uniform float omw_Fog_start;")
               content = addLineToHeader(content, "uniform float omw_Fog_end;")
               content = addLineToHeader(content, "uniform vec4 omw_Fog_color;")

               // Add fragment output variables
               content = addLineToHeader(content, "layout(location = 0) out vec4 Color0;")
               content = addLineToHeader(content, "layout(location = 1) out vec4 Color1;")
               content = content.replace("osg_FragData[0]", "Color0")
               content = content.replace("osg_FragData[1]", "Color1")
               content = content.replace("osg_FragColor", "Color0")

               // Add some defines
               content = addLineToHeader(content, "#define texture2D texture")
               content = addLineToHeader(content, "#define textureSize2D textureSize")
               content = addLineToHeader(content, "#define varying in")

               // Add some shadows stuff
               content = addLineToHeader(content, "#define shadow2DProj custom_shadow2DProj")
               content = addLineToHeader(content, "vec4 custom_shadow2DProj(sampler2DShadow sampler, vec4 uv) { return vec4(textureProj(sampler, uv)); }")
           }
           else if (it.extension == "vert") {
               // Add osg build-in attributes/uniforms
               content = addLineToHeader(content, "in vec4 osg_Vertex;")
               content = addLineToHeader(content, "in vec3 osg_Normal;")
               content = addLineToHeader(content, "in vec4 osg_Color;")
               content = addLineToHeader(content, "in vec3 osg_MultiTexCoord0;")
               content = addLineToHeader(content, "in vec3 osg_MultiTexCoord1;")
               content = addLineToHeader(content, "in vec3 osg_MultiTexCoord2;")
               content = addLineToHeader(content, "in vec3 osg_MultiTexCoord3;")
               content = addLineToHeader(content, "in vec3 osg_MultiTexCoord4;")
               content = addLineToHeader(content, "in vec3 osg_MultiTexCoord5;")
               content = addLineToHeader(content, "in vec3 osg_MultiTexCoord6;")
               content = addLineToHeader(content, "in vec3 osg_MultiTexCoord7;")

               content = addLineToHeader(content, "uniform mat4 osg_ModelViewProjectionMatrix;")
               content = addLineToHeader(content, "uniform mat4 osg_ModelViewMatrix;")
               content = addLineToHeader(content, "uniform mat3 osg_NormalMatrix;")

               // Add some defines
               content = addLineToHeader(content, "#define attribute in")
               content = addLineToHeader(content, "#define varying out")
           }
           else if (it.extension == "glsl") {
               content = content.replace("vec4 applyFogAtDist(vec4 color, float euclideanDist, float linearDist, float far)\n{", "uniform bool isReflection;\nvec4 applyFogAtDist(vec4 color, float euclideanDist, float linearDist, float far)\n{\nif (omw_Fog_end == 0.0) return color;")

               content = content.replace("sampler2DShadow", "highp sampler2DShadow")
               content = content.replace("textureSize2D(diffuseMap, 0);", "vec2(textureSize2D(diffuseMap, 0));")
           }

           File(it.toString()).writeText(content + "\n#pragma GLES\n")
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
        val physicsFPS = prefs!!.getString("pref_physicsFPS2", "")
        if (!physicsFPS!!.isEmpty()) {
            try {
                Os.setenv("OPENMW_PHYSICS_FPS", physicsFPS, true)
            } catch (e: ErrnoException) {
                Log.e("OpenMW", "Failed setting environment variables.")
                e.printStackTrace()
            }
        }

        System.loadLibrary("c++_shared")
        System.loadLibrary("openal")
        System.loadLibrary("SDL2")

        try {
            Os.setenv("OPENMW_GLES_VERSION", "32", true)
            Os.setenv("LIBGL_ES", "3", true)
        } catch (e: ErrnoException) {
            Log.e("OpenMW", "Failed setting environment variables.")
            e.printStackTrace()
        }

        if (!prefs!!.getBoolean("pref_use_spirv_shader_conv", true))
            Os.setenv("LIBGL_SIMPLE_SHADERCONV", "1", true)

        val enableANGLE = prefs!!.getBoolean("pref_use_angle", false)
        if (enableANGLE == true) {
            Os.setenv("LIBGL_SIMPLE_SHADERCONV", "0", true)
            Os.setenv("LIBGL_GLES", "libGLESv2_angle.so", true)
            Os.setenv("LIBGL_EGL", "libEGL_angle.so", true)
            Os.setenv("SDL_VIDEO_GL_DRIVER", "libGLESv2_angle.so", true)
            Os.setenv("SDL_VIDEO_EGL_DRIVER", "libEGL_angle.so", true)
        }

        Os.setenv("OSG_VERTEX_BUFFER_HINT", "VBO", true)
        Os.setenv("OSG_GL_TEXTURE_STORAGE", "OFF", true)
        Os.setenv("OSG_TEXT_SHADER_TECHNIQUE", "ALL", true)

        //Os.setenv("OPENMW_USER_FILE_STORAGE", Constants.USER_FILE_STORAGE + "/", true)
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

        patchShadersLinking()
        patchShadersToGLES()

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

        val displayInCutoutArea = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_display_cutout_area", true)
        if (displayInCutoutArea || android.os.Build.VERSION.SDK_INT < 29) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

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
