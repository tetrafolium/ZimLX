package com.google.android.apps.nexuslauncher.qsb;

import android.content.pm.LauncherActivityInfo;
import android.graphics.Rect;
import android.view.View;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.compat.ShortcutConfigActivityInfo;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingItemDragHelper;

public class ItemDragListener extends BaseItemDragListener {
  private final LauncherActivityInfo mActivityInfo;

  public ItemDragListener(final LauncherActivityInfo activityInfo,
                          final Rect rect) {
    super(rect, rect.width(), rect.width());
    mActivityInfo = activityInfo;
  }

  protected PendingItemDragHelper createDragHelper() {
    PendingAddShortcutInfo tag = new PendingAddShortcutInfo(
        new ShortcutConfigActivityInfo.ShortcutConfigActivityInfoVO(
            mActivityInfo) {
          @Override
          public ShortcutInfo createShortcutInfo() {
            return InstallShortcutReceiver.fromActivityInfo(mActivityInfo,
                                                            mLauncher);
          }
        });
    View view = new View(mLauncher);
    view.setTag(tag);
    return new PendingItemDragHelper(view);
  }

  @Override
  public void
  fillInLogContainerData(final View v, final ItemInfo info,
                         final LauncherLogProto.Target target,
                         final LauncherLogProto.Target targetParent) {}
}
