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

package file

/**
 * Converts morrowind.ini to fallback= format
 * @param data Contents of morrowind.ini as a string
 */
class IniConverter(private val data: String) {

    /**
     * Performs the actual conversion
     * @return String contents of the output file in openmw's fallback format
     */
    fun convert(): String {
        var category = ""
        var output = ""

        val blacklist = mutableSetOf<String>()
            blacklist.add("Archives_Archive_0")
            blacklist.add("Archives_Archive_1")
            blacklist.add("CopyProtectionStrings_Enter_CD_Message")
            blacklist.add("CopyProtectionStrings_Enter_CD_Title")
            blacklist.add("CopyProtectionStrings_No_CDROM_Message")
            blacklist.add("CopyProtectionStrings_No_CDROM_Title")
            blacklist.add("Cursors_Cursor_0")
            blacklist.add("Cursors_Cursor_1")
            blacklist.add("Cursors_Cursor_2")
            blacklist.add("Cursors_Cursor_3")
            blacklist.add("Cursors_Cursor_4")
            blacklist.add("Editor_Open_Cell_View")
            blacklist.add("Editor_Open_Object_Window")
            blacklist.add("Editor_Open_Preview")
            blacklist.add("Editor_Open_Render")
            blacklist.add("Game_Files_GameFile0")
            blacklist.add("Game_Files_GameFile1")
            blacklist.add("Game_Files_GameFile2")
            blacklist.add("General_Background_Keyboard")
            blacklist.add("General_CanMoveInfosWhileFiltered")
            blacklist.add("General_Clip_One_To_One_Float")
            blacklist.add("General_Create_Maps_Enable")
            blacklist.add("General_Disable_Audio")
            blacklist.add("General_DontThreadLoad")
            blacklist.add("General_Editor_Starting_Cell")
            blacklist.add("General_Editor_Starting_Dir_0")
            blacklist.add("General_Editor_Starting_Dir_1")
            blacklist.add("General_Editor_Starting_Dir_2")
            blacklist.add("General_Editor_Starting_Pos")
            blacklist.add("General_ExportDialogueWithHyperlinks")
            blacklist.add("General_Exterior_Cell_Buffer")
            blacklist.add("General_Flip_Control_Y")
            blacklist.add("General_Interior_Cell_Buffer")
            blacklist.add("General_Joystick_Look_Left/Right")
            blacklist.add("General_Joystick_Look_Up/Down")
            blacklist.add("General_Joystick_X_Turns")
            blacklist.add("General_MaintainImportedDialogueOrder")
            blacklist.add("General_Max_FPS")
            blacklist.add("General_Maximum_Shadows_Per_Object")
            blacklist.add("General_Number_of_Shadows")
            blacklist.add("General_PC_Footstep_Volume")
            blacklist.add("General_Screen_Shot_Base_Name")
            blacklist.add("General_Screen_Shot_Enable")
            blacklist.add("General_Screen_Shot_Index")
            blacklist.add("General_ShowHitFader")
            blacklist.add("General_Show_FPS")
            blacklist.add("General_SkipKFExtraction")
            blacklist.add("General_SkipProgramFlows")
            blacklist.add("General_Subtitles")
            blacklist.add("General_ThreadPriority")
            blacklist.add("General_ThreadSleepTime")
            blacklist.add("General_TryArchiveFirst")
            blacklist.add("General_UseExistingTempFile")
            blacklist.add("General_Use_Joystick")
            blacklist.add("MenuStates_LastMenuUpWas")
            blacklist.add("MenuStates_MenuBarter")
            blacklist.add("MenuStates_MenuConsole")
            blacklist.add("MenuStates_MenuContents")
            blacklist.add("MenuStates_MenuDialog")
            blacklist.add("MenuStates_MenuInventory")
            blacklist.add("MenuStates_MenuInventory-MenuBarter")
            blacklist.add("MenuStates_MenuInventory-MenuContents")
            blacklist.add("MenuStates_MenuMagic")
            blacklist.add("MenuStates_MenuMap")
            blacklist.add("MenuStates_MenuRaceSex")
            blacklist.add("MenuStates_MenuSetValues")
            blacklist.add("MenuStates_MenuSkills-MenuCreateClass")
            blacklist.add("MenuStates_MenuSpellmaking")
            blacklist.add("MenuStates_MenuStat")
            blacklist.add("MenuStates_MenuStatReview")
            blacklist.add("PreLoad_Cell_0")
            blacklist.add("PreLoad_Cell_1")
            blacklist.add("Water_Editor_Alpha")

        data
            // Split into lines
            .lines()
            // Trim whitespace/newlines
            .map { it.trim() }
            // Remove comments and empty lines
            .filter { it.isNotEmpty() && !it.startsWith(";") }
            .forEach {
                if (it.startsWith("[") && it.endsWith("]")) {
                    // It's a category
                    category = it.substring(1, it.length - 1).replace(" ", "_")
                } else if (it.contains("=")) {
                    // It's a key-value pair
                    val converted = convertLine(it)
                    if (converted.isNotEmpty() && !blacklist.contains("${category}_$converted".substringBefore(",", "")))
                        output += "fallback=${category}_$converted\n"
                }
            }

        return output
    }

    /**
     * Converts a single morrowind setting line into openmw format
     * (replacing spaces with _ and = with ,)
     * @param line Line to convert
     * @return Converted result, note that this does not include the fallback=Category_ part
     */
    private fun convertLine(line: String): String {
        // key and value are separated by = in mw and by , in omw
        val kv = line.split("=".toRegex(), 2)
        val key = kv[0].replace(" ", "_")
        val value = kv[1]

        if (key.isEmpty() || value.isEmpty())
            return ""

        return "$key,$value"
    }
}
