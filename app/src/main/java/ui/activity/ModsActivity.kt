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
import kotlinx.android.synthetic.main.activity_mods.*
import mods.*
import android.view.MenuItem
import java.io.File

import constants.Constants
import android.view.Menu
import com.codekidlabs.storagechooser.StorageChooser
import com.google.android.material.textfield.TextInputLayout
import android.preference.PreferenceManager
import android.widget.EditText
import android.app.AlertDialog

import android.app.ProgressDialog
import java.io.IOException
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.ScrollView
import android.widget.Toast
import android.widget.CheckBox
import android.widget.TextView
import android.widget.SeekBar
import android.widget.ListView
import android.widget.ArrayAdapter
import android.view.WindowManager
import android.widget.Spinner

import kotlinx.coroutines.*

class ModsActivity : AppCompatActivity() {
    var mPluginAdapter = ModsAdapter(this)
    var mResourceAdapter = ModsAdapter(this)
    var mDirAdapter = ModsAdapter(this)
    var mGroundcoverAdapter = ModsAdapter(this)

    var fallbackList = mutableSetOf<String>()
    var archiveList = mutableListOf<Pair<String, Boolean>>()
    var dataDirsList = mutableListOf<Pair<String, Boolean>>()
    var contentList = mutableListOf<Pair<String, Boolean>>()
    var groundcoverList = mutableListOf<Pair<String, Boolean>>()
    var fsPlugins = mutableSetOf<String>()
    var fsArchives = mutableSetOf<String>()

    val pluginExtensions: Array<String> = arrayOf("esm", "esp", "omwaddon", "omwgame", "omwscripts")
    val archiveExtensions: Array<String> = arrayOf("bsa", "ba2")

    var skippedTextures = mutableSetOf<String>()
    var WorkingDir = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mods)

        setSupportActionBar(findViewById(R.id.mods_toolbar))

        // Enable the "back" icon in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Switch tabs between plugins/resources
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {

                // Reload mod list when moving from data dir tab
                if(flipper.displayedChild == 2) {
                    saveCFG()
                    updateModList()
                }

                flipper.displayedChild = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })

        // Add Data Files and default plugins when missing
        val base = File(Constants.USER_OPENMW_CFG).readText()
        val gameDir = PreferenceManager.getDefaultSharedPreferences(this).getString("game_files", "")
        if (!base.contains(gameDir + "/Data Files")) {
            dataDirsList.add(Pair(gameDir + "/Data Files", true))
            archiveList.add(Pair("Morrowind.bsa", true))
            archiveList.add(Pair("Tribunal.bsa", true))
            archiveList.add(Pair("Bloodmoon.bsa", true))
            contentList.add(Pair("Morrowind.esm", true))
            contentList.add(Pair("Tribunal.esm", true))
            contentList.add(Pair("Bloodmoon.esm", true))
            File(Constants.USER_OPENMW_CFG).writeText("data=" + gameDir + "/Data Files\ncontent=Morrowind.esm\ncontent=Tribunal.esm\ncontent=Bloodmoon.esm\nfallback-archive=Morrowind.bsa\nfallback-archive=Tribunal.bsa\nfallback-archive=Bloodmoon.bsa\n" + base)
        }

        parseCFG(Pair("", false))

        // Set up adapters for the lists
        setupModList(findViewById(R.id.list_mods), ModType.Plugin)
        setupModList(findViewById(R.id.list_resources), ModType.Resource)
        setupModList(findViewById(R.id.list_dirs), ModType.Dir)
        setupModList(findViewById(R.id.list_groundcovers), ModType.Groundcover)

        val context = getApplicationContext()
        val applicationInfo = context.applicationInfo
        WorkingDir = applicationInfo.nativeLibraryDir
    }

    override fun onDestroy() {
        saveCFG()
        super.onDestroy()
    }

    private fun parseCFG(addedDir: Pair<String, Boolean>) {
        fallbackList.clear()
        archiveList.clear()
        dataDirsList.clear()
        contentList.clear()
        groundcoverList.clear()
        fsPlugins.clear()
        fsArchives.clear()

        File(Constants.USER_OPENMW_CFG).useLines {
	    lines -> lines.forEach {
                if (it.contains("fallback=") && !it.contains("#"))
                    fallbackList.add(it)
                if (it.contains("fallback-archive=") && !it.contains("#"))
                    archiveList.add(Pair(it.substringAfter("="), if (it.contains(";fallback-archive=")) false else true))
                if (it.contains("data=") && !it.contains("#")) {
                    val path = it.substringAfter("=").replace("\"", "").replace("\\", "/")
                    if (File(path).exists())
                        dataDirsList.add(Pair(path, if (it.contains(";data=")) false else true))
                }
                if (it.contains("content=") && !it.contains("#"))
                    contentList.add(Pair(it.substringAfter("="), if (it.contains(";content=")) false else true))
                if (it.contains("groundcover=") && !it.contains("#"))
                    groundcoverList.add(Pair(it.substringAfter("="), if (it.contains(";groundcover=")) false else true))
            }
        }


        val gameDir = PreferenceManager.getDefaultSharedPreferences(this).getString("game_files", "")
        val dataDirsNames = mutableSetOf<String>()
        dataDirsList.forEach { dataDirsNames.add(it.first) }

        File(gameDir).listFiles()?.forEach {
            if (!it.isFile() && !dataDirsNames.contains(gameDir + "/" + it.name) && it.name != "Data Files")
                dataDirsList.add(Pair(gameDir + "/" + it.name, false))
        }

        if (addedDir.second == true)
            dataDirsList.add(addedDir)

        dataDirsList.forEach {
            val enabled = it.second

            if (enabled) {
                File(it.first).listFiles()?.forEach {
                    if (pluginExtensions.contains(it.extension.toLowerCase()))
                        fsPlugins.add(it.name)
                    else if (archiveExtensions.contains(it.extension.toLowerCase()))
                        fsArchives.add(it.name)
                }
            }
        }
    }

    private fun saveCFG() {
        var output: String = ""

        fallbackList.forEach {
            output += it + "\n"
        }

        mDirAdapter.collection.mods.forEach {
        if (it.enabled)
            output += "data=" + it.filename + "\n"
        else
            output += ";data=" + it.filename + "\n"
        }

        mResourceAdapter.collection.mods.forEach {
        if (it.enabled)
            output += "fallback-archive=" + it.filename + "\n"
        else
            output += ";fallback-archive=" + it.filename + "\n"
        }

        mPluginAdapter.collection.mods.forEach {
        if (it.enabled)
            output += "content=" + it.filename + "\n"
        else
            output += ";content=" + it.filename + "\n"
        }

        mGroundcoverAdapter.collection.mods.forEach {
        if (it.enabled)
            output += "groundcover=" + it.filename + "\n"
        else
            output += ";groundcover=" + it.filename + "\n"
        }

        val delta_enabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("delta_enabled", false)
        val groundcoverify_enabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("groundcoverify_enabled", false)

        if (delta_enabled == true || groundcoverify_enabled == true)
            output += "data=" + Constants.USER_FILE_STORAGE + "launcher/delta\n"

        if (delta_enabled == true)
            output += "content=delta-merged.omwaddon\n"

        if (groundcoverify_enabled == true) {
            output += "content=output_deleted.omwaddon\n"
            output += "groundcover=output_groundcover.omwaddon\n"
        }

        File(Constants.USER_OPENMW_CFG).writeText(output)

        val currentPreset = PreferenceManager.getDefaultSharedPreferences(this).getString("modCollection", "Default")!!
        File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/" + currentPreset).writeText(output)
    }

    private fun updateModList() {
        parseCFG(Pair("", false))

        mPluginAdapter.collection = ModsCollection(ModType.Plugin, dataDirsList, contentList, groundcoverList, fsPlugins, fsArchives)
        mResourceAdapter.collection = ModsCollection(ModType.Resource, dataDirsList, archiveList, groundcoverList, fsPlugins, fsArchives)
        mDirAdapter.collection = ModsCollection(ModType.Dir, dataDirsList, dataDirsList, groundcoverList, fsPlugins, fsArchives)
        mGroundcoverAdapter.collection = ModsCollection(ModType.Groundcover, dataDirsList, groundcoverList, groundcoverList, fsPlugins, fsArchives)

        mPluginAdapter.notifyDataSetChanged()
        mResourceAdapter.notifyDataSetChanged()
        mDirAdapter.notifyDataSetChanged()
        mGroundcoverAdapter.notifyDataSetChanged()
    }

    /**
     * Connects a user-interface RecyclerView to underlying mod data on the disk
     * @param list The list displayed to the user
     * @param type Type of the mods this list will contain
     */
    private fun setupModList(list: RecyclerView, type: ModType) {

        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.orientation = RecyclerView.VERTICAL
        list.layoutManager = linearLayoutManager

	if (type == ModType.Plugin) {
	    mPluginAdapter.collection = ModsCollection(type, dataDirsList, contentList, groundcoverList, fsPlugins, fsArchives)
            val callback = ModMoveCallback(mPluginAdapter, mPluginAdapter, mGroundcoverAdapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(list)
            mPluginAdapter.touchHelper = touchHelper
            list.adapter = mPluginAdapter
	}
	else if (type == ModType.Resource) {
	    mResourceAdapter.collection = ModsCollection(type, dataDirsList, archiveList, groundcoverList, fsPlugins, fsArchives)
            val callback = ModMoveCallback(mResourceAdapter, mPluginAdapter, mGroundcoverAdapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(list)
            mResourceAdapter.touchHelper = touchHelper
            list.adapter = mResourceAdapter
        }
        else if (type == ModType.Dir){
	    mDirAdapter.collection = ModsCollection(type, dataDirsList, dataDirsList, groundcoverList, fsPlugins, fsArchives)
            val callback = ModMoveCallback(mDirAdapter, mPluginAdapter, mGroundcoverAdapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(list)
            mDirAdapter.touchHelper = touchHelper
            list.adapter = mDirAdapter
        }
	else if (type == ModType.Groundcover){ 
	    mGroundcoverAdapter.collection = ModsCollection(type, dataDirsList, groundcoverList, groundcoverList, fsPlugins, fsArchives)
            val callback = ModMoveCallback(mGroundcoverAdapter, mPluginAdapter, mGroundcoverAdapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(list)
            mGroundcoverAdapter.touchHelper = touchHelper
            list.adapter = mGroundcoverAdapter
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val inflater = menuInflater
        inflater.inflate(R.menu.mod_manager_settings, menu)

        return super.onPrepareOptionsMenu(menu)
    }

    private fun shellExec(cmd: String? = null, WorkingDir: String? = null): String {
        val output = StringBuilder()

        try {
            val processBuilder = ProcessBuilder()
            if (WorkingDir != null) {
                processBuilder.directory(File(WorkingDir))
            }
            System.setProperty("HOME", "/data/data/$packageName/files/")
            val commandToExecute = arrayOf("/system/bin/sh", "-c", "export HOME=/data/data/$packageName/files/; LD_LIBRARY_PATH=. $cmd")
            processBuilder.command(*commandToExecute)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            process.inputStream.bufferedReader().use { inputStreamReader ->
                var line: String?
                while (inputStreamReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }

            process.waitFor()
        } catch (e: Exception) {

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            output.append("Error executing command: ").append(e.message).append("\nStacktrace:\n").append(sw.toString())

        }

        return output.toString()
    }

    private fun runDeltaCommand(command: String, message: String, logfile: String) {
        val output = StringBuilder()

        // Get the Application Context
        val context = getApplicationContext()

        val deltaConfigFile = File(Constants.USER_OPENMW_CFG)

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage(message) // Set the message
        progressDialog.setCancelable(false) // Set cancelable to false
        progressDialog.show() // Show the ProgressDialog

        // Execute the command in a separate thread
        Thread {
            val output = shellExec(command, WorkingDir)
            File(logfile).writeText(output)
            runOnUiThread {
                progressDialog.dismiss()
            }
        }.start()
    }

    private fun runGroundcoverify() {
        val grassIds = "grass|kelp|lilypad|fern|thirrlily|spartium|in_cave_plant|reedgroup"
        val excludeIds = "refernce|infernace|planter|_furn_|_skelp|t_glb_var_skeleton|cliffgrass|terr|grassplane|flora_s_m_10_grass|cave_mud_rocks_fern|ab_in_cavemold|rp_mh_rock|ex_cave_grass00|secret_fern"
        val ids_expr = "^(?!.*($excludeIds).*).*($grassIds).*$"
        val exteriorCellRegex = "^[0-9\\-]+x[0-9\\-]+$"

        val command = "./libdelta_plugin.so -v --verbose -c " +
            Constants.USER_OPENMW_CFG + " filter --all --output " +
            Constants.USER_FILE_STORAGE + "/launcher/delta/output_groundcover.omwaddon --desc \"Generated groundcover plugin from your local cavebros\" match Cell --cellref-object-id \"$ids_expr\" --id \"$exteriorCellRegex\" match Static --id \"$ids_expr\" --modify model \"^\" \"grass\\\\\"" +
            " && " +
            "./libdelta_plugin.so -v --verbose -c " + Constants.USER_OPENMW_CFG + " filter --all --output " + Constants.USER_FILE_STORAGE + "/launcher/delta/output_deleted.omwaddon match Cell --cellref-object-id \"$ids_expr\" --id \"$exteriorCellRegex\" --delete" +
            " && " +
            "./libdelta_plugin.so -v --verbose -c " + Constants.USER_OPENMW_CFG + " query --input " + Constants.USER_FILE_STORAGE + "/launcher/delta/output_groundcover.omwaddon --ignore " + Constants.USER_FILE_STORAGE + "/launcher/delta/deleted_groundcover.omwaddon match Static"


        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Running Groundcoverify...") // Set the message
        progressDialog.setCancelable(false) // Set cancelable to false
        progressDialog.show() // Show the ProgressDialog

        Thread {
            val output = shellExec(command.toString(), WorkingDir)
            val outputlines = output.split("\n")
            val modelLines = outputlines.filter { it.trim().startsWith("model:") }
            val paths = modelLines.map { it.substringAfter("model: \"grass").replace("\\\\", "/").trim().replace("\"", "") }
            File(Constants.USER_FILE_STORAGE + "/launcher/delta/groundcoverify.log").writeText(output)

            File(Constants.USER_FILE_STORAGE + "/launcher/delta/paths.log").writeText(paths.toString())
            paths.forEach { path ->
                val filename = path.substringAfterLast("/")
                val correctedPath = path.substringBeforeLast("/").trim()

                val command2 = "mkdir -p " + Constants.USER_FILE_STORAGE + "/launcher/delta/Meshes/grass/$correctedPath" +
                    " && " +
                    "./libdelta_plugin.so -v --verbose -c " +
                    Constants.USER_OPENMW_CFG + " vfs-extract \"Meshes$correctedPath/$filename\" " +
                    Constants.USER_FILE_STORAGE + "/launcher/delta/Meshes/grass/$correctedPath/$filename"

                shellExec(command2.toString(), WorkingDir)
            }

            runOnUiThread {
                progressDialog.dismiss()
            }

        }.start()
    }

    private fun setSetting(setting: String, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        with(prefs.edit()) {
            putBoolean(setting, enabled)
            apply()
        }
    }

    private fun generateDeltaCFG() {
        val lines = File(Constants.USER_OPENMW_CFG).readLines().toMutableList()
        lines.removeAll { 
            it.contains("content=delta-merged.omwaddon") ||
            it.contains("groundcover=output_groundcover.omwaddon") ||
            it.contains("content=output_deleted.omwaddon") 
        }
        File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg").writeText(lines.joinToString("\n"))
    }

    fun convertTexture(dir: String, file: String, WorkingDir: String, quality: String, blockSize: String, message: String, progressDialog: ProgressDialog, hashFile: File, failFile: File) {

        runOnUiThread {
            progressDialog.setMessage(message)
        }

        val outputExtension = ".ktx"
        val ktxTexturesDir = Constants.USER_FILE_STORAGE + "launcher/ktx"
        val ktxTempTexture = ('"' + Constants.USER_FILE_STORAGE + "launcher/ktx/tmp_" + file.substringAfterLast("/").replace(".dds", ".ktx") + '"').toLowerCase()

        val inputFile = '"' + dir + file + '"'
        val outputFile = '"' + ktxTexturesDir + file.replace(".dds", outputExtension) + '"'
        var encodeCmd = "./libkram.so encode -f $blockSize -encoder astcenc -quality $quality -i $inputFile -o $outputFile"

        val info = shellExec("./libkram.so info -i $inputFile -v", WorkingDir)
        val dims = info.substringAfter("dims: ").substringBefore("\n")
        val width = dims.substringBefore("x").toIntOrNull()
        val height = dims.substringAfter("x").substringBefore("\n").toIntOrNull()
        val format = info.substringAfter("fmtk: ").substringBefore("\n")

        var minSize = 16
        if (format == "EXPrgb8") minSize = 64
        var isSmall = false

        if (width != null && height != null && width > minSize && height > minSize) {
            if (format != "EXPrgb8" && format != "EXPrgba8") {
                encodeCmd = "./libkram.so encode -f $blockSize -encoder astcenc -quality $quality -i $ktxTempTexture -o $outputFile"
                shellExec("./libkram.so decode -i $inputFile -o $ktxTempTexture", WorkingDir)
            }

            shellExec(encodeCmd, WorkingDir)
        }
        else
        {
            if (File(ktxTexturesDir + file.replace(".dds", outputExtension).replace(".DDS", outputExtension)).exists())
                File(ktxTexturesDir + file.replace(".dds", outputExtension).replace(".DDS", outputExtension)).delete()
            isSmall = true
        }

        if (File(ktxTexturesDir + file.replace(".dds", outputExtension).replace(".DDS", outputExtension)).exists()) {
            hashFile.appendText(file.toLowerCase() + "@" + dir + "#" + File(dir + file).hashCode().toString() + "\n") 
        }
        else {
                if (!isSmall)
                    File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/failedList.log").appendText("\n" + info)
        }

        File(ktxTempTexture.replace("\"", "")).delete()
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

            R.id.action_add_mod -> {

                val chooser = StorageChooser.Builder()
                    .withActivity(this)
                    .withFragmentManager(fragmentManager)
                    .withMemoryBar(true)
                    .allowCustomPath(true)
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build()

                chooser.show()
                chooser.setOnSelectListener { path -> addMod(path) }
                true
            }

            R.id.action_mods_preset -> {
                var modPresets = arrayOf("Default")
                val currentPreset = PreferenceManager.getDefaultSharedPreferences(this).getString("modCollection", "Default")!!
                var currentPresetLocation = 0
                var counter = 1

                File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections").listFiles().forEach {  
                    if (it.isFile() && it.getName() != "Default") {
                        modPresets += it.getName()
                        if (it.getName() == currentPreset) currentPresetLocation = counter
                        counter += 1
                    }
                }

                AlertDialog.Builder(this)
                .setTitle("Choose content list")
                .setSingleChoiceItems(modPresets, currentPresetLocation) { dialog, which ->
                    saveCFG()

                    val path = Constants.USER_FILE_STORAGE + "/launcher/ModCollections/" + modPresets[which]
                    File(path).copyTo(File(Constants.USER_OPENMW_CFG), true)

                    val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                    with(sharedPref.edit()) {
                        putString("modCollection", modPresets[which])
                       apply()
                    }
                    updateModList()
                    dialog.dismiss()
                }
                .setNegativeButton("New") { dialog, which -> 
                    val textInputLayout = TextInputLayout(this)
                    textInputLayout.setPadding(19, 0, 19, 0)
                    val input = EditText(this)
                    textInputLayout.addView(input)

                    val alert = AlertDialog.Builder(this)
                        .setTitle("Create content list")
                        .setView(textInputLayout)
                        .setMessage("Select name.")
                        .setPositiveButton("Create") { dialog, _ ->
                            if (input.text.toString() != "") {

                                saveCFG()
                                val path = Constants.USER_FILE_STORAGE + "/launcher/ModCollections/" + input.text.toString()
                                val gameDir = PreferenceManager.getDefaultSharedPreferences(this).getString("game_files", "")
                                File(path).writeText("data=" + gameDir + "/Data Files\ncontent=Morrowind.esm\ncontent=Tribunal.esm\ncontent=Bloodmoon.esm\nfallback-archive=Morrowind.bsa\nfallback-archive=Tribunal.bsa\nfallback-archive=Bloodmoon.bsa\n")
                                File(path).copyTo(File(Constants.USER_OPENMW_CFG), true)

                                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                                with(sharedPref.edit()) {
                                    putString("modCollection", input.text.toString())
                                    apply()
                                }
                                updateModList()
                            }
                            dialog.cancel()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.cancel()
                    }.show()
                }
                .setNeutralButton("delete") { dialog, which -> 
                    if (currentPreset != "Default") AlertDialog.Builder(this)
                        .setTitle("Delete current content list")
                        .setMessage("Do you want to delete " + currentPreset + " content list?")
                        .setPositiveButton("Yes") { dialog, _ ->
                            val path = Constants.USER_FILE_STORAGE + "/launcher/ModCollections/Default"
                            File(path).copyTo(File(Constants.USER_OPENMW_CFG), true)

                            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                            with(sharedPref.edit()) {
                                putString("modCollection", "Default")
                                apply()
                            }
                            updateModList()
                            File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/" + currentPreset).delete()
                            dialog.cancel()
                        }
                        .setNegativeButton("No") { dialog, _ ->
                            dialog.cancel()
                    }.show()
                }
                .setPositiveButton("Cancel") { dialog, which -> }
                .show()

                true
            }

            R.id.action_tools_delta_merge -> {
                saveCFG()
                generateDeltaCFG() 
                val command = "./libdelta_plugin.so -v --verbose -c " + 
                    Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg" + " merge --skip-cells " + 
                    Constants.USER_FILE_STORAGE + "/launcher/delta/delta-merged.omwaddon"

                var isGenerated = false
                var isEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("delta_enabled", false)
                if (File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta-merged.omwaddon").exists())
                    isGenerated = true

                var oldHash = ""
                if (File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.hash").exists())
                    oldHash = File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.hash").readText()
                val newHash = File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg").readText().hashCode().toString()

                var message = "Delta plugin is "
                if (!isGenerated) message += "not generated."
                else if (!isEnabled) message += "disabled."
                else message += "enabled."

                if (isGenerated && oldHash != newHash && isEnabled)
                    message += "\n\nAnd may be outdated!"
                else if (isGenerated && isEnabled)
                    message += "\n\nAnd is up to date."
            
                AlertDialog.Builder(this)
                    .setTitle("Delta Merge")
                    .setMessage(message)
                    .setNeutralButton(if (isGenerated) "Re-Generate" else "Generate") { _, _ ->
                        File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.hash").writeText(newHash)
                        setSetting("delta_enabled", false)
                        runDeltaCommand(command, "Running Delta Plugin...", Constants.USER_FILE_STORAGE + "/launcher/delta/delta.log")
                        setSetting("delta_enabled", true)

                    }
                    .setPositiveButton("Cancel") { _, _ ->
                    }
                    .setNegativeButton(if (!isGenerated) "" else if (isEnabled) "Disable" else "Enable") { _, _ ->
                        if (isGenerated)
                            setSetting("delta_enabled", if (isEnabled == true) false else true)
                    }
                    .show()

                true
            }

            R.id.action_tools_groundcoverify -> {
                saveCFG()
                generateDeltaCFG() 
                var isGenerated = false
                var isEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("groundcoverify_enabled", false)
                if (File(Constants.USER_FILE_STORAGE + "/launcher/delta/output_deleted.omwaddon").exists() &&
                    File(Constants.USER_FILE_STORAGE + "/launcher/delta/output_groundcover.omwaddon").exists())
                    isGenerated = true

                var oldHash = ""
                if (File(Constants.USER_FILE_STORAGE + "/launcher/delta/groundcoverify.hash").exists())
                    oldHash = File(Constants.USER_FILE_STORAGE + "/launcher/delta/groundcoverify.hash").readText()
                val newHash = File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg").readText().hashCode().toString()

                var message = "Groundcoverify is "
                if (!isGenerated) message += "not generated!"
                else if (!isEnabled) message += "disabled."
                else message += "enabled."

                if (isGenerated && oldHash != newHash && isEnabled)
                    message += "\n\nAnd may be outdated."
                else if (isGenerated && isEnabled)
                    message += "\n\nAnd is up to date."

           
                AlertDialog.Builder(this)
                    .setTitle("Groundcoverify")
                    .setMessage(message)
                    .setNeutralButton(if (isGenerated) "Re-Generate" else "Generate") { _, _ ->
                        File(Constants.USER_FILE_STORAGE + "/launcher/delta/groundcoverify.hash").writeText(newHash)
                        setSetting("groundcoverify", false)
                        runGroundcoverify()
                        setSetting("groundcoverify", true)

                    }
                    .setPositiveButton("Cancel") { _, _ ->
                    }
                    .setNegativeButton(if (!isGenerated) "" else if (isEnabled) "Disable" else "Enable") { _, _ ->
                        if (isGenerated)
                            setSetting("groundcoverify_enabled", if (isEnabled == true) false else true)
                    }
                    .show()

                true
            }
            R.id.action_tools_tex_convert-> {

                saveCFG()

                File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs").mkdirs()
                File(Constants.USER_FILE_STORAGE + "/launcher/ktx/bsatool").mkdirs()

                val progressDialog = ProgressDialog(this)
                progressDialog.setMessage("Getting Info...")
                progressDialog.setCancelable(false)
                progressDialog.getWindow()?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                var pathsList = mutableSetOf<String>()
                var texturesList = mutableSetOf<String>()
                var requiredList = mutableSetOf<String>()
                var bsaList = mutableSetOf<String>()
                var skipList = mutableSetOf<String>()

                val hashMap = HashMap<String, String>()

                val dialog = AlertDialog.Builder(this)
                dialog.setCancelable(false)
                dialog.setTitle("ASTC Compressor")

                val scrollView = ScrollView(this)
                var scrollParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                scrollView.layoutParams = scrollParams

                val layout = LinearLayout(this)
                layout.orientation = LinearLayout.VERTICAL

                val requiredText = TextView(this)
                requiredText.setText("Need Filecheck First.")
                layout.addView(requiredText)

                val convertAll = CheckBox(this)
                convertAll.setText("Convert All Textures")
                convertAll.setChecked(false)
                layout.addView(convertAll)

                val blockSizeText = TextView(this)
                blockSizeText.setText("Block Size (12x12 = Highest Compression, 4x4 = Best Quality.")
                layout.addView(blockSizeText)

                val spinnerArray = ArrayList<String>();
                spinnerArray.add("astc12x12 - 0.89bpp");
                spinnerArray.add("astc10x10 - 1.28bpp");
                spinnerArray.add("astc8x8 - 2.0bpp");
                spinnerArray.add("astc6x6 - 3.56bpp");
                spinnerArray.add("astc5x5 - 5.12bpp");
                spinnerArray.add("astc4x4 - 8.0bpp");

                val spinner = Spinner(this);
                val spinnerArrayAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerArray);
                spinner.setAdapter(spinnerArrayAdapter);
                spinner.setSelection(2, true)
                layout.addView(spinner);

                val qualityText = TextView(this)
                qualityText.setText("Quality")
                layout.addView(qualityText)

                val qualitySeekBar = SeekBar(this)
                qualitySeekBar.setProgress(75)
                qualitySeekBar.setMin(0)
                qualitySeekBar.setMax(100)
                layout.addView(qualitySeekBar)
                layout.setPadding(50, 40, 50, 10)

                scrollView.addView(layout)
                dialog.setView(scrollView)


                fun collectInfo() {
                    pathsList.clear()
                    texturesList.clear()
                    requiredList.clear()
                    skipList.clear()

                    if (File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/textureHashes.log").exists()) {
                        File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/textureHashes.log").useLines {
	                    lines -> lines.forEach {
                                hashMap[it.substringBefore("@").toLowerCase()] = it.substringAfter("@")
                            }
                        }
                    }

                    val mMods = arrayListOf<Mod>()
                    mMods.add(Mod(ModType.Dir, Constants.USER_FILE_STORAGE + "launcher/ktx/bsatool", 0, true))
                    mDirAdapter.collection.mods.forEach { mMods.add(it) }

                    mMods.reversed().forEach {
                        if (it.enabled) {
                            val dir = it.filename
                            File(dir).walk()?.forEach {
                                if (it.isFile() && (it.extension.toLowerCase().contains("dds"))) {
                                    val fileName = it.getAbsolutePath().toString().replace(dir, "")
                                    val hash = it.hashCode().toString()
                                    if (!texturesList.contains(fileName.toLowerCase())) {
                                        texturesList.add(fileName.toLowerCase())

                                        if (hashMap[fileName.toLowerCase()] != "$dir#$hash") {
                                            if (!fileName.toLowerCase().contains("menu") && !it.getAbsolutePath().toLowerCase().contains("shaders/"))
                                                requiredList.add("$fileName@$dir")
                                            else
                                                skipList.add(fileName)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    //File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/textures.log").writeText(texturesList.joinToString("\n")) 
                    File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/requiredPaths.log").writeText(requiredList.joinToString("\n"))
                    //File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/bsaList.log").writeText(bsaList.joinToString("\n")) 
                    //File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/skipList.log").writeText(skipList.joinToString("\n")) 

                    runOnUiThread {
                        requiredText.setText("Want to convert ${requiredList.size} textures.\n")
                        progressDialog.dismiss()
                    }
                }

                dialog.setPositiveButton("Convert") { dialog, _ ->
                    runOnUiThread { progressDialog.show() }
                    Thread {
                        File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/failedList.log").writeText("")

                        skipList.forEach {
                            if (File((Constants.USER_FILE_STORAGE + "/launcher/ktx$it").replace(".dds", ".ktx")).exists())
                                File((Constants.USER_FILE_STORAGE + "/launcher/ktx$it").replace(".dds", ".ktx")).delete()
                        }

                        if (convertAll.isChecked()) {
                            if (File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/textureHashes.log").exists())
                                File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/textureHashes.log").delete()

                            hashMap.clear()
                            collectInfo()
                            runOnUiThread { progressDialog.show() }
                        }

                        val quality = qualitySeekBar.getProgress().toString()
                        val blockSize = spinner.getSelectedItem().toString().substringBefore(" -")

                        runBlocking {
                            var counter = 0
                            var hashList = mutableSetOf<String>()
                            for((key, value) in hashMap) {
                                hashList.add(key + "@" + value)
                            }

                            val hashFile = File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/textureHashes.log")
                            hashFile.writeText(hashList.joinToString("\n"))
                            val failFile = File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/failedList.log")
                            failFile.writeText("")

                            requiredList.forEach {
                                val dir = it.substringAfter("@")
                                val file = it.substringBefore("@")

                                val message = "${counter.toString()}/${requiredList.size.toString()}\n$dir$file"
                                launch(Dispatchers.Default) {
                                        convertTexture(dir, file, WorkingDir, quality, blockSize, message, progressDialog, hashFile, failFile)
                                }
                                counter++
                            }
                        }

                        runOnUiThread {
                            progressDialog.dismiss()
                        }
                    }.start()
                }

                dialog.setNeutralButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }

                val BSAdialog = AlertDialog.Builder(this)
                BSAdialog.setCancelable(false)
                BSAdialog.setTitle("Extract Archives.")

                val BSAlayout = LinearLayout(this)
                BSAlayout.orientation = LinearLayout.VERTICAL
                BSAlayout.setPadding(50, 40, 50, 10)

                val archiveText = TextView(this)
                BSAlayout.addView(archiveText)
                BSAdialog.setView(BSAlayout)

                BSAdialog.setPositiveButton("Extract") { dialog, _ ->
                    var counter = 0
                    Thread {
                        bsaList.forEach {
                            runOnUiThread {
                                progressDialog.setMessage("Extracting Archive:\n" + it)
                            }

                            val bsaPath = '"' + it + '"' + " " + '"' + Constants.USER_FILE_STORAGE + "/launcher/ktx/bsatool" + '"'
                            shellExec("./libbsatool.so extractall $bsaPath", WorkingDir)

                            File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/extractedArchives.log").writeText(File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/extractedArchives.log").readText() + it + "\n")

                            if (++counter == bsaList.size) {
                                runOnUiThread {
                                    progressDialog.setMessage("Getting info....")
                                }

                                collectInfo()
                            }

                        }
                    }.start()
                }

                BSAdialog.setNeutralButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    collectInfo()
                }

                var needExtract = false
                if (!File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/extractedArchives.log").exists())
                    File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/extractedArchives.log").writeText("")

                val bsas = File(Constants.USER_FILE_STORAGE + "/launcher/ktx/logs/extractedArchives.log").readText()
                mResourceAdapter.collection.mods.forEach {
                    if (it.enabled) {
                        val resourceName = it.filename
                        mDirAdapter.collection.mods.reversed().forEach {
                            if (it.enabled && File(it.filename + "/" + resourceName).exists()) {
                                val directoryName = it.filename
                                val fullPath = directoryName + "/" + resourceName
                                bsaList.add(fullPath)
                                if (!bsas.contains(fullPath))
                                    needExtract = true
                            }
                        }
                    }
                }

                archiveText.setText(bsaList.joinToString("\n").replace("/storage/emulated/0/", ""))

                runOnUiThread {
                    dialog.show()
                    progressDialog.show()

                    if (needExtract == true)
                        BSAdialog.show()
                    else {
                        Thread {
                            progressDialog.setMessage("Getting info....")
                            collectInfo() 
                        }.start()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addMod(path: String) {
        saveCFG()
        parseCFG(Pair(path, true))

        mPluginAdapter.collection = ModsCollection(ModType.Plugin, dataDirsList, contentList, groundcoverList, fsPlugins, fsArchives)
        mResourceAdapter.collection = ModsCollection(ModType.Resource, dataDirsList, archiveList, groundcoverList, fsPlugins, fsArchives)
        mDirAdapter.collection = ModsCollection(ModType.Dir, dataDirsList, dataDirsList, groundcoverList, fsPlugins, fsArchives)
        mGroundcoverAdapter.collection = ModsCollection(ModType.Groundcover, dataDirsList, groundcoverList, groundcoverList, fsPlugins, fsArchives)

        mPluginAdapter.notifyDataSetChanged()
        mResourceAdapter.notifyDataSetChanged()
        mDirAdapter.notifyDataSetChanged()
        mGroundcoverAdapter.notifyDataSetChanged()
    }
}
