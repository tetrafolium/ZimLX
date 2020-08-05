/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps;

import static org.zimmob.zimlx.util.Config.SORT_AZ;
import static org.zimmob.zimlx.util.Config.SORT_BY_COLOR;
import static org.zimmob.zimlx.util.Config.SORT_LAST_INSTALLED;
import static org.zimmob.zimlx.util.Config.SORT_MOST_USED;
import static org.zimmob.zimlx.util.Config.SORT_ZA;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.UserHandle;
import androidx.core.graphics.ColorUtils;
import com.android.launcher3.AppInfo;
import com.android.launcher3.IconCache;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LabelComparator;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.zimmob.zimlx.ZimPreferences;
import org.zimmob.zimlx.allapps.AppColorComparator;
import org.zimmob.zimlx.groups.DrawerFolderInfo;
import org.zimmob.zimlx.groups.DrawerFolderItem;
import org.zimmob.zimlx.model.AppCountInfo;
import org.zimmob.zimlx.util.DbHelper;
import org.zimmob.zimlx.util.InstallTimeComparator;
import org.zimmob.zimlx.util.MostUsedComparator;

/**
 * The alphabetically sorted list of applications.
 */
public class AlphabeticalAppsList implements AllAppsStore.OnUpdateListener {

public static final String TAG = "AlphabeticalAppsList";

private static final int FAST_SCROLL_FRACTION_DISTRIBUTE_BY_ROWS_FRACTION = 0;
private static final int FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS = 1;

private final int mFastScrollDistributionMode =
	FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS;

/**
 * Info about a fast scroller section, depending if sections are merged, the
 * fast scroller sections will not be the same set as the section headers.
 */
public static class FastScrollSectionInfo {
// The section name
public String sectionName;
// The AdapterItem to scroll to for this section
public AdapterItem fastScrollToItem;
// The touch fraction that should map to this fast scroll section info
public float touchFraction;
// The color of this fast scroll section
public int color;

public FastScrollSectionInfo(final String sectionName, final int color) {
	this.sectionName = sectionName;
	this.color = color;
}
}

/**
 * Info about a particular adapter item (can be either section or app)
 */
public static class AdapterItem {
/**
 * Common properties
 */
// The index of this adapter item in the list
public int position;
// The type of this item
public int viewType;

/**
 * App-only properties
 */
// The section name of this app.  Note that there can be multiple items with
// different sectionNames in the same section
public String sectionName = null;
// The row that this item shows up on
public int rowIndex;
// The index of this app in the row
public int rowAppIndex;
// The associated AppInfo for the app
public AppInfo appInfo = null;
// The index of this app not including sections
public int appIndex = -1;

/**
 * Folder-only properties
 */
// The associated folder for the folder
public DrawerFolderItem folderItem = null;

/**
 * Search suggestion-only properties
 */
public String suggestion;

public static AdapterItem asApp(final int pos, final String sectionName,
                                final AppInfo appInfo, final int appIndex) {
	AdapterItem item = new AdapterItem();
	item.viewType = AllAppsGridAdapter.VIEW_TYPE_ICON;
	item.position = pos;
	item.sectionName = sectionName;
	item.appInfo = appInfo;
	item.appIndex = appIndex;
	return item;
}

public static AdapterItem asEmptySearch(final int pos) {
	AdapterItem item = new AdapterItem();
	item.viewType = AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH;
	item.position = pos;
	return item;
}

public static AdapterItem asAllAppsDivider(final int pos) {
	AdapterItem item = new AdapterItem();
	item.viewType = AllAppsGridAdapter.VIEW_TYPE_ALL_APPS_DIVIDER;
	item.position = pos;
	return item;
}

public static AdapterItem asMarketSearch(final int pos) {
	AdapterItem item = new AdapterItem();
	item.viewType = AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET;
	item.position = pos;
	return item;
}

public static AdapterItem asWorkTabFooter(final int pos) {
	AdapterItem item = new AdapterItem();
	item.viewType = AllAppsGridAdapter.VIEW_TYPE_WORK_TAB_FOOTER;
	item.position = pos;
	return item;
}

public static AdapterItem asFolder(final int pos, final String sectionName,
                                   final DrawerFolderInfo folderInfo,
                                   final int folderIndex) {
	AdapterItem item = new AdapterItem();
	item.viewType = AllAppsGridAdapter.VIEW_TYPE_FOLDER;
	item.position = pos;
	item.sectionName = sectionName;
	item.folderItem = new DrawerFolderItem(folderInfo, folderIndex);
	return item;
}

public static AdapterItem asSearchSuggestion(final int pos,
                                             final String suggestion) {
	AdapterItem item = new AdapterItem();
	item.viewType = AllAppsGridAdapter.VIEW_TYPE_SEARCH_SUGGESTION;
	item.position = pos;
	item.suggestion = suggestion;
	return item;
}
}

private final Launcher mLauncher;

// The set of apps from the system
private final List<AppInfo> mApps = new ArrayList<>();
private final AllAppsStore mAllAppsStore;

// The set of filtered apps with the current filter
private final List<AppInfo> mFilteredApps = new ArrayList<>();
// The current set of adapter items
private final ArrayList<AdapterItem> mAdapterItems = new ArrayList<>();
// The set of sections that we allow fast-scrolling to (includes non-merged
// sections)
private final List<FastScrollSectionInfo> mFastScrollerSections =
	new ArrayList<>();
// Is it the work profile app list.
private boolean mIsWork;

// The of ordered component names as a result of a search query
private ArrayList<ComponentKey> mSearchResults;
private HashMap<CharSequence, String> mCachedSectionNames = new HashMap<>();
private AllAppsGridAdapter mAdapter;
private AlphabeticIndexCompat mIndexer;
private AppInfoComparator mAppNameComparator;
private AppColorComparator mAppColorComparator;
private final int mNumAppsPerRow;
private int mNumAppRowsInAdapter;
private ItemInfoMatcher mItemFilter;
private ZimPreferences prefs;

private List<String> mSearchSuggestions;

public AlphabeticalAppsList(final Context context,
                            final AllAppsStore appsStore,
                            final boolean isWork) {
	mAllAppsStore = appsStore;
	mLauncher = Launcher.getLauncher(context);
	mIndexer = new AlphabeticIndexCompat(context);
	mAppNameComparator = new AppInfoComparator(context);
	mAppColorComparator = new AppColorComparator(context);
	mIsWork = isWork;
	mNumAppsPerRow = mLauncher.getDeviceProfile().inv.numColsDrawer;
	mAllAppsStore.addUpdateListener(this);
	prefs = Utilities.getZimPrefs(context);
}

public void updateItemFilter(final ItemInfoMatcher itemFilter) {
	this.mItemFilter = itemFilter;
	onAppsUpdated();
}

/**
 * Sets the adapter to notify when this dataset changes.
 */
public void setAdapter(final AllAppsGridAdapter adapter) {
	mAdapter = adapter;
}

/**
 * Returns all the apps.
 */
public List<AppInfo> getApps() {
	return mApps;
}

private void sortApps(final int sortType) {
	switch (sortType) {
	// SORT BY NAME AZ
	case SORT_AZ:
		Collections.sort(mApps, mAppNameComparator);
		break;

	// SORT BY NAME ZA
	case SORT_ZA:
		Collections.sort(
			mApps,
			(p2, p1)->Collator.getInstance().compare(p1.title, p2.title));
		break;

	// SORT BY LAST INSTALLED
	case SORT_LAST_INSTALLED:
		PackageManager pm = mLauncher.getApplicationContext().getPackageManager();
		InstallTimeComparator installTimeComparator =
			new InstallTimeComparator(pm);
		Collections.sort(mApps, installTimeComparator);
		break;

	// SORT BY MOST USED DESC
	case SORT_MOST_USED:
		DbHelper db = new DbHelper(mLauncher.getApplicationContext());
		List<AppCountInfo> appsCounter = db.getAppsCount();
		db.close();
		MostUsedComparator mostUsedComparator =
			new MostUsedComparator(appsCounter);
		Collections.sort(mApps, mostUsedComparator);
		break;

	case SORT_BY_COLOR:
		Collections.sort(mApps, mAppColorComparator);
		break;
	default:
		Collections.sort(mApps, mAppNameComparator);
		break;
	}
}

/**
 * Returns fast scroller sections of all the current filtered applications.
 */
public List<FastScrollSectionInfo> getFastScrollerSections() {
	return mFastScrollerSections;
}

/**
 * Returns the current filtered list of applications broken down into their
 * sections.
 */
public List<AdapterItem> getAdapterItems() {
	return mAdapterItems;
}

/**
 * Returns the number of rows of applications
 */
public int getNumAppRows() {
	return mNumAppRowsInAdapter;
}

/**
 * Returns the number of applications in this list.
 */
public int getNumFilteredApps() {
	return mFilteredApps.size();
}

/**
 * Returns whether there are is a filter set.
 */
public boolean hasFilter() {
	return (mSearchResults != null);
}

/**
 * Returns whether there are suggestions.
 */
public boolean hasSuggestions() {
	return mSearchSuggestions != null && !mSearchSuggestions.isEmpty();
}

/**
 * Returns whether there are no filtered results.
 */
public boolean hasNoFilteredResults() {
	return (mSearchResults != null) && mFilteredApps.isEmpty() &&
	       (mSearchSuggestions != null) && mSearchSuggestions.isEmpty();
}

public List<AppInfo> getFilteredApps() {
	return mFilteredApps;
}

/**
 * Sets the sorted list of filtered components.
 */
public boolean setOrderedFilter(final ArrayList<ComponentKey> f) {
	if (mSearchResults != f) {
		boolean same = mSearchResults != null && mSearchResults.equals(f);
		mSearchResults = f;
		onAppsUpdated();
		return !same;
	}
	return false;
}

public boolean setSearchSuggestions(final List<String> suggestions) {
	if (mSearchSuggestions != suggestions) {
		boolean same =
			mSearchSuggestions != null && mSearchSuggestions.equals(suggestions);
		mSearchSuggestions = suggestions;
		onAppsUpdated();
		return !same;
	}
	return false;
}

/**
 * Updates internals when the set of apps are updated.
 */
@Override
public void onAppsUpdated() {
	// Sort the list of apps
	mApps.clear();

	for (AppInfo app : mAllAppsStore.getApps()) {
		if (mItemFilter == null || mItemFilter.matches(app, null) ||
		    hasFilter()) {
			mApps.add(app);
		}
	}
	sortApps(prefs.getSortMode());

	/*if (!prefs.getShowPredictions()) {
	    sortApps(prefs.getSortMode());
	   }
	   else{
	    Collections.sort(mApps, mAppNameComparator);
	   }*/

	// As a special case for some languages (currently only Simplified Chinese),
	// we may need to coalesce sections
	Locale curLocale = mLauncher.getResources().getConfiguration().locale;
	boolean localeRequiresSectionSorting =
		curLocale.getLanguage().equals(
			Locale.SIMPLIFIED_CHINESE.getLanguage()) &&
		curLocale.getCountry().equals(Locale.SIMPLIFIED_CHINESE.getCountry());
	if (localeRequiresSectionSorting) {
		// Compute the section headers. We use a TreeMap with the section name
		// comparator to ensure that the sections are ordered when we iterate over
		// it later
		TreeMap<String, ArrayList<AppInfo> > sectionMap =
			new TreeMap<>(new LabelComparator());
		for (AppInfo info : mApps) {
			// Add the section to the cache
			String sectionName = getAndUpdateCachedSectionName(info);

			// Add it to the mapping
			ArrayList<AppInfo> sectionApps = sectionMap.get(sectionName);
			if (sectionApps == null) {
				sectionApps = new ArrayList<>();
				sectionMap.put(sectionName, sectionApps);
			}
			sectionApps.add(info);
		}

		// Add each of the section apps to the list in order
		mApps.clear();
		for (Map.Entry<String, ArrayList<AppInfo> > entry :
		     sectionMap.entrySet()) {
			mApps.addAll(entry.getValue());
		}
	} else {
		// Just compute the section headers for use below
		for (AppInfo info : mApps) {
			// Add the section to the cache
			getAndUpdateCachedSectionName(info);
		}
	}

	// Recompose the set of adapter items from the current set of apps
	updateAdapterItems();
}

/**
 * Updates the set of filtered apps with the current filter.  At this point,
 * we expect mCachedSectionNames to have been calculated for the set of all
 * apps in mApps.
 */
private void updateAdapterItems() {
	refillAdapterItems();
	refreshRecyclerView();
}

private void refreshRecyclerView() {
	if (mAdapter != null) {
		mAdapter.notifyDataSetChanged();
	}
}

private void refillAdapterItems() {
	String lastSectionName = null;
	FastScrollSectionInfo lastFastScrollerSectionInfo = null;
	int position = 0;
	int appIndex = 0;
	int folderIndex = 0;

	// Prepare to update the list of sections, filtered apps, etc.
	mFilteredApps.clear();
	mFastScrollerSections.clear();
	mAdapterItems.clear();

	// Search suggestions should be all the way to the top
	if (hasFilter() && hasSuggestions()) {
		for (String suggestion : mSearchSuggestions) {
			mAdapterItems.add(
				AdapterItem.asSearchSuggestion(position++, suggestion));
		}
	}

	// Drawer folders are arranged before all the apps
	if (!hasFilter()) {
		for (DrawerFolderInfo info : getFolderInfos()) {
			String sectionName = "#";

			// Create a new section if the section names do not match
			if (!sectionName.equals(lastSectionName)) {
				lastSectionName = sectionName;
				lastFastScrollerSectionInfo =
					new FastScrollSectionInfo(sectionName, Color.WHITE);
				mFastScrollerSections.add(lastFastScrollerSectionInfo);
			}

			info.setAppsStore(mAllAppsStore);
			// Create an folder item
			AdapterItem appItem =
				AdapterItem.asFolder(position++, sectionName, info, folderIndex++);
			if (lastFastScrollerSectionInfo.fastScrollToItem == null) {
				lastFastScrollerSectionInfo.fastScrollToItem = appItem;
			}
			mAdapterItems.add(appItem);
		}
	}

	Set<ComponentKey> folderFilters = getFolderFilteredApps();

	// Recreate the filtered and sectioned apps (for convenience for the grid
	// layout) from the ordered set of sections
	for (AppInfo info : getFiltersAppInfos()) {
		if (!hasFilter() && folderFilters.contains(info.toComponentKey())) {
			continue;
		}
		String sectionName = getAndUpdateCachedSectionName(info);

		// Create a new section if the section names do not match
		if (!sectionName.equals(lastSectionName)) {
			lastSectionName = sectionName;
			int color = Color.WHITE;
			if (prefs.getSortMode() == SORT_BY_COLOR) {
				color = info.iconColor;
			}
			lastFastScrollerSectionInfo =
				new FastScrollSectionInfo(sectionName, color);
			mFastScrollerSections.add(lastFastScrollerSectionInfo);
		}

		// Create an app item
		AdapterItem appItem =
			AdapterItem.asApp(position++, sectionName, info, appIndex++);
		if (lastFastScrollerSectionInfo.fastScrollToItem == null) {
			lastFastScrollerSectionInfo.fastScrollToItem = appItem;
		}
		mAdapterItems.add(appItem);
		mFilteredApps.add(info);
	}

	if (hasFilter()) {
		// Append the search market item
		if (hasNoFilteredResults() && !hasSuggestions()) {
			mAdapterItems.add(AdapterItem.asEmptySearch(position++));
		} else {
			mAdapterItems.add(AdapterItem.asAllAppsDivider(position++));
		}
		mAdapterItems.add(AdapterItem.asMarketSearch(position++));
	}

	if (mNumAppsPerRow != 0) {
		// Update the number of rows in the adapter after we do all the merging
		// (otherwise, we would have to shift the values again)
		int numAppsInSection = 0;
		int numAppsInRow = 0;
		int rowIndex = -1;
		for (AdapterItem item : mAdapterItems) {
			item.rowIndex = 0;
			if (AllAppsGridAdapter.isDividerViewType(item.viewType)) {
				numAppsInSection = 0;
			} else if (AllAppsGridAdapter.isIconViewType(item.viewType)) {
				if (numAppsInSection % mNumAppsPerRow == 0) {
					numAppsInRow = 0;
					rowIndex++;
				}
				item.rowIndex = rowIndex;
				item.rowAppIndex = numAppsInRow;
				numAppsInSection++;
				numAppsInRow++;
			}
		}
		mNumAppRowsInAdapter = rowIndex + 1;

		// Pre-calculate all the fast scroller fractions
		switch (mFastScrollDistributionMode) {
		case FAST_SCROLL_FRACTION_DISTRIBUTE_BY_ROWS_FRACTION:
			float rowFraction = 1f / mNumAppRowsInAdapter;
			for (FastScrollSectionInfo info : mFastScrollerSections) {
				AdapterItem item = info.fastScrollToItem;
				if (!AllAppsGridAdapter.isIconViewType(item.viewType)) {
					info.touchFraction = 0f;
					continue;
				}

				float subRowFraction =
					item.rowAppIndex * (rowFraction / mNumAppsPerRow);
				info.touchFraction = item.rowIndex * rowFraction + subRowFraction;
			}
			break;
		case FAST_SCROLL_FRACTION_DISTRIBUTE_BY_NUM_SECTIONS:
			float perSectionTouchFraction = 1f / mFastScrollerSections.size();
			float cumulativeTouchFraction = 0f;
			for (FastScrollSectionInfo info : mFastScrollerSections) {
				AdapterItem item = info.fastScrollToItem;
				if (!AllAppsGridAdapter.isIconViewType(item.viewType)) {
					info.touchFraction = 0f;
					continue;
				}
				info.touchFraction = cumulativeTouchFraction;
				cumulativeTouchFraction += perSectionTouchFraction;
			}
			break;
		}
	}

	// Add the work profile footer if required.
	if (shouldShowWorkFooter()) {
		mAdapterItems.add(AdapterItem.asWorkTabFooter(position++));
	}
}

private boolean shouldShowWorkFooter() {
	return mIsWork && Utilities.ATLEAST_P &&
	       (DeepShortcutManager.getInstance(mLauncher).hasHostPermission() ||
	        Utilities.hasPermission(mLauncher,
	                                "android.permission.MODIFY_QUIET_MODE"));
}

private List<AppInfo> getFiltersAppInfos() {
	if (mSearchResults == null) {
		return mApps;
	}

	final LauncherAppsCompat launcherApps =
		LauncherAppsCompat.getInstance(mLauncher);
	final UserHandle user = android.os.Process.myUserHandle();
	final IconCache iconCache =
		LauncherAppState.getInstance(mLauncher).getIconCache();
	boolean quietMode =
		UserManagerCompat.getInstance(mLauncher).isQuietModeEnabled(user);
	ArrayList<AppInfo> result = new ArrayList<>();
	for (ComponentKey key : mSearchResults) {
		AppInfo match = mAllAppsStore.getApp(key);
		if (match != null) {
			result.add(match);
		} else {
			for (LauncherActivityInfo info : launcherApps.getActivityList(
				     key.componentName.getPackageName(), user)) {
				if (info.getComponentName().equals(key.componentName)) {
					final AppInfo appInfo = new AppInfo(info, user, quietMode);
					iconCache.getTitleAndIcon(appInfo, false);
					result.add(appInfo);
					break;
				}
			}
		}
	}
	return result;
}

/**
 * Returns the cached section name for the given title, recomputing and
 * updating the cache if the title has no cached section name.
 */
private String getAndUpdateCachedSectionName(final AppInfo info) {
	String sectionName = mCachedSectionNames.get(info);
	if (sectionName == null) {
		if (prefs.getSortMode() == SORT_BY_COLOR) {
			float[] hsl = new float[3];
			ColorUtils.colorToHSL(info.iconColor, hsl);
			sectionName = ""; // String.format("%d:%d:%d",
			                  // AppColorComparator.remapHue(hsl[0]),
			                  // AppColorComparator.remap(hsl[2]),
			                  // AppColorComparator.remap(hsl[1]));
		} else {
			sectionName = mIndexer.computeSectionName(info.title);
		}
		mCachedSectionNames.put(info.title, sectionName);
	}
	return sectionName;
}

public void setIsWork(final boolean isWork) {
	mIsWork = isWork;
}

private List<DrawerFolderInfo> getFolderInfos() {
	return Utilities.getZimPrefs(mLauncher)
	       .getAppGroupsManager()
	       .getDrawerFolders()
	       .getFolderInfos(this);
}

private Set<ComponentKey> getFolderFilteredApps() {
	return Utilities.getZimPrefs(mLauncher)
	       .getAppGroupsManager()
	       .getDrawerFolders()
	       .getHiddenComponents();
}

public void reset() {
	updateAdapterItems();
}
}
