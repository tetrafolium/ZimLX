/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static android.view.MotionEvent.ACTION_DOWN;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.android.launcher3.CellLayout.ContainerType;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import org.jetbrains.annotations.NotNull;
import org.zimmob.zimlx.ZimPreferences;
import org.zimmob.zimlx.util.ZimFlags;

public class ShortcutAndWidgetContainer
	extends ViewGroup implements ZimPreferences.OnPreferenceChangeListener {
static final String TAG = "ShortcutAndWidgetContainer";

// These are temporary variables to prevent having to allocate a new object
// just to return an (x, y) value from helper functions. Do NOT use them to
// maintain other state.
private final int[] mTmpCellXY = new int[2];

@ContainerType private final int mContainerType;
private final WallpaperManager mWallpaperManager;

private int mCellWidth;
private int mCellHeight;

private int mCountX;

private Launcher mLauncher;
private boolean mInvertIfRtl = false;

private ZimPreferences mPrefs;

public ShortcutAndWidgetContainer(final Context context,
                                  final @ContainerType int containerType) {
	super(context);
	mLauncher = Launcher.getLauncher(context);
	mWallpaperManager = WallpaperManager.getInstance(context);
	mContainerType = containerType;
	mPrefs = Utilities.getZimPrefs(context);
}

@Override
protected void onAttachedToWindow() {
	super.onAttachedToWindow();
	mPrefs.addOnPreferenceChangeListener(ZimFlags.DESKTOP_OVERLAP_WIDGET, this);
}

@Override
public void onValueChanged(final @NotNull String key,
                           final @NotNull ZimPreferences prefs,
                           final boolean force) {
	setClipChildren(!prefs.getAllowOverlap());
	setClipToPadding(!prefs.getAllowOverlap());
	setClipToOutline(!prefs.getAllowOverlap());
}

@Override
protected void onDetachedFromWindow() {
	super.onDetachedFromWindow();
	mPrefs.removeOnPreferenceChangeListener(ZimFlags.DESKTOP_OVERLAP_WIDGET,
	                                        this);
}
public void setCellDimensions(final int cellWidth, final int cellHeight,
                              final int countX, final int countY) {
	mCellWidth = cellWidth;
	mCellHeight = cellHeight;
	mCountX = countX;
}

public View getChildAt(final int x, final int y) {
	final int count = getChildCount();
	for (int i = 0; i < count; i++) {
		View child = getChildAt(i);
		CellLayout.LayoutParams lp =
			(CellLayout.LayoutParams)child.getLayoutParams();

		if ((lp.cellX <= x) && (x < lp.cellX + lp.cellHSpan) && (lp.cellY <= y) &&
		    (y < lp.cellY + lp.cellVSpan)) {
			return child;
		}
	}
	return null;
}

@Override
protected void onMeasure(final int widthMeasureSpec,
                         final int heightMeasureSpec) {
	int count = getChildCount();

	int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
	int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
	setMeasuredDimension(widthSpecSize, heightSpecSize);

	for (int i = 0; i < count; i++) {
		View child = getChildAt(i);
		if (child.getVisibility() != GONE) {
			measureChild(child);
		}
	}
}

public void setupLp(final View child) {
	CellLayout.LayoutParams lp =
		(CellLayout.LayoutParams)child.getLayoutParams();
	if (child instanceof LauncherAppWidgetHostView) {
		DeviceProfile profile = mLauncher.getDeviceProfile();
		lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX,
		         profile.appWidgetScale.x, profile.appWidgetScale.y);
	} else {
		lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX);
	}
}

// Set whether or not to invert the layout horizontally if the layout is in
// RTL mode.
public void setInvertIfRtl(final boolean invert) {
	mInvertIfRtl = invert;
}

public int getCellContentHeight() {
	return Math.min(getMeasuredHeight(),
	                mLauncher.getDeviceProfile().getCellHeight(mContainerType));
}

public void measureChild(final View child) {
	CellLayout.LayoutParams lp =
		(CellLayout.LayoutParams)child.getLayoutParams();
	final DeviceProfile profile = mLauncher.getDeviceProfile();

	if (child instanceof LauncherAppWidgetHostView) {
		lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX,
		         profile.appWidgetScale.x, profile.appWidgetScale.y);
		// Widgets have their own padding
	} else {
		lp.setup(mCellWidth, mCellHeight, invertLayoutHorizontally(), mCountX);
		// Center the icon/folder
		int cHeight = getCellContentHeight();
		int cellPaddingY = (int)Math.max(0, ((lp.height - cHeight) / 2f));
		int cellPaddingX = mContainerType == CellLayout.WORKSPACE
		             ? profile.workspaceCellPaddingXPx
		             : (int)(profile.edgeMarginPx / 2f);
		child.setPadding(cellPaddingX, cellPaddingY, cellPaddingX, 0);
	}
	int childWidthMeasureSpec =
		MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
	int childheightMeasureSpec =
		MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
	child.measure(childWidthMeasureSpec, childheightMeasureSpec);
}

public boolean invertLayoutHorizontally() {
	return mInvertIfRtl && Utilities.isRtl(getResources());
}

@Override
protected void onLayout(final boolean changed, final int l, final int t,
                        final int r, final int b) {
	int count = getChildCount();
	for (int i = 0; i < count; i++) {
		final View child = getChildAt(i);
		if (child.getVisibility() != GONE) {
			CellLayout.LayoutParams lp =
				(CellLayout.LayoutParams)child.getLayoutParams();

			if (child instanceof LauncherAppWidgetHostView) {
				LauncherAppWidgetHostView lahv = (LauncherAppWidgetHostView)child;

				// Scale and center the widget to fit within its cells.
				DeviceProfile profile = mLauncher.getDeviceProfile();
				float scaleX = profile.appWidgetScale.x;
				float scaleY = profile.appWidgetScale.y;

				lahv.setScaleToFit(Math.min(scaleX, scaleY));
				lahv.setTranslationForCentering(
					-(lp.width - (lp.width * scaleX)) / 2.0f,
					-(lp.height - (lp.height * scaleY)) / 2.0f);
			}

			int childLeft = lp.x;
			int childTop = lp.y;
			child.layout(childLeft, childTop, childLeft + lp.width,
			             childTop + lp.height);

			if (lp.dropped) {
				lp.dropped = false;

				final int[] cellXY = mTmpCellXY;
				getLocationOnScreen(cellXY);
				mWallpaperManager.sendWallpaperCommand(
					getWindowToken(), WallpaperManager.COMMAND_DROP,
					cellXY[0] + childLeft + lp.width / 2,
					cellXY[1] + childTop + lp.height / 2, 0, null);
			}
		}
	}
}

@Override
public boolean onInterceptTouchEvent(final MotionEvent ev) {
	if (ev.getAction() == ACTION_DOWN && getAlpha() == 0) {
		// Dont let children handle touch, if we are not visible.
		return true;
	}
	return super.onInterceptTouchEvent(ev);
}

@Override
public boolean shouldDelayChildPressedState() {
	return false;
}

@Override
public void requestChildFocus(final View child, final View focused) {
	super.requestChildFocus(child, focused);
	if (child != null) {
		Rect r = new Rect();
		child.getDrawingRect(r);
		requestRectangleOnScreen(r);
	}
}

@Override
public void cancelLongPress() {
	super.cancelLongPress();

	// Cancel long press for all children
	final int count = getChildCount();
	for (int i = 0; i < count; i++) {
		final View child = getChildAt(i);
		child.cancelLongPress();
	}
}
}
