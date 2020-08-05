package com.android.launcher3.shortcuts;

import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.util.ComponentKey;

/**
 * A key that uniquely identifies a shortcut using its package, id, and user
 * handle.
 */
public class ShortcutKey extends ComponentKey {

  public ShortcutKey(final String packageName, final UserHandle user,
                     final String id) {
    // Use the id as the class name.
    super(new ComponentName(packageName, id), user);
  }

  public static ShortcutKey fromInfo(final ShortcutInfoCompat shortcutInfo) {
    return new ShortcutKey(shortcutInfo.getPackage(),
                           shortcutInfo.getUserHandle(), shortcutInfo.getId());
  }

  public static ShortcutKey fromIntent(final Intent intent,
                                       final UserHandle user) {
    String shortcutId =
        intent.getStringExtra(ShortcutInfoCompat.EXTRA_SHORTCUT_ID);
    return new ShortcutKey(intent.getPackage(), user, shortcutId);
  }

  public static ShortcutKey fromItemInfo(final ItemInfo info) {
    return fromIntent(info.getIntent(), info.user);
  }

  public String getId() { return componentName.getClassName(); }
}
