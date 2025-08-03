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
import constants.Constants
import java.io.File

/**
 * Represents an ordered list of mods of a specific type
 * @param type Type of the mods represented by this collection, Plugin or Resource
 * @param dataFiles Path to the directory of the mods (the Data Files directory)
 */
class ModsCollection(private val type: ModType,
                     private var dirList: MutableList<Pair<String, Boolean>>,
                     private var modList: MutableList<Pair<String, Boolean>>,
                     private var groundcoverList: MutableList<Pair<String, Boolean>>,
                     private var fsPlugins: MutableSet<String>,
                     private var fsArchives: MutableSet<String>) {

    val mods = arrayListOf<Mod>()

    init {
        syncWithFs(type)
    }

    /**
     * Synchronizes state of mods in database with the actual mod files on disk
     * This could result in it deleting or adding mods to the database.
     */
    private fun syncWithFs(type: ModType) {
        val blacklist = mutableSetOf<String>()

        blacklist.add(Constants.USER_FILE_STORAGE + "launcher/delta")
        blacklist.add("delta-merged.omwaddon")
        blacklist.add("output_deleted.omwaddon")
        blacklist.add("output_groundcover.omwaddon")

        // Figure current maximum order, new mods will be pushed below it
        var maxOrder = mods.maxBy { it.order }?.order ?: 0

        // Add all mods from openmw.cfg that exists on filesystem in its order
        modList.forEach {
            if ((type == ModType.Dir || fsPlugins.contains(it.first) || fsArchives.contains(it.first)) && !blacklist.contains(it.first)) {
                maxOrder += 1
                val mod = Mod(type, it.first, maxOrder, it.second)
                mods.add(mod)
            }
        }

        // Add mods not specified in openmw.cfg but existing on filesystem at end of modlist
        if (type != ModType.Dir && type != ModType.Groundcover) {
            val cfgNames = mutableSetOf<String>()

            modList.forEach { cfgNames.add(it.first) }
            if (type == ModType.Plugin) {
                var groundcovers = mutableSetOf<String>()
                groundcoverList.forEach { groundcovers.add(it.first) }
                fsPlugins.forEach {
                    if (!cfgNames.contains(it) && !groundcovers.contains(it) && !blacklist.contains(it)) {
                        maxOrder += 1
                        val mod = Mod(type, it, maxOrder, false)
                        mods.add(mod)
                    }
                }
            }

            if (type == ModType.Resource) {
                fsArchives.forEach {
                    if (!cfgNames.contains(it)) {
                        maxOrder += 1
                        val mod = Mod(type, it, maxOrder, false)
                        mods.add(mod)
                    }
                }
            }
        }

        // Sort the mods in order
        mods.sortBy { it.order }
    }
}
