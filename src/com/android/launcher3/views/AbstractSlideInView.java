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
package com.android.launcher3.views;

import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.SwipeDetector;

/**
 * Extension of AbstractFloatingView with common methods for sliding in from
 * bottom
 */
public abstract class AbstractSlideInView
	extends AbstractFloatingView implements SwipeDetector.Listener {

protected static Property<AbstractSlideInView, Float> TRANSLATION_SHIFT =
	new Property<AbstractSlideInView, Float>(Float.class,
	                                         "translationShift") {
	@Override
	public Float get(final AbstractSlideInView view) {
		return view.mTranslationShift;
	}

	@Override
	public void set(final AbstractSlideInView view, final Float value) {
		view.setTranslationShift(value);
	}
};
protected static final float TRANSLATION_SHIFT_CLOSED = 1f;
protected static final float TRANSLATION_SHIFT_OPENED = 0f;

protected final Launcher mLauncher;
protected final SwipeDetector mSwipeDetector;
protected final ObjectAnimator mOpenCloseAnimator;

protected View mContent;
protected Interpolator mScrollInterpolator;

// range [0, 1], 0=> completely open, 1=> completely closed
protected float mTranslationShift = TRANSLATION_SHIFT_CLOSED;

protected boolean mNoIntercept;

public AbstractSlideInView(final Context context, final AttributeSet attrs,
                           final int defStyleAttr) {
	super(context, attrs, defStyleAttr);
	mLauncher = Launcher.getLauncher(context);

	mScrollInterpolator = Interpolators.SCROLL_CUBIC;
	mSwipeDetector = new SwipeDetector(context, this, SwipeDetector.VERTICAL);

	mOpenCloseAnimator = LauncherAnimUtils.ofPropertyValuesHolder(this);
	mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(final Animator animation) {
			        mSwipeDetector.finishedScrolling();
			        announceAccessibilityChanges();
			}
		});
}

protected void setTranslationShift(final float translationShift) {
	mTranslationShift = translationShift;
	mContent.setTranslationY(mTranslationShift * mContent.getHeight());
}

@Override
public boolean onControllerInterceptTouchEvent(final MotionEvent ev) {
	if (mNoIntercept) {
		return false;
	}

	int directionsToDetectScroll =
		mSwipeDetector.isIdleState() ? SwipeDetector.DIRECTION_NEGATIVE : 0;
	mSwipeDetector.setDetectableScrollConditions(directionsToDetectScroll,
	                                             false);
	mSwipeDetector.onTouchEvent(ev);
	return mSwipeDetector.isDraggingOrSettling() ||
	       !mLauncher.getDragLayer().isEventOverView(mContent, ev);
}

@Override
public boolean onControllerTouchEvent(final MotionEvent ev) {
	mSwipeDetector.onTouchEvent(ev);
	// If we got ACTION_UP without ever starting swipe, close the panel.
	if ((ev.getAction() == MotionEvent.ACTION_UP &&
	     mSwipeDetector.isIdleState()) && (!mLauncher.getDragLayer().isEventOverView(mContent, ev))) {
		close(true);
	}
	return true;
}

/* SwipeDetector.Listener */

@Override
public void onDragStart(final boolean start) {
}

@Override
public boolean onDrag(final float displacement, final float velocity) {
	float range = mContent.getHeight();
	displacement = Utilities.boundToRange(displacement, 0, range);
	setTranslationShift(displacement / range);
	return true;
}

@Override
public void onDragEnd(final float velocity, final boolean fling) {
	if ((fling && velocity > 0) || mTranslationShift > 0.5f) {
		mScrollInterpolator = scrollInterpolatorForVelocity(velocity);
		mOpenCloseAnimator.setDuration(SwipeDetector.calculateDuration(
						       velocity, TRANSLATION_SHIFT_CLOSED - mTranslationShift));
		close(true);
	} else {
		mOpenCloseAnimator.setValues(PropertyValuesHolder.ofFloat(
						     TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
		mOpenCloseAnimator
		.setDuration(
			SwipeDetector.calculateDuration(velocity, mTranslationShift))
		.setInterpolator(Interpolators.DEACCEL);
		mOpenCloseAnimator.start();
	}
}

protected void handleClose(final boolean animate,
                           final long defaultDuration) {
	if (mIsOpen && !animate) {
		mOpenCloseAnimator.cancel();
		setTranslationShift(TRANSLATION_SHIFT_CLOSED);
		onCloseComplete();
		return;
	}
	if (!mIsOpen || mOpenCloseAnimator.isRunning()) {
		return;
	}
	mOpenCloseAnimator.setValues(PropertyValuesHolder.ofFloat(
					     TRANSLATION_SHIFT, TRANSLATION_SHIFT_CLOSED));
	mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(final Animator animation) {
			        onCloseComplete();
			}
		});
	if (mSwipeDetector.isIdleState()) {
		mOpenCloseAnimator.setDuration(defaultDuration)
		.setInterpolator(Interpolators.ACCEL);
	} else {
		mOpenCloseAnimator.setInterpolator(mScrollInterpolator);
	}
	mOpenCloseAnimator.start();
}

protected void onCloseComplete() {
	mIsOpen = false;
	mLauncher.getDragLayer().removeView(this);
}
}
