/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.zimmob.zimlx.popup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.view.View
import com.android.launcher3.*
import com.android.launcher3.ItemInfoWithIcon.FLAG_SYSTEM_YES
import com.android.launcher3.LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.popup.SystemShortcut
import com.google.android.apps.nexuslauncher.CustomBottomSheet
import org.zimmob.zimlx.hasFlag
import org.zimmob.zimlx.override.CustomInfoProvider
import org.zimmob.zimlx.util.ZimSingletonHolder
import org.zimmob.zimlx.zimPrefs
import java.net.URISyntaxException

class ZimShortcut(private val context: Context) {

    private val shortcuts = listOf(
        ShortcutEntry("dash", DashAdd(), false),
        ShortcutEntry("edit", Edit(), true),
        ShortcutEntry("info", SystemShortcut.AppInfo(), true),
        ShortcutEntry("widgets", SystemShortcut.Widgets(), true),
        ShortcutEntry("install", SystemShortcut.Install(), true),
        ShortcutEntry("remove", Remove(), false),
        ShortcutEntry("uninstall", Uninstall(), false)
    )

    inner class ShortcutEntry(key: String, val shortcut: SystemShortcut<*>, enabled: Boolean) {

        val enabled by context.zimPrefs.BooleanPref("pref_iconPopup_$key", enabled)
    }

    val enabledShortcuts get() = shortcuts.filter { it.enabled }.map { it.shortcut }

    class Uninstall : SystemShortcut<Launcher>(R.drawable.ic_uninstall_no_shadow, R.string.uninstall_drop_target_label) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
            if (itemInfo is ItemInfoWithIcon) {
                if (itemInfo.runtimeStatusFlags.hasFlag(FLAG_SYSTEM_YES)) {
                    return null
                }
            }

            return getUninstallTarget(launcher, itemInfo)?.let { cn ->
                View.OnClickListener {
                    AbstractFloatingView.closeAllOpenViews(launcher)
                    try {
                        val i = Intent.parseUri(launcher.getString(R.string.delete_package_intent), 0)
                            .setData(Uri.fromParts("package", cn.packageName, cn.className))
                            .putExtra(Intent.EXTRA_USER, itemInfo.user)
                        launcher.startActivity(i)
                    } catch (e: URISyntaxException) {
                    }
                }
            }
        }

        private fun getUninstallTarget(launcher: Launcher, item: ItemInfo): ComponentName? {
            if (item.itemType == ITEM_TYPE_APPLICATION && item.id == ItemInfo.NO_ID.toLong()) {
                val intent = item.intent
                val user = item.user
                if (intent != null) {
                    val info = LauncherAppsCompat.getInstance(launcher).resolveActivity(intent, user)
                    if (info != null && !info.applicationInfo.flags.hasFlag(ApplicationInfo.FLAG_SYSTEM)) {
                        return info.componentName
                    }
                }
            }
            return null
        }
    }

    class Remove : SystemShortcut<Launcher>(R.drawable.ic_remove_no_shadow, R.string.remove_drop_target_label) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
            if (itemInfo.id == NO_ID.toLong()) return null
            return if (itemInfo is ShortcutInfo || itemInfo is LauncherAppWidgetInfo || itemInfo is FolderInfo) {
                View.OnClickListener {
                    AbstractFloatingView.closeAllOpenViews(launcher)

                    val currentPage = launcher.workspace.currentPage

                    launcher.removeItem(null, itemInfo, true /* deleteFromDb */)
                    launcher.model.forceReload(currentPage)
                    launcher.workspace.stripEmptyScreens()
                }
            } else null
        }
    }

    class Edit : SystemShortcut<Launcher>(R.drawable.ic_edit_no_shadow, R.string.action_preferences) {

        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
            if (launcher.zimPrefs.lockDesktop) return null
            if (!CustomInfoProvider.isEditable(itemInfo)) return null
            return View.OnClickListener {
                AbstractFloatingView.closeAllOpenViews(launcher)
                CustomBottomSheet.show(launcher, itemInfo)
            }
        }
    }

    class DashAdd : SystemShortcut<Launcher>(R.drawable.ic_add_box, R.string.dash_add_target_label) {
        override fun getOnClickListener(launcher: Launcher, itemInfo: ItemInfo): View.OnClickListener? {
            return View.OnClickListener {
                AbstractFloatingView.closeAllOpenViews(launcher)
                saveDashItems(launcher, itemInfo)
            }
        }

        private fun saveDashItems(context: Context, itemInfo: ItemInfo) {
            val prefs = Utilities.getZimPrefs(context)
            val currentItems = ArrayList(prefs.minibarItems)
            validateDashItems(itemInfo)

            currentItems.add(itemInfo.targetComponent.toString())
            prefs.minibarItems = currentItems.toHashSet()
        }

        private fun validateDashItems(itemInfo: ItemInfo) {
            // TODO VALIDAR DUPLICADOS Y ELIMINADOS
        }
    }

    companion object : ZimSingletonHolder<ZimShortcut>(::ZimShortcut)
}
