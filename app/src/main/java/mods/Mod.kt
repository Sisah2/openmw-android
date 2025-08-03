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

enum class ModType(val v: Int) {
    Plugin(1),
    Resource(2),
    Dir(3),
    Groundcover(4);
}

/**
 * Representation of a single mod in the database
 * @param type Type of the mod: plugin or resource
 * @param filename Filename of the mod, without the path
 * @param order Load order, or order in the list
 * @param enabled Whether the mod is enabled
 */
class Mod(var type: ModType, val filename: String, var order: Int, var enabled: Boolean) {
}

