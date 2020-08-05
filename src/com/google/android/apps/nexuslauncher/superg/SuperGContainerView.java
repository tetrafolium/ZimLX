package com.google.android.apps.nexuslauncher.superg;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import org.zimmob.zimlx.ZimUtilsKt;

public class SuperGContainerView extends BaseGContainerView {

  private int mQsbColor = Color.WHITE;

  public SuperGContainerView(final Context paramContext) {
    this(paramContext, null);
  }

  public SuperGContainerView(final Context paramContext,
                             final AttributeSet paramAttributeSet) {
    this(paramContext, paramAttributeSet, 0);
  }

  public SuperGContainerView(final Context paramContext,
                             final AttributeSet paramAttributeSet,
                             final int paramInt) {
    super(paramContext, paramAttributeSet, paramInt);
    View.inflate(paramContext, R.layout.qsb_blocker_view, this);
  }

  @Override
  protected int getQsbView(final boolean withMic) {
    return R.layout.qsb_without_mic;
  }

  @Override
  public void setPadding(final int left, final int top, final int right,
                         final int bottom) {
    super.setPadding(0, 0, 0, 0);
  }

  @Override
  protected void onMeasure(final int widthMeasureSpec,
                           final int heightMeasureSpec) {
    int qsbOverlapMargin =
        -getResources().getDimensionPixelSize(R.dimen.qsb_overlap_margin);
    DeviceProfile deviceProfile =
        LauncherAppState.getIDP(getContext()).getDeviceProfile(getContext());
    int size = MeasureSpec.getSize(widthMeasureSpec) - qsbOverlapMargin;

    int qsbWidth;
    int marginStart;
    if (deviceProfile.isVerticalBarLayout()) {
      qsbWidth = size;
      marginStart = qsbOverlapMargin + getResources().getDimensionPixelSize(
                                           R.dimen.qsb_button_elevation);
    } else {
      Rect workspacePadding = deviceProfile.workspacePadding;
      int fullWidth = size - workspacePadding.left - workspacePadding.right;
      qsbWidth = DeviceProfile.calculateCellWidth(
                     fullWidth, deviceProfile.inv.numColumns) *
                 deviceProfile.inv.numColumns;
      marginStart = 0;
    }

    if (mQsbView != null) {
      LayoutParams layoutParams = (LayoutParams)mQsbView.getLayoutParams();
      layoutParams.width = qsbWidth / deviceProfile.inv.numColumns;
      if (deviceProfile.isVerticalBarLayout()) {
        layoutParams.width =
            Math.max(layoutParams.width, getResources().getDimensionPixelSize(
                                             R.dimen.qsb_min_width_with_mic));
      } else {
        layoutParams.width =
            Math.max(layoutParams.width, getResources().getDimensionPixelSize(
                                             R.dimen.qsb_min_width_portrait));
      }
      layoutParams.setMarginStart(marginStart);
      layoutParams.resolveLayoutDirection(layoutParams.getLayoutDirection());
    }
    if (mConnectorView != null) {
      LayoutParams layoutParams =
          (LayoutParams)mConnectorView.getLayoutParams();
      layoutParams.width = marginStart + layoutParams.height / 2;
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void setGoogleAnimationStart(final Rect rect, final Intent intent) {

  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
  }

  @Override
  protected void applyQsbColor() {
    super.applyQsbColor();
    float radius = ZimUtilsKt.dpToPx(100);
    mQsbView.setBackground(
        ZimUtilsKt.createRipplePill(getContext(), mQsbColor, radius));
  }
}
