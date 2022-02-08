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

package ui.activity

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.system.ErrnoException
import android.system.Os
import android.util.DisplayMetrics
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.bugsnag.android.Bugsnag

import com.libopenmw.openmw.BuildConfig
import com.libopenmw.openmw.R
import constants.Constants
import file.GameInstaller

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader

import file.utils.CopyFilesFromAssets
import mods.ModType
import mods.ModsCollection
import mods.ModsDatabaseOpenHelper
import ui.fragments.FragmentSettings
import permission.PermissionHelper
import utils.MyApp
import utils.Utils.hideAndroidControls
import java.util.*

import android.util.Base64

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApp.app.defaultScaling = determineScaling()

        PermissionHelper.getWriteExternalStoragePermission(this@MainActivity)
        setContentView(R.layout.main)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val theme = prefs.getInt(getString(R.string.theme), 0)
        if(theme == 0) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        else if(theme == 1) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        fragmentManager.beginTransaction()
            .replace(R.id.content_frame, FragmentSettings()).commit()

        setSupportActionBar(findViewById(R.id.main_toolbar))

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { checkStartGame() }

        if (prefs.getString("bugsnag_consent", "")!! == "") {
            askBugsnagConsent()
        }
    }

    /**
     * Set new user consent and maybe restart the app
     * @param consent New value of bugsnag consent
     */
    @SuppressLint("ApplySharedPref")
    private fun setBugsnagConsent(consent: String) {
        val currentConsent = prefs.getString("bugsnag_consent", "")!!
        if (currentConsent == consent)
            return

        // We only need to force a restart if the user revokes their consent
        // If user grants consent, crashes won't be reported for 1 game session, but that's alright
        val needRestart = currentConsent == "true" && consent == "false"

        with (prefs.edit()) {
            putString("bugsnag_consent", consent)
            commit()
        }

        if (needRestart) {
            AlertDialog.Builder(this)
                .setOnDismissListener { System.exit(0) }
                .setTitle(R.string.bugsnag_consent_restart_title)
                .setMessage(R.string.bugsnag_consent_restart_message)
                .setPositiveButton(android.R.string.ok) { _, _ -> System.exit(0) }
                .show()
        }
    }

    /**
     * Opens the url in a web browser and gracefully handles the failure
     * @param url Url to open
     */
    fun openUrl(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_browser_title)
                .setMessage(getString(R.string.no_browser_message, url))
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }
    }

    /**
     * Asks the user if they want to automatically report crashes
     */
    private fun askBugsnagConsent() {
        // Do nothing for builds without api-key
        if (!MyApp.haveBugsnagApiKey)
            return

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.bugsnag_consent_title)
            .setMessage(R.string.bugsnag_consent_message)
            .setNeutralButton(R.string.bugsnag_policy) { _, _ -> /* set up below */ }
            .setNegativeButton(R.string.bugsnag_no) { _, _ -> setBugsnagConsent("false") }
            .setPositiveButton(R.string.bugsnag_yes) { _, _ -> setBugsnagConsent("true") }
            .create()

        dialog.show()

        // don't close the dialog when the privacy-policy button is clicked
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            openUrl("https://omw.xyz.is/privacy-policy.html")
        }
    }

    /**
     * Checks that the game is properly installed and if so, starts the game
     * - the game files must be selected
     * - there must be at least 1 activated mod (user can ignore this warning)
     */
    private fun checkStartGame() {
        // First, check that there are game files present
        val inst = GameInstaller(prefs.getString("game_files", "")!!)
        if (!inst.check()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_data_files_title)
                .setMessage(R.string.no_data_files_message)
                .setNeutralButton(R.string.dialog_howto) { _, _ ->
                    openUrl("https://omw.xyz.is/game.html")
                }
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int -> }
                .show()
            return
        }
/**
        // Second, check if user has at least one mod enabled
	var dataDirs = ArrayList<String>()
	dataDirs.add(inst.findDataFiles())
        val plugins = ModsCollection(ModType.Plugin, dataDirs,
            ModsDatabaseOpenHelper.getInstance(this))
        if (plugins.mods.count { it.enabled } == 0) {
            // No mods enabled, show a warning
            AlertDialog.Builder(this)
                .setTitle(R.string.no_content_files_title)
                .setMessage(R.string.no_content_files_message)
                .setNeutralButton(R.string.dialog_howto) { _, _ ->
                    openUrl("https://omw.xyz.is/mods.html")
                }
                .setNegativeButton(R.string.no_content_files_dismiss) { _, _ -> startGame() }
                .setPositiveButton(R.string.configure_mods) { _, _ ->
                    this.startActivity(Intent(this, ModsActivity::class.java))
                }
                .show()

            return
        }
*/
        // If everything's alright, start the game
        startGame()
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory)
            for (child in fileOrDirectory.listFiles())
                deleteRecursive(child)

        fileOrDirectory.delete()
    }

    private fun logConfig() {

    }

    private fun runGame() {
        logConfig()
        val intent = Intent(this@MainActivity,
            GameActivity::class.java)
        finish()

        this@MainActivity.startActivityForResult(intent, 1)
    }


    /**
     * Set up fixed screen resolution
     * This doesn't do anything unless the user chose to override screen resolution
     */
    private fun obtainFixedScreenResolution() {
        // Split resolution e.g 640x480 to width/height
        val customResolution = prefs.getString("pref_customResolution", "")
        val sep = customResolution!!.indexOf("x")
        if (sep > 0) {
            try {
                val x = Integer.parseInt(customResolution.substring(0, sep))
                val y = Integer.parseInt(customResolution.substring(sep + 1))

                resolutionX = x
                resolutionY = y
            } catch (e: NumberFormatException) {
                // user entered resolution wrong, just ignore it
            }
        }
    }

    /**
     * Generates openmw.cfg using values from openmw.base.cfg combined with mod manager settings
     */
    private fun generateOpenmwCfg() {
        // contents of openmw.base.cfg
        val base: String
        // contents of openmw.fallback.cfg
        val fallback: String

        // try to read the files
        try {
            base = File(Constants.OPENMW_BASE_CFG).readText()
            // TODO: support user custom options
            fallback = File(Constants.OPENMW_FALLBACK_CFG).readText()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read openmw.base.cfg or openmw.fallback.cfg", e)
            return
        }

        val db = ModsDatabaseOpenHelper.getInstance(this)

	var dataFilesList = ArrayList<String>()
	var dataDirsPath = ArrayList<String>()
	dataFilesList.add(GameInstaller.getDataFiles(this))
        dataDirsPath.add(GameInstaller.getDataFiles(this).dropLast(10))

	File(GameInstaller.getDataFiles(this).dropLast(10)).listFiles().forEach {
	    if (!it.isFile())
	        dataFilesList.add(GameInstaller.getDataFiles(this).dropLast(10) + it.getName())
	}

        val resources = ModsCollection(ModType.Resource, dataFilesList, db)
        val dirs = ModsCollection(ModType.Dir, dataDirsPath, db)
        val plugins = ModsCollection(ModType.Plugin, dataFilesList, db)

        try {
            // generate final output.cfg
            var output = base + "\n" + fallback + "\n"

            // output resources
            resources.mods
                .filter { it.enabled }
                .forEach { output += "fallback-archive=${it.filename}\n" }

            // output data dirs
            dirs.mods
                .filter { it.enabled }
                .forEach { output += "data=" + '"' + GameInstaller.getDataFiles(this).dropLast(10) + it.filename + '"' + "\n" }

            // output plugins
            plugins.mods
                .filter { it.enabled }
                .forEach { output += "content=${it.filename}\n" }

            // write everything to openmw.cfg
            File(Constants.OPENMW_CFG).writeText(output)
//            File("/storage/emulated/0/omw_nightly/config/test.cfg").writeText(output)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to generate openmw.cfg.", e)
        }
    }

    /**
     * Determines required screen scaling based on resolution and physical size of the device
     */
    private fun determineScaling(): Float {
        // The idea is to stretch an old-school 1024x768 monitor to the device screen
        // Assume that 1x scaling corresponds to resolution of 1024x768
        // Assume that the longest side of the device corresponds to the 1024 side
        // Therefore scaling is calculated as longest size of the device divided by 1024
        // Note that it doesn't take into account DPI at all. Which is fine for now, but in future
        // we might want to add some bonus scaling to e.g. phone devices so that it's easier
        // to click things.

        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        return maxOf(dm.heightPixels, dm.widthPixels) / 1024.0f
    }

    /**
     * Removes old and creates new files located in private application directories
     * (i.e. under getFilesDir(), or /data/data/.../files)
     */
    private fun reinstallStaticFiles() {
        // we store global "config" and "resources" under private files

        // wipe old version first
        removeStaticFiles()

        // copy in the new version
        val assetCopier = CopyFilesFromAssets(this)
        assetCopier.copy("libopenmw/resources", Constants.RESOURCES)
        assetCopier.copy("libopenmw/openmw", Constants.GLOBAL_CONFIG)

        // set up user config (if not present)
        File(Constants.USER_CONFIG).mkdirs()
        if (!File(Constants.USER_OPENMW_CFG).exists())
            File(Constants.USER_OPENMW_CFG).writeText("# This is the user openmw.cfg. Feel free to modify it as you wish.\n")

        // create user custom icon folder as a hint
        File(Constants.USER_FILE_STORAGE + "/icons").mkdirs()
        if (!File(Constants.USER_FILE_STORAGE + "/icons/paste custom icons here.txt").exists())
            File(Constants.USER_FILE_STORAGE + "/icons/paste custom icons here.txt").writeText(
"attack.png \ninventory.png \njournal.png \njump.png \nkeyboard.png \nmouse.png \npause.png \npointer_arrow.png \nrun.png \nsave.png \nsneak.png \nthird_person.png \ntoggle_magic.png \ntoggle_weapon.png \ntoggle.png \nuse.png \nwait.png")

        // set version stamp
        File(Constants.VERSION_STAMP).writeText(BuildConfig.VERSION_CODE.toString())
    }

    /**
     * Removes global static files, these include resources and config
     */
    private fun removeStaticFiles() {
        // remove version stamp so that reinstallStaticFiles is called during game launch
        File(Constants.VERSION_STAMP).delete()

        deleteRecursive(File(Constants.GLOBAL_CONFIG))
        deleteRecursive(File(Constants.RESOURCES))
    }

    /**
     * Resets user config to default values by removing it
     */
    private fun removeUserConfig() {
        deleteRecursive(File(Constants.USER_CONFIG))
    }

    /**
     * Reset user resource files to default
     */
    private fun removeResourceFiles() {
        reinstallStaticFiles()
        deleteRecursive(File(Constants.USER_FILE_STORAGE + "/resources/"))

        var src = File(Constants.RESOURCES)
        var dst = File(Constants.USER_FILE_STORAGE + "/resources/")
        dst.mkdirs()
        src.copyRecursively(dst, true) 
    }

    private fun configureDefaultsBin(args: Map<String, String>) {
        val defaults = File(Constants.DEFAULTS_BIN).readText()
        val decoded = String(android.util.Base64.decode(defaults, android.util.Base64.DEFAULT))
        val lines = decoded.lines().map {
            for ((k, v) in args) {
                if (it.startsWith("$k ="))
                    return@map "$k = $v"
            }
            it
        }
        val data = lines.joinToString("\n")

        val encoded = android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.NO_WRAP)
        File(Constants.DEFAULTS_BIN).writeText(encoded)
    }

    private fun startGame() {
        // Get scaling factor from config; if invalid or not provided, generate one
        var scaling = 0f

        try {
            scaling = prefs.getString("pref_uiScaling", "")!!.toFloat()
        } catch (e: NumberFormatException) {
            // Reset the invalid setting
            with(prefs.edit()) {
                putString("pref_uiScaling", "")
                apply()
            }
        }

        // set up gamma, if invalid, use the default (1.0)
        var gamma = 1.0f
        try {
            gamma = prefs.getString("pref_gamma", "")!!.toFloat()
        } catch (e: NumberFormatException) {
            // Reset the invalid setting
            with(prefs.edit()) {
                putString("pref_gamma", "")
                apply()
            }
        }

        try {
            Os.setenv("OPENMW_GAMMA", "%.2f".format(Locale.ROOT, gamma), true)
        } catch (e: ErrnoException) {
            // can't really do much if that fails...
        }

        // If scaling didn't get set, determine it automatically
        if (scaling == 0f) {
            scaling = MyApp.app.defaultScaling
        }

        val dialog = ProgressDialog.show(
            this, "", "Preparing for launch...", true)

        val activity = this

        // hide the controls so that ScreenResolutionHelper can get the right resolution
        hideAndroidControls(this)

        val th = Thread {
            try {
                // Only reinstall static files if they are of a mismatched version
                try {
                    val stamp = File(Constants.VERSION_STAMP).readText().trim()
                    if (stamp.toInt() != BuildConfig.VERSION_CODE) {
                        reinstallStaticFiles()
                    }
                } catch (e: Exception) {
                    reinstallStaticFiles()
                }

                val inst = GameInstaller(prefs.getString("game_files", "")!!)

                // Regenerate the fallback file in case user edits their Morrowind.ini
                inst.convertIni(prefs.getString("pref_encoding", GameInstaller.DEFAULT_CHARSET_PREF)!!)

                generateOpenmwCfg()

                // openmw.cfg: data, resources
                file.Writer.write(Constants.OPENMW_CFG, "resources", Constants.RESOURCES)
                file.Writer.write(Constants.OPENMW_CFG, "data", "\"" + inst.findDataFiles() + "\"")

                file.Writer.write(Constants.OPENMW_CFG, "encoding", prefs!!.getString("pref_encoding", GameInstaller.DEFAULT_CHARSET_PREF)!!)

                var src = File(Constants.RESOURCES)
                var dst = File(Constants.USER_FILE_STORAGE + "/resources/")
                val resourcesDirCreated :Boolean = dst.mkdirs()

                if(resourcesDirCreated)
                    src.copyRecursively(dst, false) 


                configureDefaultsBin(mapOf(
                        "scaling factor" to "%.2f".format(Locale.ROOT, scaling),
                        // android-specific defaults
                        "viewing distance" to "2048.0",
                        "camera sensitivity" to "0.4",
                        // and a bunch of windows positioning
                        "stats x" to "0.0",
                        "stats y" to "0.0",
                        "stats w" to "0.375",
                        "stats h" to "0.4275",
                        "spells x" to "0.625",
                        "spells y" to "0.5725",
                        "spells w" to "0.375",
                        "spells h" to "0.4275",
                        "map x" to "0.625",
                        "map y" to "0.0",
                        "map w" to "0.375",
                        "map h" to "0.5725",
                        "inventory y" to "0.4275",
                        "inventory w" to "0.6225",
                        "inventory h" to "0.5725",
                        "inventory container x" to "0.0",
                        "inventory container y" to "0.4275",
                        "inventory container w" to "0.6225",
                        "inventory container h" to "0.5725",
                        "inventory barter x" to "0.0",
                        "inventory barter y" to "0.4275",
                        "inventory barter w" to "0.6225",
                        "inventory barter h" to "0.5725",
                        "inventory companion x" to "0.0",
                        "inventory companion y" to "0.4275",
                        "inventory companion w" to "0.6225",
                        "inventory companion h" to "0.5725",
                        "dialogue x" to "0.095",
                        "dialogue y" to "0.095",
                        "dialogue w" to "0.810",
                        "dialogue h" to "0.890",
                        "console x" to "0.0",
                        "console y" to "0.0",
                        "container x" to "0.25",
                        "container y" to "0.0",
                        "container w" to "0.75",
                        "container h" to "0.375",
                        "barter x" to "0.25",
                        "barter y" to "0.0",
                        "barter w" to "0.75",
                        "barter h" to "0.375",
                        "companion x" to "0.25",
                        "companion y" to "0.0",
                        "companion w" to "0.75",
                        "companion h" to "0.375",

			// Game Mechanics
                        "toggle sneak" to if(prefs.getBoolean("gs_toggle_sneak", true)) "true" else "false",
                        "uncapped damage fatigue" to if(prefs.getBoolean("gs_uncapped_damage_fatigue", false)) "true" else "false",
                        "rebalance soul gem values" to if(prefs.getBoolean("gs_soulgem_values_rebalance", false)) "true" else "false",
                        "followers attack on sight" to if(prefs.getBoolean("gs_followers_defend_immediately", false)) "true" else "false",
                        "barter disposition change is permanent" to if(prefs.getBoolean("gs_permanent_barter_disposition_changes", false)) "true" else "false",
                        "NPCs avoid collisions" to if(prefs.getBoolean("gs_npc_avoid_collision", false)) "true" else "false",
                        "only appropriate ammunition bypasses resistance" to if(prefs.getBoolean("gs_only_weapon_bs", false)) "true" else "false",
                        "normalise race speed" to if(prefs.getBoolean("gs_racial_variation_in_speed_fix", false)) "true" else "false",
                        "swim upward correction" to if(prefs.getBoolean("gs_swim_upward_correction", false)) "true" else "false",
                        "can loot during death animation" to if(prefs.getBoolean("gs_can_loot_during_death_animation", true)) "true" else "false",
                        "enchanted weapons are magical" to if(prefs.getBoolean("gs_enchanted_weapons_are_magical", true)) "true" else "false",
                        "classic reflected absorb spells behavior" to if(prefs.getBoolean("gs_classic_reflected_absorb_spells_behavior", true)) "true" else "false",
                        "always allow stealing from knocked out actors" to if(prefs.getBoolean("gs_always_allow_stealing_from_knocked_out_actors", false)) "true" else "false",
                        "allow actors to follow over water surface" to if(prefs.getBoolean("gs_always_allow_npc_to_follow_over_water_surface", true)) "true" else "false",
                        "strength influences hand to hand" to prefs.getString("gs_factor_strength_into_hand-to-hand_combat", "0").toString(),

			// Visuals graphics
                        "antialiasing" to prefs.getString("gs_antialiasing", "0").toString(),
                        "framerate limit" to prefs.getString("gs_framerate_limit", "60").toString(),

			// Visuals animations
                        "use magic item animations" to if(prefs.getBoolean("gs_use_magic_item_animation", false)) "true" else "false",
                        "use additional anim sources" to if(prefs.getBoolean("gs_use_additional_animation_sources", false)) "true" else "false",
                        "weapon sheathing" to if(prefs.getBoolean("gs_weapon_sheating", false)) "true" else "false",
                        "shield sheathing" to if(prefs.getBoolean("gs_shield_sheating", false)) "true" else "false",
                        "graphic herbalism" to if(prefs.getBoolean("gs_enable_graphics_herbalism", true)) "true" else "false",
                        "smooth movement" to if(prefs.getBoolean("gs_smooth_movement", false)) "true" else "false",
                        "turn to movement direction" to if(prefs.getBoolean("gs_turn_to_movement_direction", false)) "true" else "false",

			// Visuals shaders
                        "auto use object normal maps" to if(prefs.getBoolean("gs_auto_use_object_normal_maps", false)) "true" else "false",
                        "auto use object specular maps" to if(prefs.getBoolean("gs_auto_use_object_specular_maps", false)) "true" else "false",
                        "auto use terrain normal maps" to if(prefs.getBoolean("gs_auto_use_terrain_normal_maps", false)) "true" else "false",
                        "auto use terrain specular maps" to if(prefs.getBoolean("gs_auto_use_terrain_specular_maps", false)) "true" else "false",
                        "apply lighting to environment maps" to if(prefs.getBoolean("gs_bump_map_local_lighting", false)) "true" else "false",
                        "radial fog" to if(prefs.getBoolean("gs_radial_fog", false)) "true" else "false",
                        "soft particles" to if(prefs.getBoolean("gs_soft_particles", false)) "true" else "false",

			// Visuals terrain
                        "object paging min size" to prefs.getString("gs_object_paging_min_size", "0.01").toString(),
                        "distant terrain" to if(prefs.getBoolean("gs_distant_land", false)) "true" else "false",
                        "object paging active grid" to if(prefs.getBoolean("gs_active_grid_object_paging", true)) "true" else "false",

			// Camera
                        "view over shoulder" to if(prefs.getBoolean("gs_view_over_shoulder", false)) "true" else "false",
                        "auto switch shoulder" to if(prefs.getBoolean("gs_auto_switch_shoulder", true)) "true" else "false",
                        "view over shoulder offset" to prefs.getString("gs_default_shoulder", "30 -10").toString(),
                        "preview if stand still" to if(prefs.getBoolean("gs_preview_if_standing_still", false)) "true" else "false",
                        "deferred preview rotation" to if(prefs.getBoolean("gs_deferred_preview_rotation", true)) "true" else "false",
                        "head bobbing" to if(prefs.getBoolean("gs_head_bobbing", false)) "true" else "false",
                        "hand inertia" to if(prefs.getBoolean("gs_hand_inertia", false)) "3.0" else "0.0",

			// Interface
                        "show owned" to prefs!!.getString("gs_show_owned", "0").toString(),
                        "show effect duration" to if(prefs.getBoolean("gs_show_effect_duration", false)) "true" else "false",
                        "show enchant chance" to if(prefs.getBoolean("gs_show_enchant_chance", false)) "true" else "false",
                        "show melee info" to if(prefs.getBoolean("gs_show_melee_info", false)) "true" else "false",
                        "show projectile damage" to if(prefs.getBoolean("gs_show_projectile_damage", false)) "true" else "false",
                        "color topic enable" to if(prefs.getBoolean("gs_change_dialogue_topic_color", false)) "true" else "false",
                        "stretch menu background" to if(prefs.getBoolean("gs_stretch_menu_background", false)) "true" else "false",
                        "allow zooming" to if(prefs.getBoolean("gs_can_zoom_on_maps", false)) "true" else "false",

			// Bug Fixes
                        "prevent merchant equipping" to if(prefs.getBoolean("gs_merchant_equipping_fix", false)) "true" else "false",
                        "trainers training skills based on base skill" to if(prefs.getBoolean("gs_trainers_bs", false)) "true" else "false",

			// Miscellaneous
                        "timeplayed" to if(prefs.getBoolean("gs_add_time_to_saves", false)) "true" else "false",
                        "max quicksaves" to prefs.getString("gs_maximum_quicksaves", "1").toString(),

			// Engine Settings
                        "enabled" to if(prefs.getBoolean("gs_groundcover", false)) "true" else "false",
                        "enable" to if(prefs.getBoolean("gs_build_navmesh", true)) "true" else "false",
                        "write to navmeshdb" to if(prefs.getBoolean("gs_write_navmesh", false)) "true" else "false",
                        "async nav mesh updater threads" to prefs.getString("gs_navmesh_threads", "1").toString(),
                        "async num threads" to prefs.getString("gs_physics_threads", "1").toString(),
                        "preload num threads" to prefs.getString("gs_preload_threads", "1").toString()

                ))

                runOnUiThread {
                    obtainFixedScreenResolution()
                    dialog.hide()
                    runGame()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write config files.", e)
            }
        }
        th.start()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_settings, menu)

        if (!MyApp.haveBugsnagApiKey)
            menu.findItem(R.id.action_bugsnag_consent).setVisible(false)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_config -> {
                removeStaticFiles()
                Toast.makeText(this, getString(R.string.config_was_reset), Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_reset_user_config -> {
                removeUserConfig()
                Toast.makeText(this, getString(R.string.user_config_was_reset), Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_reset_user_resources -> {
                removeStaticFiles()
                removeResourceFiles()
                Toast.makeText(this, getString(R.string.user_resources_was_reset), Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_theme_system -> {
                with (prefs.edit()) {
                    putInt(getString(R.string.theme), 0)
                    apply()
                }

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

                Toast.makeText(this, "Theme set to system", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_theme_light -> {
                with (prefs.edit()) {
                    putInt(getString(R.string.theme), 1)
                    apply()
                }

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

                Toast.makeText(this, "Theme set to light", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_theme_dark -> {
                with (prefs.edit()) {
                    putInt(getString(R.string.theme), 2)
                    apply()
                }

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

                Toast.makeText(this, "Theme set to dark", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_generate_navmesh -> {
                Os.setenv("OPENMW_GENERATE_NAVMESH_CACHE", "1", true)
                checkStartGame()
                true
            }

            R.id.action_about -> {
                val text = assets.open("libopenmw/3rdparty-licenses.txt")
                    .bufferedReader()
                    .use { it.readText() }

                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.about_title))
                    .setMessage(text)
                    .show()

                true
            }

            R.id.action_bugsnag_consent -> {
                askBugsnagConsent()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "OpenMW-Launcher"

        var resolutionX = 0
        var resolutionY = 0
    }
}
