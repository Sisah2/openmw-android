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

package mods

import org.jetbrains.anko.db.*
import java.io.File

/**
 * Represents an ordered list of mods of a specific type
 * @param type Type of the mods represented by this collection, Plugin or Resource
 * @param dataFiles Path to the directory of the mods (the Data Files directory)
 */
class ModsCollection(private val type: ModType,
                     private val dataFiles: ArrayList<String>,
                     private val db: ModsDatabaseOpenHelper) {

    val mods = arrayListOf<Mod>()
    private var extensions: Array<String> = if (type == ModType.Resource)
        arrayOf("bsa", "ba2")
    else if (type == ModType.Dir)
        arrayOf("")
    else
        arrayOf("esm", "esp", "omwaddon", "omwgame", "omwscripts")

    init {
        if (isEmpty())
            initDb()
        syncWithFs(type)
        // The database might have become empty (e.g. if user deletes all mods) after the FS sync
        if (isEmpty())
            initDb()
    }

    /**
     * Checks if the mod DB is empty, i.e. no mods defined yet. This can happen for example
     * on first startup
     * @return True if the DB doesn't have any mods
     */
    private fun isEmpty(): Boolean {
        var count = 0
        db.use {
            count = select("mod", "count(1)").exec {
                parseSingle(IntParser)
            }
        }
        return count == 0
    }

    /**
     * Inserts built-in mods into the database, in proper order.
     * Also checks to make sure only installed mods are inserted.
     */
    private fun initDb() {
        val builtIn = arrayOf("Morrowind", "Tribunal", "Bloodmoon")
        initDbMods(builtIn.map { "$it.esm" }, ModType.Plugin)
        initDbMods(builtIn.map { "$it.bsa" }, ModType.Resource)
    }

    /**
     * Inserts built-in mods of a specific mod type. All of the built-in mods will be enabled
     * by default.
     * @param files Filenames of the mods, including extensions
     * @param type Type of the mods (plugins/resources)
     */
    private fun initDbMods(files: List<String>, type: ModType) {
        var order = 0
	var counter = 0
	repeat(dataFiles.size) {
            db.use {
                files
                    .map { File(dataFiles.elementAt(counter), it) }
                    .filter { it.exists() }
                    .map { order += 1; Mod(type, it.name, order, true) }
                    .forEach { it.insert(this) }
            }
	    counter = counter +1
	}
    }

    /**
     * Synchronizes state of mods in database with the actual mod files on disk
     * This could result in it deleting or adding mods to the database.
     */
    private fun syncWithFs(type: ModType) {
        var dbMods = listOf<Mod>()

        // Get mods from the database
        db.use {
            select("mod", "type", "filename", "load_order", "enabled")
                .whereArgs("type = {type}", "type" to type.v).exec {
                    dbMods = parseList(ModRowParser())
                }
        }

        val fsNames = mutableSetOf<String>()
	var counter = 0

	repeat(dataFiles.size) {

     	   // Get file names matching the extensions
     	   var modFiles = File(dataFiles.elementAt(counter)).listFiles()?.filter {
     	       extensions.contains(it.extension.toLowerCase())
     	   }

           // Blacklist "Data Files" in Directories tab and default plugins in Groundcovers tab
           val blacklist = mutableSetOf<String>()
           if(type == ModType.Dir) {
               blacklist.add("Data Files")
           }

           if(type == ModType.Groundcover) {
               blacklist.add("Morrowind.esm")
               blacklist.add("Tribunal.esm")
               blacklist.add("Bloodmoon.esm")
               blacklist.add("adamantiumarmor.esp")
               blacklist.add("AreaEffectArrows.esp")
               blacklist.add("bcsounds.esp")
               blacklist.add("EBQ_Artifact.esp")
               blacklist.add("entertainers.esp")
               blacklist.add("LeFemmArmor.esp")
               blacklist.add("master_index.esp")
               blacklist.add("Siege at Firemoth.esp")
           }

     	   // Collect filenames of mods on the FS
      	   modFiles?.forEach {
     	       if(!blacklist.contains(it.name)) fsNames.add(it.name)
     	   }
     	   counter = counter + 1
	}

        // Collect filenames of mods in the DB
        val dbNames = mutableSetOf<String>()
        dbMods.forEach {
            dbNames.add(it.filename)
        }

        // Get mods which are both in DB and on FS
        dbMods.filter { fsNames.contains(it.filename) }.forEach {
            mods.add(it)
        }

        // Figure current maximum order, new mods will be pushed below it
        var maxOrder = mods.maxBy { it.order }?.order ?: 0

        // Create an entry for each mod that's on FS but not in DB and assign proper order
        val newMods = arrayListOf<Mod>()
        (fsNames - dbNames).forEach {
            maxOrder += 1
            val mod = Mod(type, it, maxOrder, false)
            newMods.add(mod)
            mods.add(mod)
        }

        // Commit changes to the database
        db.use {
            transaction {
                // Delete all mods which are in db but not on fs
                (dbNames - fsNames).forEach {
                    delete("mod",
                        "type = {type} AND filename = {filename}",
                        "type" to type.v,
                        "filename" to it)
                }

                // Create all mods which are on fs but not in db
                newMods.forEach { it.insert(this) }
            }
        }

        // Sort the mods in order
        mods.sortBy { it.order }
    }

    /**
     * Performs DB updates for all mods marked as dirty
     */
    fun update() {
        db.use {
            mods.filter { it.dirty }.forEach {
                it.update(this)
                it.dirty = false
            }
        }
    }
}
