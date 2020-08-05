package com.android.launcher3.util;

import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import java.util.Arrays;

/**
 * Creates a hash key based on package name and user.
 */
public class PackageUserKey {

  public String mPackageName;
  public UserHandle mUser;
  private int mHashCode;

  public PackageUserKey(final String packageName, final UserHandle user) {
    update(packageName, user);
  }

  public static PackageUserKey fromItemInfo(final ItemInfo info) {
    return new PackageUserKey(info.getTargetComponent().getPackageName(),
                              info.user);
  }

  public static PackageUserKey
  fromNotification(final StatusBarNotification notification) {
    return new PackageUserKey(notification.getPackageName(),
                              notification.getUser());
  }

  private void update(final String packageName, final UserHandle user) {
    mPackageName = packageName;
    mUser = user;
    mHashCode = Arrays.hashCode(new Object[] {packageName, user});
  }

  /**
   * This should only be called to avoid new object creations in a loop.
   *
   * @return Whether this PackageUserKey was successfully updated - it shouldn't
   *     be used if not.
   */
  public boolean updateFromItemInfo(final ItemInfo info) {
    if (DeepShortcutManager.supportsShortcuts(info)) {
      update(info.getTargetComponent().getPackageName(), info.user);
      return true;
    }
    return false;
  }

  public boolean updateFromNotification(final StatusBarNotification sbn) {
    update(sbn.getPackageName(), sbn.getUser());
    return true;
  }

  @Override
  public int hashCode() {
    return mHashCode;
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof PackageUserKey))
      return false;
    PackageUserKey otherKey = (PackageUserKey)obj;
    return mPackageName.equals(otherKey.mPackageName) &&
        mUser.equals(otherKey.mUser);
  }
}
