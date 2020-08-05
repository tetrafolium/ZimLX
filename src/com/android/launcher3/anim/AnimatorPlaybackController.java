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
package com.android.launcher3.anim;

import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class to control the playback of an {@link AnimatorSet}, with custom
 * interpolators and durations. <p> Note: The implementation does not support
 * start delays on child animations or sequential playbacks.
 */
public abstract class AnimatorPlaybackController
    implements ValueAnimator.AnimatorUpdateListener {

  public static AnimatorPlaybackController wrap(final AnimatorSet anim,
                                                final long duration) {
    return wrap(anim, duration, null);
  }

  /**
   * Creates an animation controller for the provided animation.
   * The actual duration does not matter as the animation is manually
   * controlled. It just needs to be larger than the total number of pixels so
   * that we don't have jittering due to float (animation-fraction * total
   * duration) to int conversion.
   */
  public static AnimatorPlaybackController
  wrap(final AnimatorSet anim, final long duration,
       final Runnable onCancelRunnable) {

    /**
     * TODO: use {@link AnimatorSet#setCurrentPlayTime(long)} once b/68382377 is
     * fixed.
     */
    return new AnimatorPlaybackControllerVL(anim, duration, onCancelRunnable);
  }

  private final ValueAnimator mAnimationPlayer;
  private final long mDuration;

  protected final AnimatorSet mAnim;

  protected float mCurrentFraction;
  private Runnable mEndAction;

  protected boolean mTargetCancelled = false;
  protected Runnable mOnCancelRunnable;

  protected AnimatorPlaybackController(final AnimatorSet anim,
                                       final long duration,
                                       final Runnable onCancelRunnable) {
    mAnim = anim;
    mDuration = duration;
    mOnCancelRunnable = onCancelRunnable;

    mAnimationPlayer = ValueAnimator.ofFloat(0, 1);
    mAnimationPlayer.setInterpolator(LINEAR);
    mAnimationPlayer.addListener(new OnAnimationEndDispatcher());
    mAnimationPlayer.addUpdateListener(this);

    mAnim.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationCancel(final Animator animation) {
        mTargetCancelled = true;
        if (mOnCancelRunnable != null) {
          mOnCancelRunnable.run();
          mOnCancelRunnable = null;
        }
      }

      @Override
      public void onAnimationEnd(final Animator animation) {
        mTargetCancelled = false;
        mOnCancelRunnable = null;
      }

      @Override
      public void onAnimationStart(final Animator animation) {
        mTargetCancelled = false;
      }
    });
  }

  public AnimatorSet getTarget() { return mAnim; }

  public long getDuration() { return mDuration; }

  public TimeInterpolator getInterpolator() {
    return mAnim.getInterpolator() != null ? mAnim.getInterpolator() : LINEAR;
  }

  /**
   * Starts playing the animation forward from current position.
   */
  public void start() {
    mAnimationPlayer.setFloatValues(mCurrentFraction, 1);
    mAnimationPlayer.setDuration(clampDuration(1 - mCurrentFraction));
    mAnimationPlayer.start();
  }

  /**
   * Starts playing the animation backwards from current position
   */
  public void reverse() {
    mAnimationPlayer.setFloatValues(mCurrentFraction, 0);
    mAnimationPlayer.setDuration(clampDuration(mCurrentFraction));
    mAnimationPlayer.start();
  }

  /**
   * Pauses the currently playing animation.
   */
  public void pause() { mAnimationPlayer.cancel(); }

  /**
   * Returns the underlying animation used for controlling the set.
   */
  public ValueAnimator getAnimationPlayer() { return mAnimationPlayer; }

  /**
   * Sets the current animation position and updates all the child animators
   * accordingly.
   */
  public abstract void setPlayFraction(float fraction);

  public float getProgressFraction() { return mCurrentFraction; }

  /**
   * Sets the action to be called when the animation is completed. Also clears
   * any previously set action.
   */
  public void setEndAction(final Runnable runnable) { mEndAction = runnable; }

  @Override
  public void onAnimationUpdate(final ValueAnimator valueAnimator) {
    setPlayFraction((float)valueAnimator.getAnimatedValue());
  }

  protected long clampDuration(final float fraction) {
    float playPos = mDuration * fraction;
    if (playPos <= 0) {
      return 0;
    } else {
      return Math.min((long)playPos, mDuration);
    }
  }

  public void dispatchOnStart() { dispatchOnStartRecursively(mAnim); }

  private void dispatchOnStartRecursively(final Animator animator) {
    for (AnimatorListener l : nonNullList(animator.getListeners())) {
      l.onAnimationStart(animator);
    }

    if (animator instanceof AnimatorSet) {
      for (Animator anim :
           nonNullList(((AnimatorSet)animator).getChildAnimations())) {
        dispatchOnStartRecursively(anim);
      }
    }
  }

  public void dispatchOnCancel() { dispatchOnCancelRecursively(mAnim); }

  private void dispatchOnCancelRecursively(final Animator animator) {
    for (AnimatorListener l : nonNullList(animator.getListeners())) {
      l.onAnimationCancel(animator);
    }

    if (animator instanceof AnimatorSet) {
      for (Animator anim :
           nonNullList(((AnimatorSet)animator).getChildAnimations())) {
        dispatchOnCancelRecursively(anim);
      }
    }
  }

  public void dispatchSetInterpolator(final TimeInterpolator interpolator) {
    dispatchSetInterpolatorRecursively(mAnim, interpolator);
  }

  private void
  dispatchSetInterpolatorRecursively(final Animator anim,
                                     final TimeInterpolator interpolator) {
    anim.setInterpolator(interpolator);
    if (anim instanceof AnimatorSet) {
      for (Animator child :
           nonNullList(((AnimatorSet)anim).getChildAnimations())) {
        dispatchSetInterpolatorRecursively(child, interpolator);
      }
    }
  }

  public void setOnCancelRunnable(final Runnable runnable) {
    mOnCancelRunnable = runnable;
  }

  public Runnable getOnCancelRunnable() { return mOnCancelRunnable; }

  public static class AnimatorPlaybackControllerVL
      extends AnimatorPlaybackController {

    private final ValueAnimator[] mChildAnimations;

    private AnimatorPlaybackControllerVL(final AnimatorSet anim,
                                         final long duration,
                                         final Runnable onCancelRunnable) {
      super(anim, duration, onCancelRunnable);

      // Build animation list
      ArrayList<ValueAnimator> childAnims = new ArrayList<>();
      getAnimationsRecur(mAnim, childAnims);
      mChildAnimations =
          childAnims.toArray(new ValueAnimator[childAnims.size()]);
    }

    private void getAnimationsRecur(final AnimatorSet anim,
                                    final ArrayList<ValueAnimator> out) {
      long forceDuration = anim.getDuration();
      TimeInterpolator forceInterpolator = anim.getInterpolator();
      for (Animator child : anim.getChildAnimations()) {
        if (forceDuration > 0) {
          child.setDuration(forceDuration);
        }
        if (forceInterpolator != null) {
          child.setInterpolator(forceInterpolator);
        }
        if (child instanceof ValueAnimator) {
          out.add((ValueAnimator)child);
        } else if (child instanceof AnimatorSet) {
          getAnimationsRecur((AnimatorSet)child, out);
        } else {
          throw new RuntimeException("Unknown animation type " + child);
        }
      }
    }

    @Override
    public void setPlayFraction(final float fraction) {
      mCurrentFraction = fraction;
      // Let the animator report the progress but don't apply the progress to
      // child animations if it has been cancelled.
      if (mTargetCancelled) {
        return;
      }
      long playPos = clampDuration(fraction);
      for (ValueAnimator anim : mChildAnimations) {
        anim.setCurrentPlayTime(Math.min(playPos, anim.getDuration()));
      }
    }
  }

  private class OnAnimationEndDispatcher extends AnimationSuccessListener {

    @Override
    public void onAnimationStart(final Animator animation) {
      mCancelled = false;
    }

    @Override
    public void onAnimationSuccess(final Animator animator) {
      dispatchOnEndRecursively(mAnim);
      if (mEndAction != null) {
        mEndAction.run();
      }
    }

    private void dispatchOnEndRecursively(final Animator animator) {
      for (AnimatorListener l : nonNullList(animator.getListeners())) {
        l.onAnimationEnd(animator);
      }

      if (animator instanceof AnimatorSet) {
        for (Animator anim :
             nonNullList(((AnimatorSet)animator).getChildAnimations())) {
          dispatchOnEndRecursively(anim);
        }
      }
    }
  }

  private static <T> List<T> nonNullList(final ArrayList<T> list) {
    return list == null ? Collections.emptyList() : list;
  }
}
