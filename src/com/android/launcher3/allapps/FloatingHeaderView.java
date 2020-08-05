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
package com.android.launcher3.allapps;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsContainerView.AdapterHolder;
import com.android.launcher3.anim.PropertySetter;

import java.util.ArrayList;

public class FloatingHeaderView extends LinearLayout implements
    ValueAnimator.AnimatorUpdateListener {

    private final Rect mClip = new Rect(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    private final ValueAnimator mAnimator = ValueAnimator.ofInt(0, 0);
    private final Point mTempOffset = new Point();
    private final RecyclerView.OnScrollListener mOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(final RecyclerView recyclerView, final int newState) {
        }

        @Override
        public void onScrolled(final RecyclerView rv, final int dx, final int dy) {
            if (rv != mCurrentRV) {
                return;
            }

            if (mAnimator.isStarted()) {
                mAnimator.cancel();
            }

            int current = -mCurrentRV.getCurrentScrollY();
            moved(current);
            apply();
        }
    };

    protected ViewGroup mTabLayout;
    private AllAppsRecyclerView mCurrentRV;
    private ArrayList<AllAppsRecyclerView> mRVs = new ArrayList<>();
    private ViewGroup mParent;
    private boolean mHeaderCollapsed;
    private int mSnappedScrolledY;
    private int mTranslationY;

    private boolean mAllowTouchForwarding;
    private boolean mForwardToRecyclerView;

    protected boolean mTabsHidden;
    protected int mMaxTranslation;
    private int mActiveRV = 0;

    public FloatingHeaderView(final @NonNull Context context) {
        this(context, null);
    }

    public FloatingHeaderView(final @NonNull Context context, final @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTabLayout = findViewById(R.id.tabs_scroller);
    }

    public void setup(final AdapterHolder[] mAH, final boolean tabsHidden) {
        mTabsHidden = tabsHidden;
        mTabLayout.setVisibility(tabsHidden ? View.GONE : View.VISIBLE);
        for (AllAppsRecyclerView recyclerView : mRVs) {
            recyclerView.removeOnScrollListener(mOnScrollListener);
        }
        mRVs.clear();
        for (AdapterHolder holder : mAH) {
            if (holder.recyclerView != null) {
                mRVs.add(setupRV(null, holder.recyclerView));
            }
        }
        mParent = (ViewGroup) mRVs.get(0).getParent();
        setCurrentActive(Math.min(mActiveRV, mRVs.size() - 1));
        reset(false);
    }

    private AllAppsRecyclerView setupRV(final AllAppsRecyclerView old, final AllAppsRecyclerView updated) {
        if (old != updated && updated != null) {
            updated.addOnScrollListener(mOnScrollListener);
        }
        return updated;
    }

    public void setCurrentActive(final int active) {
        mCurrentRV = mRVs.get(active);
        mActiveRV = active;
    }

    public int getMaxTranslation() {
        if (mMaxTranslation == 0 && mTabsHidden) {
            return getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_bottom_padding);
        } else if (mMaxTranslation > 0 && mTabsHidden) {
            return mMaxTranslation + getPaddingTop();
        } else {
            return mMaxTranslation;
        }
    }

    private boolean canSnapAt(final int currentScrollY) {
        return Math.abs(currentScrollY) <= mMaxTranslation;
    }

    private void moved(final int currentScrollY) {
        if (mHeaderCollapsed) {
            if (currentScrollY <= mSnappedScrolledY) {
                if (canSnapAt(currentScrollY)) {
                    mSnappedScrolledY = currentScrollY;
                }
            } else {
                mHeaderCollapsed = false;
            }
            mTranslationY = currentScrollY;
        } else if (!mHeaderCollapsed) {
            mTranslationY = currentScrollY - mSnappedScrolledY - mMaxTranslation;

            // update state vars
            if (mTranslationY >= 0) { // expanded: must not move down further
                mTranslationY = 0;
                mSnappedScrolledY = currentScrollY - mMaxTranslation;
            } else if (mTranslationY <= -mMaxTranslation) { // hide or stay hidden
                mHeaderCollapsed = true;
                mSnappedScrolledY = -mMaxTranslation;
            }
        }
    }

    protected void applyScroll(final int uncappedY, final int currentY) {
    }

    protected void apply() {
        int uncappedTranslationY = mTranslationY;
        mTranslationY = Math.max(mTranslationY, -mMaxTranslation);
        applyScroll(uncappedTranslationY, mTranslationY);
        mTabLayout.setTranslationY(mTranslationY);
        mClip.top = mMaxTranslation + mTranslationY;
        // clipping on a draw might cause additional redraw
        for (AllAppsRecyclerView rv : mRVs) {
            rv.setClipBounds(mClip);
        }
    }

    public void reset(final boolean animate) {
        if (mAnimator.isStarted()) {
            mAnimator.cancel();
        }
        if (animate) {
            mAnimator.setIntValues(mTranslationY, 0);
            mAnimator.addUpdateListener(this);
            mAnimator.setDuration(150);
            mAnimator.start();
        } else {
            mTranslationY = 0;
            apply();
        }
        mHeaderCollapsed = false;
        mSnappedScrolledY = -mMaxTranslation;
        mCurrentRV.scrollToTop();
    }

    public boolean isExpanded() {
        return !mHeaderCollapsed;
    }

    @Override
    public void onAnimationUpdate(final ValueAnimator animation) {
        mTranslationY = (Integer) animation.getAnimatedValue();
        apply();
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        if (!mAllowTouchForwarding) {
            mForwardToRecyclerView = false;
            return super.onInterceptTouchEvent(ev);
        }
        calcOffset(mTempOffset);
        ev.offsetLocation(mTempOffset.x, mTempOffset.y);
        mForwardToRecyclerView = mCurrentRV.onInterceptTouchEvent(ev);
        ev.offsetLocation(-mTempOffset.x, -mTempOffset.y);
        return mForwardToRecyclerView || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (mForwardToRecyclerView) {
            // take this view's and parent view's (view pager) location into account
            calcOffset(mTempOffset);
            event.offsetLocation(mTempOffset.x, mTempOffset.y);
            try {
                return mCurrentRV.onTouchEvent(event);
            } finally {
                event.offsetLocation(-mTempOffset.x, -mTempOffset.y);
            }
        } else {
            return super.onTouchEvent(event);
        }
    }

    private void calcOffset(final Point p) {
        p.x = getLeft() - mCurrentRV.getLeft() - mParent.getLeft();
        p.y = getTop() - mCurrentRV.getTop() - mParent.getTop();
    }

    public void setContentVisibility(final boolean hasHeader, final boolean hasContent, final PropertySetter setter,
                                     final Interpolator fadeInterpolator) {
        setter.setViewAlpha(this, hasContent ? 1 : 0, fadeInterpolator);
        allowTouchForwarding(hasContent);
    }

    protected void allowTouchForwarding(final boolean allow) {
        mAllowTouchForwarding = allow;
    }

    public boolean hasVisibleContent() {
        return false;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}


