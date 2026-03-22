/*
    Copyright (C) 2019 Ilya Zhuravlev

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

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import file.GameInstaller
import kotlinx.android.synthetic.main.activity_settings.*
import android.view.MenuItem
import java.io.File


import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.Intent
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceManager
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup

import constants.Constants
import android.widget.Toast
import android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION
import android.opengl.EGL14.EGL_OPENGL_ES2_BIT
import android.opengl.GLES20
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGL10.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

class FragmentGameSettings : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.game_settings)
/*
        findPreference("game_settings_game_mechanics").setOnPreferenceClickListener {
            getPreferenceScreen().removeAll()
            addPreferencesFromResource(R.xml.gs_game_mechanics)
            true
        }

        findPreference("game_settings_visuals").setOnPreferenceClickListener {
            getPreferenceScreen().removeAll()
            addPreferencesFromResource(R.xml.gs_visuals)
            true
        }
*/
        findPreference("game_settings_game_mechanics").setOnPreferenceClickListener {
            val intent = Intent(activity, Game_Mechanics_SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("game_settings_visuals").setOnPreferenceClickListener {
            val intent = Intent(activity, Visuals_SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("game_settings_shadows").setOnPreferenceClickListener {
            val intent = Intent(activity, Shadows_SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("game_settings_animations").setOnPreferenceClickListener {
            val intent = Intent(activity, Animations_SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("game_settings_interface").setOnPreferenceClickListener {
            val intent = Intent(activity, Interface_SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("game_settings_bug_fixes").setOnPreferenceClickListener {
            val intent = Intent(activity, Bug_Fixes_SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("game_settings_miscellaneous").setOnPreferenceClickListener {
            val intent = Intent(activity, Miscellaneous_SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("game_settings_engine").setOnPreferenceClickListener {
            val intent = Intent(activity, Engine_SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }
    }
}

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))

        // Enable the "back" icon in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
/*
        // Switch tabs between categories
        settings_tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {	
                settings_flipper.displayedChild = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })
*/

        fragmentManager.beginTransaction()
            .replace(R.id.settings_frame, FragmentGameSettings()).commit()
    }

    /**
     * Makes the "back" icon in the actionbar perform the back operation
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}

class FragmentGameSettingsPage(val res: Int) : PreferenceFragment(), OnSharedPreferenceChangeListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(res)
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        val config = intArrayOf(
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 0,
            EGL_STENCIL_SIZE, 0,
            EGL_NONE
        )

        fun chooseEglConfig(egl: EGL10, eglDisplay: EGLDisplay) : EGLConfig {
            val configsCount = intArrayOf(0);
            val configs = arrayOfNulls<EGLConfig>(1);
            egl.eglChooseConfig(eglDisplay, config, configs, 1, configsCount)
            return configs[0]!!
        }

        fun getExtensionList() {
            val egl = EGLContext.getEGL() as EGL10
            val eglDisplay = egl.eglGetDisplay(EGL_DEFAULT_DISPLAY)
            egl.eglInitialize(eglDisplay, intArrayOf(0,0))   // getting OpenGL ES 2
            val eglConfig = chooseEglConfig(egl, eglDisplay);
            val eglContext = egl.eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE));

            //FIXME: Create some surface to dont rely on extension, pbuffer require egl 1.4 what is better?
            //val eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, texture_view.surfaceTexture, null)
            if (egl.eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, eglContext) == true) {
                val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
                File(Constants.USER_FILE_STORAGE + "/launcher/extensions.log").writeText(extensions.replace(" ", "\n"))

                egl.eglDestroyContext(eglDisplay, eglContext)
                //egl.eglDestroySurface(eglDisplay, eglSurface)

                val prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity())
                val enableANGLE = prefs!!.getBoolean("pref_use_angle", false)

                if (extensions.contains("GL_EXT_depth_clamp") == false && !enableANGLE) {
                    findPreference("gs_object_shadows").isEnabled = false
                    findPreference("gs_terrain_shadows").isEnabled = false
                    findPreference("gs_actor_shadows").isEnabled = false
                    findPreference("gs_player_shadows").isEnabled = false
                    findPreference("gs_indoor_shadows").isEnabled = false
                    findPreference("gs_shadow_map_resolution").isEnabled = false
                    findPreference("gs_shadow_computation_method").isEnabled = false
                    findPreference("gs_shadows_distance").isEnabled = false
                    findPreference("gs_shadows_fade_start").isEnabled = false
                    findPreference("gs_shadows_pcf").isEnabled = false
                }
            }
            else Toast.makeText(this.getActivity(), "Cant check for extensions!, shadows setting wont be applyed!", Toast.LENGTH_SHORT).show()
        }

        if (res == R.xml.gs_shadows) getExtensionList()

        // what is this?
        //if (res == R.xml.gs_game_mechanics) findPreference("gs_always_allow_npc_to_follow_over_water_surface").isEnabled = preferenceScreen.sharedPreferences.getBoolean("gs_build_navmesh", true)

        if (res == R.xml.gs_animations) updatePreference(preferenceScreen.sharedPreferences, "gs_use_additional_animation_sources")
        if (res == R.xml.gs_engine) updatePreference(preferenceScreen.sharedPreferences, "gs_build_navmesh")
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updatePreference(sharedPreferences, key)
    }

    private fun updatePreference(sharedPreferences: SharedPreferences, key: String) {

        if(key == "gs_use_additional_animation_sources") {
            findPreference("gs_weapon_sheating").isEnabled = sharedPreferences.getBoolean("gs_use_additional_animation_sources", false)
            findPreference("gs_shield_sheating").isEnabled = sharedPreferences.getBoolean("gs_use_additional_animation_sources", false)
        }

        if(key == "gs_build_navmesh") {
            findPreference("gs_write_navmesh").isEnabled = sharedPreferences.getBoolean("gs_build_navmesh", true)
            findPreference("gs_navmesh_threads").isEnabled = sharedPreferences.getBoolean("gs_build_navmesh", true)
        }
    }
}

class Game_Mechanics_SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(R.id.settings_frame, FragmentGameSettingsPage(R.xml.gs_game_mechanics)).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class Visuals_SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(R.id.settings_frame, FragmentGameSettingsPage(R.xml.gs_visuals)).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class Shadows_SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(R.id.settings_frame, FragmentGameSettingsPage(R.xml.gs_shadows)).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class Animations_SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(R.id.settings_frame, FragmentGameSettingsPage(R.xml.gs_animations)).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class Interface_SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(R.id.settings_frame, FragmentGameSettingsPage(R.xml.gs_interface)).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class Bug_Fixes_SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(R.id.settings_frame, FragmentGameSettingsPage(R.xml.gs_bug_fixes)).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class Miscellaneous_SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(R.id.settings_frame, FragmentGameSettingsPage(R.xml.gs_miscellaneous)).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class Engine_SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.settings_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fragmentManager.beginTransaction().replace(R.id.settings_frame, FragmentGameSettingsPage(R.xml.gs_engine)).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

