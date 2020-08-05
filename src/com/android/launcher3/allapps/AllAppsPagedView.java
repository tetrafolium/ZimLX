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
package com.android.launcher3.allapps;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;

import com.android.launcher3.PagedView;
import com.android.launcher3.R;

public class AllAppsPagedView extends PagedView<PersonalWorkSlidingTabStrip> {

    final static float START_DAMPING_TOUCH_SLOP_ANGLE = (float) Math.PI / 6;
    final static float MAX_SWIPE_ANGLE = (float) Math.PI / 3;
    final static float TOUCH_SLOP_DAMPING_FACTOR = 4;

    public AllAppsPagedView(final Context context) {
        this(context, null);
    }

    public AllAppsPagedView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsPagedView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected String getCurrentPageDescription() {
        // Not necessary, tab-bar already has two tabs with their own descriptions.
        return "";
    }

    @Override
    protected void onScrollChanged(final int l, final int t, final int oldl, final int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mPageIndicator.setScroll(l, mMaxScrollX);
    }

    @Override
    protected void determineScrollingStart(final MotionEvent ev) {
        float absDeltaX = Math.abs(ev.getX() - getDownMotionX());
        float absDeltaY = Math.abs(ev.getY() - getDownMotionY());

        if (Float.compare(absDeltaX, 0f) == 0) return;

        float slope = absDeltaY / absDeltaX;
        float theta = (float) Math.atan(slope);

        if (absDeltaX > mTouchSlop || absDeltaY > mTouchSlop) {
            cancelCurrentPageLongPress();
        }

        if (theta > MAX_SWIPE_ANGLE) {
            return;
        } else if (theta > START_DAMPING_TOUCH_SLOP_ANGLE) {
            theta -= START_DAMPING_TOUCH_SLOP_ANGLE;
            float extraRatio = (float)
                               Math.sqrt((theta / (MAX_SWIPE_ANGLE - START_DAMPING_TOUCH_SLOP_ANGLE)));
            super.determineScrollingStart(ev, 1 + TOUCH_SLOP_DAMPING_FACTOR * extraRatio);
        } else {
            super.determineScrollingStart(ev);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void addTabs(final int count) {
        int childCount = getChildCount();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = childCount; i < count; i++) {
            inflater.inflate(R.layout.all_apps_rv_layout, this);
        }
        while (getChildCount() > count) {
            removeViewAt(0);
        }
    }
}
