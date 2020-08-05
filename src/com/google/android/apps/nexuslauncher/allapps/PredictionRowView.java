/*
 * Copyright (C) 2018 CypherOS
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

package com.google.android.apps.nexuslauncher.allapps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.StaticLayout.Builder;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AllAppsStore.OnUpdateListener;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.keyboard.FocusIndicatorHelper;
import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.Themes;

import org.zimmob.zimlx.anim.AnimatedFloat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.launcher3.userevent.nano.LauncherLogProto.Target;

public class PredictionRowView extends LinearLayout implements UserEventDispatcher.LogContainerProvider,
    OnUpdateListener, OnDeviceProfileChangeListener {
    private static final Interpolator ALPHA_FACTOR_INTERPOLATOR = input -> input < 0.8f ? 0.0f : (input - 0.8f) / 0.2f;
    private static final String TAG = "PredictionRowView";

    private static final Property<PredictionRowView, Integer> TEXT_ALPHA = new Property<PredictionRowView, Integer>(Integer.class, "textAlpha") {
        @Override
        public void set(PredictionRowView predictionRowView, Integer value) {
            predictionRowView.setTextAlpha(value);
        }

        @Override
        public Integer get(PredictionRowView predictionRowView) {
            return predictionRowView.mIconCurrentTextAlpha;
        }
    };

    private Layout mAllAppsLabelLayout;
    @ColorInt
    private int mAllAppsLabelTextColor;
    private int mAllAppsLabelTextCurrentAlpha;
    private final int mAllAppsLabelTextFullAlpha;
    private final TextPaint mAllAppsLabelTextPaint;
    private final AnimatedFloat mContentAlphaFactor;
    private DividerType mDividerType;
    private final FocusIndicatorHelper mFocusHelper;
    private int mIconCurrentTextAlpha;
    private int mIconFullTextAlpha;
    private int mIconTextColor;
    private boolean mIsCollapsed;
    private final Launcher mLauncher;
    private View mLoadingProgress;
    private final int mNumPredictedAppsPerRow;
    private final AnimatedFloat mOverviewScrollFactor;
    private final Paint mPaint;
    private PredictionsFloatingHeader mParent;
    private final List<ComponentKeyMapper> mPredictedAppComponents;
    private final ArrayList<ItemInfoWithIcon> mPredictedApps;
    private boolean mPredictionsEnabled;
    private float mScrollTranslation;
    private boolean mScrolledOut;
    private int mStrokeColor;
    private Typeface mAllAppsLabelTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    public enum DividerType {
        NONE,
        LINE,
        ALL_APPS_LABEL
    }

    public boolean hasOverlappingRendering() {
        return false;
    }

    public PredictionRowView(@NonNull Context context) {
        this(context, null);
    }

    public PredictionRowView(@NonNull Context context, @Nullable AttributeSet attributeSet) {
        super(context, attributeSet);
        mPredictedAppComponents = new ArrayList<>();
        mPredictedApps = new ArrayList<>();
        mAllAppsLabelTextPaint = new TextPaint();
        mScrollTranslation = 0f;
        mContentAlphaFactor = new AnimatedFloat(this::updateTranslationAndAlpha);
        mOverviewScrollFactor = new AnimatedFloat(this::updateTranslationAndAlpha);
        mIsCollapsed = false;
        mPredictionsEnabled = false;
        setOrientation(LinearLayout.HORIZONTAL);
        setWillNotDraw(false);
        boolean isMainColorDark = Themes.getAttrBoolean(context, R.attr.isMainColorDark);
        mPaint = new Paint();
        mPaint.setColor(ContextCompat.getColor(context, isMainColorDark ? R.color.all_apps_prediction_row_separator_dark : R.color.all_apps_prediction_row_separator));
        mPaint.setStrokeWidth((float) context.getResources().getDimensionPixelSize(R.dimen.all_apps_divider_height));
        mStrokeColor = mPaint.getColor();
        mFocusHelper = new SimpleFocusIndicatorHelper(this);
        mNumPredictedAppsPerRow = LauncherAppState.getIDP(context).numPredictions; // TODO: make a separated pref
        mLauncher = Launcher.getLauncher(context);
        mLauncher.addOnDeviceProfileChangeListener(this);

        mIconTextColor = Color.BLACK;
        mIconFullTextAlpha = Color.alpha(mIconTextColor);
        mIconCurrentTextAlpha = mIconFullTextAlpha;

        mAllAppsLabelTextPaint.setColor(ContextCompat.getColor(context, isMainColorDark ? R.color.all_apps_label_text_dark : R.color.all_apps_label_text));
        mAllAppsLabelTextColor = mAllAppsLabelTextPaint.getColor();
        mAllAppsLabelTextFullAlpha = Color.alpha(mAllAppsLabelTextColor);
        mAllAppsLabelTextCurrentAlpha = mAllAppsLabelTextFullAlpha;
        updateVisibility();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getAppsStore().addUpdateListener(this);
        getAppsStore().registerIconContainer(this);
    }

    private AllAppsStore getAppsStore() {
        return this.mLauncher.getAppsView().getAppsStore();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getAppsStore().removeUpdateListener(this);
        getAppsStore().unregisterIconContainer(this);
    }

    public void setup(PredictionsFloatingHeader predictionsFloatingHeader, boolean z) {
        this.mParent = predictionsFloatingHeader;
        setPredictionsEnabled(z);
    }

    private void setPredictionsEnabled(boolean z) {
        if (z != this.mPredictionsEnabled) {
            this.mPredictionsEnabled = z;
            updateVisibility();
        }
    }

    private void updateVisibility() {
        setVisibility((!this.mPredictionsEnabled || this.mIsCollapsed) ? View.GONE : View.VISIBLE);
    }

    protected void onMeasure(int i, int i2) {
        super.onMeasure(i, View.MeasureSpec.makeMeasureSpec(getExpectedHeight(), MeasureSpec.EXACTLY));
    }

    protected void dispatchDraw(Canvas canvas) {
        this.mFocusHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    public int getExpectedHeight() {
        if (getVisibility() == View.GONE) {
            return 0;
        }
        DeviceProfile dp = Launcher.getLauncher(getContext()).getDeviceProfile();
        return dp.allAppsCellHeightPx + getPaddingBottom() + getPaddingTop();
    }

    public void setDividerType(DividerType dividerType, boolean force) {
        int i = 0;
        if (mDividerType != dividerType || force) {
            if (dividerType == DividerType.ALL_APPS_LABEL) {
                rebuildLabel();
            } else {
                mAllAppsLabelLayout = null;
            }
        }
        mDividerType = dividerType;
        if (mDividerType == DividerType.LINE) {
            i = getResources().getDimensionPixelSize(R.dimen.all_apps_prediction_row_divider_height);
        } else if (mDividerType == DividerType.ALL_APPS_LABEL) {
            i = getAllAppsLayoutFullHeight();
        }
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), i);
    }

    @SuppressLint("NewApi")
    private void rebuildLabel() {
        mAllAppsLabelTextPaint.setAntiAlias(true);
        mAllAppsLabelTextPaint.setTypeface(mAllAppsLabelTypeface);
        mAllAppsLabelTextPaint.setTextSize((float) getResources().getDimensionPixelSize(R.dimen.all_apps_label_text_size));
        CharSequence text = getResources().getText(R.string.all_apps_label);
        mAllAppsLabelLayout = Builder.obtain(text, 0, text.length(), mAllAppsLabelTextPaint, Math.round(mAllAppsLabelTextPaint.measureText(text.toString()))).setAlignment(
                                  Alignment.ALIGN_CENTER).setMaxLines(1).setIncludePad(true).build();
    }

    public List<ItemInfoWithIcon> getPredictedApps() {
        return this.mPredictedApps;
    }

    public void setPredictedApps(boolean z, List<ComponentKeyMapper> list) {
        setPredictionsEnabled(z);
        this.mPredictedAppComponents.clear();
        this.mPredictedAppComponents.addAll(list);
        onAppsUpdated();
    }

    public List<ComponentKeyMapper> getPredictedAppComponents() {
        return mPredictedAppComponents;
    }

    public void onDeviceProfileChanged(DeviceProfile deviceProfile) {
        removeAllViews();
        applyPredictionApps();
    }

    public void onAppsUpdated() {
        mPredictedApps.clear();
        mPredictedApps.addAll(processPredictedAppComponents(mPredictedAppComponents));
        applyPredictionApps();
    }

    private void applyPredictionApps() {
        if (mLoadingProgress != null) {
            removeView(mLoadingProgress);
        }
        if (getChildCount() != mNumPredictedAppsPerRow) {
            while (getChildCount() > mNumPredictedAppsPerRow) {
                removeViewAt(0);
            }
            while (getChildCount() < mNumPredictedAppsPerRow) {
                BubbleTextView icon = (BubbleTextView) mLauncher.getLayoutInflater().inflate(R.layout.all_apps_icon, this, false);
                icon.setOnClickListener(ItemClickHandler.INSTANCE);
                icon.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS);
                icon.setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
                icon.setOnFocusChangeListener(mFocusHelper);
                LayoutParams layoutParams = (LayoutParams) icon.getLayoutParams();
                layoutParams.height = mLauncher.getDeviceProfile().allAppsCellHeightPx;
                layoutParams.width = 0;
                layoutParams.weight = 1.0f;
                addView(icon);
            }
        }
        int predictionCount = mPredictedApps.size();
        int alphaComponent = ColorUtils.setAlphaComponent(mIconTextColor, mIconCurrentTextAlpha);

        for (int i = 0; i < getChildCount(); i++) {
            BubbleTextView icon = (BubbleTextView) getChildAt(i);
            icon.reset();
            if (predictionCount > i) {
                icon.setVisibility(View.VISIBLE);
                if (mPredictedApps.get(i) instanceof AppInfo) {
                    icon.applyFromApplicationInfo((AppInfo) mPredictedApps.get(i));
                } else if (mPredictedApps.get(i) instanceof ShortcutInfo) {
                    icon.applyFromShortcutInfo((ShortcutInfo) mPredictedApps.get(i));
                }
                icon.setTextColor(alphaComponent);
            } else {
                icon.setVisibility(predictionCount == 0 ? View.GONE : View.VISIBLE);
            }

        }
        if (predictionCount == 0) {
            if (mLoadingProgress == null) {
                mLoadingProgress = LayoutInflater.from(getContext()).inflate(R.layout.prediction_load_progress, this, false);
            }
            addView(mLoadingProgress);
        } else {
            mLoadingProgress = null;
        }
        mParent.headerChanged();
    }

    private List<ItemInfoWithIcon> processPredictedAppComponents(List<ComponentKeyMapper> list) {
        if (getAppsStore().getApps().isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemInfoWithIcon> arrayList = new ArrayList<>();
        for (ComponentKeyMapper app : list) {
            ItemInfoWithIcon app2 = app.getApp(getAppsStore());
            if (app2 != null) {
                arrayList.add(app2);
            }
            if (arrayList.size() == this.mNumPredictedAppsPerRow) {
                break;
            }
        }
        return arrayList;
    }

    protected void onDraw(Canvas canvas) {
        if (mDividerType == DividerType.LINE) {
            int dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
            float height = (float) (getHeight() - (getPaddingBottom() / 2));
            Canvas canvas2 = canvas;
            canvas2.drawLine((float) (getPaddingLeft() + dimensionPixelSize), height, (float) ((getWidth() - getPaddingRight()) - dimensionPixelSize), height, mPaint);
        } else if (mDividerType == DividerType.ALL_APPS_LABEL) {
            drawAllAppsHeader(canvas);
        }
    }

    public void fillInLogContainerData(View view, ItemInfo itemInfo, Target target, Target target2) {
        for (int i = 0; i < this.mPredictedApps.size(); i++) {
            if (this.mPredictedApps.get(i) == itemInfo) {
                target2.containerType = 7;
                target.predictedRank = i;
                return;
            }
        }
    }

    public void setScrolledOut(boolean z) {
        this.mScrolledOut = z;
        updateTranslationAndAlpha();
    }

    public void setTextAlpha(int i) {
        mIconCurrentTextAlpha = i;
        int alphaComponent = ColorUtils.setAlphaComponent(mIconTextColor, mIconCurrentTextAlpha);
        if (mLoadingProgress == null) {
            for (int i2 = 0; i2 < getChildCount(); i2++) {
                ((BubbleTextView) getChildAt(i2)).setTextColor(alphaComponent);
            }
        }
        i = ColorUtils.setAlphaComponent(mStrokeColor, Math.round(((float) (Color.alpha(mStrokeColor) * i)) / 255f));
        if (i != mPaint.getColor()) {
            mPaint.setColor(i);
            mAllAppsLabelTextCurrentAlpha = Math.round((mAllAppsLabelTextFullAlpha * mIconCurrentTextAlpha) / mIconFullTextAlpha);
            mAllAppsLabelTextPaint.setColor(ColorUtils.setAlphaComponent(mAllAppsLabelTextColor, mAllAppsLabelTextCurrentAlpha));
            if (mDividerType != DividerType.NONE) {
                invalidate();
            }
        }
    }

    public void setScrollTranslation(float f) {
        this.mScrollTranslation = f;
        updateTranslationAndAlpha();
    }

    private void updateTranslationAndAlpha() {
        setTranslationY((1.0f - mOverviewScrollFactor.value) * mScrollTranslation);
        float interpolation = ALPHA_FACTOR_INTERPOLATOR.getInterpolation(mOverviewScrollFactor.value);
        setAlpha(mContentAlphaFactor.value * (interpolation + ((1.0f - interpolation) * (mScrolledOut ? 0f : 1f))));
    }

    @SuppressLint("NewApi")
    public void setContentVisibility(boolean hasHeader, boolean hasContent, PropertySetter propertySetter, Interpolator interpolator) {
        int i = 0;
        boolean visible = getAlpha() > 0f;
        if (!hasHeader) {
            i = mIconCurrentTextAlpha;
        } else if (hasContent) {
            i = mIconFullTextAlpha;
        }
        if (!visible) {
            setTextAlpha(i);
        } else {
            propertySetter.setInt(this, TEXT_ALPHA, i, interpolator);
        }
        hasContent = hasHeader && !hasContent;
        propertySetter.setFloat(mOverviewScrollFactor, AnimatedFloat.VALUE, hasContent ? 1f : 0f, Interpolators.LINEAR);
        propertySetter.setFloat(mContentAlphaFactor, AnimatedFloat.VALUE, hasHeader ? 1f : 0f, interpolator);
    }

    private void drawAllAppsHeader(Canvas canvas) {
        drawAllAppsHeader(canvas, this, mAllAppsLabelLayout);
    }

    static void drawAllAppsHeader(Canvas canvas, View view, Layout allAppsLayout) {
        int width = (view.getWidth() / 2) - (allAppsLayout.getWidth() / 2);
        int height = view.getHeight() - view.getResources().getDimensionPixelSize(R.dimen.all_apps_label_bottom_padding) - allAppsLayout.getHeight();
        canvas.translate((float) width, (float) height);
        allAppsLayout.draw(canvas);
        canvas.translate((float) (-width), (float) (-height));
    }

    private int getAllAppsLayoutFullHeight() {
        return (this.mAllAppsLabelLayout.getHeight() + getResources().getDimensionPixelSize(R.dimen.all_apps_label_top_padding)) + getResources().getDimensionPixelSize(R.dimen.all_apps_label_bottom_padding);
    }

    public void setCollapsed(boolean z) {
        if (z != this.mIsCollapsed) {
            this.mIsCollapsed = z;
            updateVisibility();
        }
    }
}

