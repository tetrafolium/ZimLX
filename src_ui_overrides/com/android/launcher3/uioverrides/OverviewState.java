/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.states.RotationHelper.REQUEST_ROTATE;

import android.view.View;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

private static final int STATE_FLAGS =
	FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED | FLAG_DISABLE_RESTORE |
	FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY;

public OverviewState(final int id) {
	this(id, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
}

protected OverviewState(final int id, final int transitionDuration,
                        final int stateFlags) {
	super(id, ContainerType.TASKSWITCHER, transitionDuration, stateFlags);
}

@Override
public float[] getWorkspaceScaleAndTranslation(final Launcher launcher) {
	// RecentsView recentsView = launcher.getOverviewPanel();
	Workspace workspace = launcher.getWorkspace();
	View workspacePage = workspace.getPageAt(workspace.getCurrentPage());
	float workspacePageWidth =
		workspacePage != null && workspacePage.getWidth() != 0
	    ? workspacePage.getWidth()
	    : launcher.getDeviceProfile().availableWidthPx;
	// recentsView.getTaskSize(sTempRect);
	float scale = (float)sTempRect.width() / workspacePageWidth;
	float parallaxFactor = 0.5f;
	return new float[] {scale, 0,
		            -getDefaultSwipeHeight(launcher) * parallaxFactor};
}

@Override
public float[] getOverviewScaleAndTranslationYFactor(
	final Launcher launcher) {
	return new float[] {1f, 0f};
}

@Override
public void onStateEnabled(final Launcher launcher) {
	// RecentsView rv = launcher.getOverviewPanel();
	// rv.setOverviewStateEnabled(true);
	AbstractFloatingView.closeAllOpenViews(launcher);
}

@Override
public void onStateDisabled(final Launcher launcher) {
	/*RecentsView rv = launcher.getOverviewPanel();
	   rv.setOverviewStateEnabled(false);
	   RecentsModel.getInstance(launcher).resetAssistCache();*/
}

@Override
public void onStateTransitionEnd(final Launcher launcher) {
	launcher.getRotationHelper().setCurrentStateRequest(REQUEST_ROTATE);
	DiscoveryBounce.showForOverviewIfNeeded(launcher);
}

public PageAlphaProvider
getWorkspacePageAlphaProvider(final Launcher launcher) {
	return new PageAlphaProvider(DEACCEL_2) {
		       @Override
		       public float getPageAlpha(final int pageIndex) {
			       return 0;
		       }
	};
}

@Override
public int getVisibleElements(final Launcher launcher) {
	if (launcher.getDeviceProfile().isVerticalBarLayout()) {
		return VERTICAL_SWIPE_INDICATOR;
	} else {
		return HOTSEAT_SEARCH_BOX | VERTICAL_SWIPE_INDICATOR |
		       (launcher.getAppsView().getFloatingHeaderView().hasVisibleContent()
	       ? ALL_APPS_HEADER_EXTRA
	       : HOTSEAT_ICONS);
	}
}

@Override
public float getWorkspaceScrimAlpha(final Launcher launcher) {
	return 0.5f;
}

@Override
public float getWorkspaceBlurAlpha(final Launcher launcher) {
	boolean blurEnabled =
		Utilities.getZimPrefs(launcher).getRecentsBlurredBackground();
	return blurEnabled ? 1f : 0f;
}

@Override
public float getVerticalProgress(final Launcher launcher) {
	if ((getVisibleElements(launcher) & ALL_APPS_HEADER_EXTRA) == 0) {
		// We have no all apps content, so we're still at the fully down progress.
		return super.getVerticalProgress(launcher);
	}
	return getNormalVerticalProgress(launcher);
}

public static float getNormalVerticalProgress(final Launcher launcher) {
	return 1 - (getDefaultSwipeHeight(launcher) /
	            launcher.getAllAppsController().getShiftRange());
}

@Override
public String getDescription(final Launcher launcher) {
	return "";
	// return launcher.getString(R.string.accessibility_desc_recent_apps);
}

public static float getDefaultSwipeHeight(final Launcher launcher) {
	return getDefaultSwipeHeight(launcher.getDeviceProfile());
}

public static float getDefaultSwipeHeight(final DeviceProfile dp) {
	return dp.allAppsCellHeightPx - dp.allAppsIconTextSizePx;
}
}
