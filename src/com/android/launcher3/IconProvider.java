package com.android.launcher3;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.util.Locale;
import org.zimmob.zimlx.iconpack.AdaptiveIconCompat;

public class IconProvider {

  protected String mSystemState;

  public static IconProvider newInstance(final Context context) {
    IconProvider provider = Utilities.getOverrideObject(
        IconProvider.class, context, R.string.icon_provider_class);
    provider.updateSystemStateString(context);
    return provider;
  }

  public IconProvider() { updateSystemStateString(); }

  public void updateSystemStateString() {
    mSystemState = Locale.getDefault().toString() + "," + Build.VERSION.SDK_INT;
  }

  public void updateSystemStateString(final Context context) {
    final String locale;
    if (Utilities.ATLEAST_NOUGAT) {
      locale = context.getResources()
                   .getConfiguration()
                   .getLocales()
                   .toLanguageTags();
    } else {
      locale = Locale.getDefault().toString();
    }

    mSystemState = locale + "," + Build.VERSION.SDK_INT;
  }

  public String getIconSystemState(final String packageName) {
    return mSystemState;
  }

  /**
   * @param flattenDrawable true if the caller does not care about the
   *     specification of the
   *                        original icon as long as the flattened version looks
   * the same.
   */
  public Drawable getIcon(final LauncherActivityInfo info, final int iconDpi,
                          final boolean flattenDrawable) {
    return AdaptiveIconCompat.wrap(info.getIcon(iconDpi));
  }
}
