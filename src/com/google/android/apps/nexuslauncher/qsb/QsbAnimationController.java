package com.google.android.apps.nexuslauncher.qsb;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherRootView;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.anim.Interpolators;

public class QsbAnimationController implements LauncherRootView.WindowStateListener, LauncherStateManager.StateListener {
    AnimatorSet mAnimatorSet;
    public boolean mGoogleHasFocus;
    private boolean mSearchRequested;
    private final Launcher mLauncher;

    public QsbAnimationController(final Launcher launcher) {
        mLauncher = launcher;
        mLauncher.getStateManager().addStateListener(this);
        mLauncher.getRootView().setWindowStateListener(this);
    }

    public final void dZ() {
        if (mLauncher.hasWindowFocus()) {
            mSearchRequested = true;
        } else {
            openQsb();
        }
    }

    public AnimatorSet openQsb() {
        mSearchRequested = false;
        playAnimation(mGoogleHasFocus = true, true);
        return mAnimatorSet;
    }

    public final void z(final boolean z) {
        mSearchRequested = false;
        if (mGoogleHasFocus) {
            mGoogleHasFocus = false;
            playAnimation(false, z);
        }
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        if (hasFocus || !mSearchRequested) {
            if (hasFocus) {
                z(true);
            }
            return;
        }
        openQsb();
    }

    @Override
    public void onWindowVisibilityChanged(final int visibility) {
        z(false);
    }

    private void playAnimation(final boolean z, final boolean z2) {
        if (mAnimatorSet != null) {
            mAnimatorSet.cancel();
            mAnimatorSet = null;
        }
        View view = mLauncher.getDragLayer();
        if (mLauncher.isInState(LauncherState.ALL_APPS)) {
            view.setAlpha(1.0f);
            view.setTranslationY(0.0f);
            return;
        }
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                if (animation == mAnimatorSet) {
                    mAnimatorSet = null;
                }
            }
        });
        if (z) {
            mAnimatorSet.play(ObjectAnimator.ofFloat(view, View.ALPHA, 0f));
            Animator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, (float) ((-mLauncher.getHotseat().getHeight()) / 2));
            animator.setInterpolator(Interpolators.ACCEL);
            mAnimatorSet.play(animator);
            mAnimatorSet.setDuration(200);
        } else {
            mAnimatorSet.play(ObjectAnimator.ofFloat(view, View.ALPHA, 1f));
            Animator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f);
            animator.setInterpolator(Interpolators.DEACCEL);
            mAnimatorSet.play(animator);
            mAnimatorSet.setDuration(200);
        }
        mAnimatorSet.start();
        if (!z2) {
            mAnimatorSet.end();
        }
    }

    public void onStateTransitionStart(final LauncherState launcherState) {
    }

    public void onStateTransitionComplete(final LauncherState launcherState) {
        a(launcherState);
    }

    public void onStateSetImmediately(final LauncherState launcherState) {
        a(launcherState);
    }

    private void a(final LauncherState launcherState) {
        if (mGoogleHasFocus && launcherState != LauncherState.ALL_APPS && !mLauncher.hasWindowFocus()) {
            playAnimation(true, false);
        }
    }
}
