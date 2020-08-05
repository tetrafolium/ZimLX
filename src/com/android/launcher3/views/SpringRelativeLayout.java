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

import static androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY;
import static androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW;
import static androidx.dynamicanimation.animation.SpringForce.STIFFNESS_MEDIUM;
import static androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.EdgeEffect;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

public class SpringRelativeLayout extends RelativeLayout {

  private static final float STIFFNESS = (STIFFNESS_MEDIUM + STIFFNESS_LOW) / 2;
  private static final float DAMPING_RATIO = DAMPING_RATIO_MEDIUM_BOUNCY;
  private static final float VELOCITY_MULTIPLIER = 0.3f;

  private static final FloatPropertyCompat<SpringRelativeLayout> DAMPED_SCROLL =
      new FloatPropertyCompat<SpringRelativeLayout>("value") {
        @Override
        public float getValue(final SpringRelativeLayout object) {
          return object.mDampedScrollShift;
        }

        @Override
        public void setValue(final SpringRelativeLayout object,
                             final float value) {
          object.setDampedScrollShift(value);
        }
      };

  protected final SparseBooleanArray mSpringViews = new SparseBooleanArray();
  private final SpringAnimation mSpring;

  private float mDampedScrollShift = 0;
  private SpringEdgeEffect mActiveEdge;

  public SpringRelativeLayout(final Context context) { this(context, null); }

  public SpringRelativeLayout(final Context context, final AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SpringRelativeLayout(final Context context, final AttributeSet attrs,
                              final int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    mSpring = new SpringAnimation(this, DAMPED_SCROLL, 0);
    mSpring.setSpring(
        new SpringForce(0).setStiffness(STIFFNESS).setDampingRatio(
            DAMPING_RATIO));
  }

  public void addSpringView(final int id) { mSpringViews.put(id, true); }

  public void removeSpringView(final int id) {
    mSpringViews.delete(id);
    invalidate();
  }

  /**
   * Used to clip the canvas when drawing child views during overscroll.
   */
  public int getCanvasClipTopForOverscroll() { return 0; }

  @Override
  protected boolean drawChild(final Canvas canvas, final View child,
                              final long drawingTime) {
    if (mDampedScrollShift != 0 && mSpringViews.get(child.getId())) {
      int saveCount = canvas.save();

      canvas.clipRect(0, getCanvasClipTopForOverscroll(), getWidth(),
                      getHeight());
      canvas.translate(0, mDampedScrollShift);
      boolean result = super.drawChild(canvas, child, drawingTime);

      canvas.restoreToCount(saveCount);

      return result;
    }
    return super.drawChild(canvas, child, drawingTime);
  }

  private void setActiveEdge(final SpringEdgeEffect edge) {
    if (mActiveEdge != edge && mActiveEdge != null) {
      mActiveEdge.mDistance = 0;
    }
    mActiveEdge = edge;
  }

  protected void setDampedScrollShift(final float shift) {
    if (shift != mDampedScrollShift) {
      mDampedScrollShift = shift;
      invalidate();
    }
  }

  private void finishScrollWithVelocity(final float velocity) {
    mSpring.setStartVelocity(velocity);
    mSpring.setStartValue(mDampedScrollShift);
    mSpring.start();
  }

  protected void finishWithShiftAndVelocity(
      final float shift, final float velocity,
      final DynamicAnimation.OnAnimationEndListener listener) {
    setDampedScrollShift(shift);
    mSpring.addEndListener(listener);
    finishScrollWithVelocity(velocity);
  }

  public EdgeEffectFactory createEdgeEffectFactory() {
    return new SpringEdgeEffectFactory();
  }

  private class SpringEdgeEffectFactory extends EdgeEffectFactory {

    @NonNull
    @Override
    protected EdgeEffect createEdgeEffect(final RecyclerView view,
                                          final int direction) {
      switch (direction) {
      case DIRECTION_TOP:
        return new SpringEdgeEffect(getContext(), +VELOCITY_MULTIPLIER);
      case DIRECTION_BOTTOM:
        return new SpringEdgeEffect(getContext(), -VELOCITY_MULTIPLIER);
      }
      return super.createEdgeEffect(view, direction);
    }
  }

  private class SpringEdgeEffect extends EdgeEffect {

    private final float mVelocityMultiplier;

    private float mDistance;

    public SpringEdgeEffect(final Context context,
                            final float velocityMultiplier) {
      super(context);
      mVelocityMultiplier = velocityMultiplier;
    }

    @Override
    public boolean draw(final Canvas canvas) {
      return false;
    }

    @Override
    public void onAbsorb(final int velocity) {
      finishScrollWithVelocity(velocity * mVelocityMultiplier);
    }

    @Override
    public void onPull(final float deltaDistance, final float displacement) {
      setActiveEdge(this);
      mDistance += deltaDistance * (mVelocityMultiplier / 3f);
      setDampedScrollShift(mDistance * getHeight());
    }

    @Override
    public void onRelease() {
      mDistance = 0;
      finishScrollWithVelocity(0);
    }
  }
}
