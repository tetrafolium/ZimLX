/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.views;

import static com.android.launcher3.Utilities.SINGLE_FRAME_MS;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.util.TouchController;
import java.util.ArrayList;

/**
 * A viewgroup with utility methods for drag-n-drop and touch interception
 */
public abstract class BaseDragLayer<T extends BaseDraggingActivity>
	extends InsettableFrameLayout {

protected final int[] mTmpXY = new int[2];
protected final Rect mHitRect = new Rect();

protected final T mActivity;
private final MultiValueAlpha mMultiValueAlpha;

protected TouchController[] mControllers;
protected TouchController mActiveController;
private TouchCompleteListener mTouchCompleteListener;

public BaseDragLayer(final Context context, final AttributeSet attrs,
                     final int alphaChannelCount) {
	super(context, attrs);
	mActivity = (T)BaseActivity.fromContext(context);
	mMultiValueAlpha = new MultiValueAlpha(this, alphaChannelCount);
}

public boolean isEventOverView(final View view, final MotionEvent ev) {
	getDescendantRectRelativeToSelf(view, mHitRect);
	return mHitRect.contains((int)ev.getX(), (int)ev.getY());
}

@Override
public boolean onInterceptTouchEvent(final MotionEvent ev) {
	int action = ev.getAction();

	if (action == MotionEvent.ACTION_UP ||
	    action == MotionEvent.ACTION_CANCEL) {
		if (mTouchCompleteListener != null) {
			mTouchCompleteListener.onTouchComplete();
		}
		mTouchCompleteListener = null;
	} else if (action == MotionEvent.ACTION_DOWN) {
		mActivity.finishAutoCancelActionMode();
	}
	return findActiveController(ev);
}

protected boolean findActiveController(final MotionEvent ev) {
	mActiveController = null;

	AbstractFloatingView topView =
		AbstractFloatingView.getTopOpenView(mActivity);
	if (topView != null && topView.onControllerInterceptTouchEvent(ev)) {
		mActiveController = topView;
		return true;
	}

	for (TouchController controller : mControllers) {
		if (controller.onControllerInterceptTouchEvent(ev)) {
			mActiveController = controller;
			return true;
		}
	}
	return false;
}

@Override
public boolean
onRequestSendAccessibilityEvent(final View child,
                                final AccessibilityEvent event) {
	// Shortcuts can appear above folder
	View topView = AbstractFloatingView.getTopOpenViewWithType(
		mActivity, AbstractFloatingView.TYPE_ACCESSIBLE);
	if (topView != null) {
		if (child == topView) {
			return super.onRequestSendAccessibilityEvent(child, event);
		}
		// Skip propagating onRequestSendAccessibilityEvent for all other children
		// which are not topView
		return false;
	}
	return super.onRequestSendAccessibilityEvent(child, event);
}

@Override
public void
addChildrenForAccessibility(final ArrayList<View> childrenForAccessibility) {
	View topView = AbstractFloatingView.getTopOpenViewWithType(
		mActivity, AbstractFloatingView.TYPE_ACCESSIBLE);
	if (topView != null) {
		// Only add the top view as a child for accessibility when it is open
		addAccessibleChildToList(topView, childrenForAccessibility);
	} else {
		super.addChildrenForAccessibility(childrenForAccessibility);
	}
}

protected void addAccessibleChildToList(final View child,
                                        final ArrayList<View> outList) {
	if (child.isImportantForAccessibility()) {
		outList.add(child);
	} else {
		child.addChildrenForAccessibility(outList);
	}
}

@Override
public void onViewRemoved(final View child) {
	super.onViewRemoved(child);
	if (child instanceof AbstractFloatingView) {
		// Handles the case where the view is removed without being properly
		// closed. This can happen if something goes wrong during a state
		// change/transition.
		postDelayed(()->{
				AbstractFloatingView floatingView = (AbstractFloatingView)child;
				if (floatingView.isOpen()) {
				        floatingView.close(false);
				}
			}, SINGLE_FRAME_MS);
	}
}

@Override
public boolean onTouchEvent(final MotionEvent ev) {
	int action = ev.getAction();
	if (action == MotionEvent.ACTION_UP ||
	    action == MotionEvent.ACTION_CANCEL) {
		if (mTouchCompleteListener != null) {
			mTouchCompleteListener.onTouchComplete();
		}
		mTouchCompleteListener = null;
	}

	if (mActiveController != null) {
		return mActiveController.onControllerTouchEvent(ev);
	} else {
		// In case no child view handled the touch event, we may not get
		// onIntercept anymore
		return findActiveController(ev);
	}
}

/**
 * Determine the rect of the descendant in this DragLayer's coordinates
 *
 * @param descendant The descendant whose coordinates we want to find.
 * @param r          The rect into which to place the results.
 * @return The factor by which this descendant is scaled relative to this
 *     DragLayer.
 */
public float getDescendantRectRelativeToSelf(final View descendant,
                                             final Rect r) {
	mTmpXY[0] = 0;
	mTmpXY[1] = 0;
	float scale = getDescendantCoordRelativeToSelf(descendant, mTmpXY);

	r.set(mTmpXY[0], mTmpXY[1],
	      (int)(mTmpXY[0] + scale * descendant.getMeasuredWidth()),
	      (int)(mTmpXY[1] + scale * descendant.getMeasuredHeight()));
	return scale;
}

public float getLocationInDragLayer(final View child, final int[] loc) {
	loc[0] = 0;
	loc[1] = 0;
	return getDescendantCoordRelativeToSelf(child, loc);
}

public float getDescendantCoordRelativeToSelf(final View descendant,
                                              final int[] coord) {
	return getDescendantCoordRelativeToSelf(descendant, coord, false);
}

/**
 * Given a coordinate relative to the descendant, find the coordinate in this
 * DragLayer's coordinates.
 *
 * @param descendant        The descendant to which the passed coordinate is
 *     relative.
 * @param coord             The coordinate that we want mapped.
 * @param includeRootScroll Whether or not to account for the scroll of the
 *     root descendant:
 *                          sometimes this is relevant as in a child's
 * coordinates within the root descendant.
 * @return The factor by which this descendant is scaled relative to this
 *     DragLayer. Caution
 * this scale factor is assumed to be equal in X and Y, and so if at any point
 * this assumption fails, we will need to return a pair of scale factors.
 */
public float
getDescendantCoordRelativeToSelf(final View descendant, final int[] coord,
                                 final boolean includeRootScroll) {
	return Utilities.getDescendantCoordRelativeToAncestor(
		descendant, this, coord, includeRootScroll);
}

/**
 * Inverse of {@link #getDescendantCoordRelativeToSelf(View, int[])}.
 */
public void mapCoordInSelfToDescendant(final View descendant,
                                       final int[] coord) {
	Utilities.mapCoordInSelfToDescendant(descendant, this, coord);
}

public void getViewRectRelativeToSelf(final View v, final Rect r) {
	int[] loc = new int[2];
	getLocationInWindow(loc);
	int x = loc[0];
	int y = loc[1];

	v.getLocationInWindow(loc);
	int vX = loc[0];
	int vY = loc[1];

	int left = vX - x;
	int top = vY - y;
	r.set(left, top, left + v.getMeasuredWidth(), top + v.getMeasuredHeight());
}

@Override
public boolean dispatchUnhandledMove(final View focused,
                                     final int direction) {
	// Consume the unhandled move if a container is open, to avoid switching
	// pages underneath.
	return AbstractFloatingView.getTopOpenView(mActivity) != null;
}

@Override
protected boolean
onRequestFocusInDescendants(final int direction,
                            final Rect previouslyFocusedRect) {
	View topView = AbstractFloatingView.getTopOpenView(mActivity);
	if (topView != null) {
		return topView.requestFocus(direction, previouslyFocusedRect);
	} else {
		return super.onRequestFocusInDescendants(direction,
		                                         previouslyFocusedRect);
	}
}

@Override
public void addFocusables(final ArrayList<View> views, final int direction,
                          final int focusableMode) {
	View topView = AbstractFloatingView.getTopOpenView(mActivity);
	if (topView != null) {
		topView.addFocusables(views, direction);
	} else {
		super.addFocusables(views, direction, focusableMode);
	}
}

public void setTouchCompleteListener(final TouchCompleteListener listener) {
	mTouchCompleteListener = listener;
}

public interface TouchCompleteListener { void onTouchComplete(); }

@Override
public LayoutParams generateLayoutParams(final AttributeSet attrs) {
	return new LayoutParams(getContext(), attrs);
}

@Override
protected LayoutParams generateDefaultLayoutParams() {
	return new LayoutParams(LayoutParams.WRAP_CONTENT,
	                        LayoutParams.WRAP_CONTENT);
}

// Override to allow type-checking of LayoutParams.
@Override
protected boolean checkLayoutParams(final ViewGroup.LayoutParams p) {
	return p instanceof LayoutParams;
}

@Override
protected LayoutParams generateLayoutParams(final ViewGroup.LayoutParams p) {
	return new LayoutParams(p);
}

public AlphaProperty getAlphaProperty(final int index) {
	return mMultiValueAlpha.getProperty(index);
}

public static class LayoutParams extends InsettableFrameLayout.LayoutParams {
public int x, y;
public boolean customPosition = false;

public LayoutParams(final Context c, final AttributeSet attrs) {
	super(c, attrs);
}

public LayoutParams(final int width, final int height) {
	super(width, height);
}

public LayoutParams(final ViewGroup.LayoutParams lp) {
	super(lp);
}

public void setWidth(final int width) {
	this.width = width;
}

public int getWidth() {
	return width;
}

public void setHeight(final int height) {
	this.height = height;
}

public int getHeight() {
	return height;
}

public void setX(final int x) {
	this.x = x;
}

public int getX() {
	return x;
}

public void setY(final int y) {
	this.y = y;
}

public int getY() {
	return y;
}
}

protected void onLayout(final boolean changed, final int l, final int t,
                        final int r, final int b) {
	super.onLayout(changed, l, t, r, b);
	int count = getChildCount();
	for (int i = 0; i < count; i++) {
		View child = getChildAt(i);
		final FrameLayout.LayoutParams flp =
			(FrameLayout.LayoutParams)child.getLayoutParams();
		if (flp instanceof LayoutParams) {
			final LayoutParams lp = (LayoutParams)flp;
			if (lp.customPosition) {
				child.layout(lp.x, lp.y, lp.x + lp.width, lp.y + lp.height);
			}
		}
	}
}
}
