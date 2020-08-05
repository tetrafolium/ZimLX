/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.dynamicui;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Saves and loads colors extracted from the wallpaper, as well as the
 * associated wallpaper id.
 */
public class ExtractedColors {
  public static final int DEFAULT_LIGHT = Color.WHITE;
  public static final int DEFAULT_DARK = Color.BLACK;
  // These color profile indices should NOT be changed, since they are used when
  // saving and loading extracted colors. New colors should always be added at
  // the end.
  public static final int VERSION_INDEX = 0;
  public static final int HOTSEAT_INDEX = 1;
  public static final int STATUS_BAR_INDEX = 2;
  public static final int WALLPAPER_VIBRANT_INDEX = 3;
  public static final int ALLAPPS_GRADIENT_MAIN_INDEX = 4;
  public static final int ALLAPPS_GRADIENT_SECONDARY_INDEX = 5;
  private static final String TAG = "ExtractedColors";
  private static final int VERSION;
  private static final int[] DEFAULT_VALUES;
  private static final String COLOR_SEPARATOR = ",";

  private static final int HOTSEAT_LIGHT_MUTED_INDEX = 1;
  private static final int HOTSEAT_DARK_MUTED_INDEX = 12;
  private static final int HOTSEAT_LIGHT_VIBRANT_INDEX = 13;
  private static final int HOTSEAT_DARK_VIBRANT_INDEX = 14;
  private static final int IS_SUPER_LIGHT = 15;
  private static final int IS_SUPER_DARK = 16;
  static {
    if (FeatureFlags.LAUNCHER3_GRADIENT_ALL_APPS) {
      VERSION = 3;
      DEFAULT_VALUES = new int[] {
          VERSION,      // VERSION_INDEX
          0x40FFFFFF,   // HOTSEAT_INDEX: White with 25% alpha
          DEFAULT_DARK, // STATUS_BAR_INDEX
          0xFFCCCCCC,   // WALLPAPER_VIBRANT_INDEX
          0xFF000000,   // ALLAPPS_GRADIENT_MAIN_INDEX
          0xFF000000    // ALLAPPS_GRADIENT_SECONDARY_INDEX
      };
    } else if (FeatureFlags.QSB_IN_HOTSEAT) {
      VERSION = 2;
      DEFAULT_VALUES = new int[] {
          VERSION,      // VERSION_INDEX
          0x40FFFFFF,   // HOTSEAT_INDEX: White with 25% alpha
          DEFAULT_DARK, // STATUS_BAR_INDEX
          0xFFCCCCCC,   // WALLPAPER_VIBRANT_INDEX
      };
    } else {
      VERSION = 1;
      DEFAULT_VALUES = new int[] {
          VERSION,      // VERSION_INDEX
          0x40FFFFFF,   // HOTSEAT_INDEX: White with 25% alpha
          DEFAULT_DARK, // STATUS_BAR_INDEX
      };
    }
  }

  private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();
  private final int[] mColors;

  public ExtractedColors() {
    // The first entry is reserved for the version number.
    mColors = Arrays.copyOf(DEFAULT_VALUES, DEFAULT_VALUES.length);
  }

  public void setColorAtIndex(final int index, final int color) {
    if (index > VERSION_INDEX && index < mColors.length) {
      mColors[index] = color;
    } else {
      Log.e(TAG, "Attempted to set a color at an invalid index " + index);
    }
  }

  /**
   * Encodes {@link #mColors} as a comma-separated String.
   */
  String encodeAsString() {
    StringBuilder colorsStringBuilder = new StringBuilder();
    for (int color : mColors) {
      colorsStringBuilder.append(color).append(COLOR_SEPARATOR);
    }
    return colorsStringBuilder.toString();
  }

  /**
   * Loads colors and wallpaper id from {@link Utilities#getPrefs(Context)}.
   * These were saved there in {@link ColorExtractionService}.
   */
  public void load(final Context context) {
    String encodedString = Utilities.getPrefs(context).getString(
        ExtractionUtils.EXTRACTED_COLORS_PREFERENCE_KEY, VERSION + "");

    String[] splitColorsString = encodedString.split(COLOR_SEPARATOR);
    if (splitColorsString.length == DEFAULT_VALUES.length &&
        Integer.parseInt(splitColorsString[VERSION_INDEX]) == VERSION) {
      // Parse and apply the saved values.
      for (int i = 0; i < mColors.length; i++) {
        mColors[i] = Integer.parseInt(splitColorsString[i]);
      }
    } else {
      // Leave the values as default values as the saved values may not be
      // compatible.
      ExtractionUtils.startColorExtractionService(context);
    }
  }

  /**
   * @param index must be one of the index values defined at the top of this
   *     class.
   */
  public int getColor(final int index) { return mColors[index]; }

  /**
   * @param index must be one of the index values defined at the top of this
   *     class.
   */
  public int getColor(final int index, final int defaultColor) {
    if (index > VERSION_INDEX && index < mColors.length) {
      int color = mColors[index];
      return color == -1 ? defaultColor : color;
    }
    return defaultColor;
  }
  /**
   * The hotseat's color is defined as follows:
   * - 12% black for super light wallpaper
   * - 18% white for super dark
   * - 25% white otherwise
   */
  public void updateHotseatPalette(final Palette hotseatPalette) {
    int hotseatColor;
    if (hotseatPalette != null &&
        ExtractionUtils.isSuperLight(hotseatPalette)) {
      hotseatColor =
          ColorUtils.setAlphaComponent(Color.BLACK, (int)(0.12f * 255));
    } else if (hotseatPalette != null &&
               ExtractionUtils.isSuperDark(hotseatPalette)) {
      hotseatColor =
          ColorUtils.setAlphaComponent(Color.WHITE, (int)(0.18f * 255));
    } else {
      hotseatColor = DEFAULT_VALUES[HOTSEAT_INDEX];
    }
    setColorAtIndex(HOTSEAT_INDEX, hotseatColor);
  }

  public void updateStatusBarPalette(final Palette statusBarPalette) {
    setColorAtIndex(STATUS_BAR_INDEX,
                    ExtractionUtils.isSuperLight(statusBarPalette)
                        ? DEFAULT_LIGHT
                        : DEFAULT_DARK);
  }

  public void updateWallpaperThemePalette(final
                                          @Nullable Palette wallpaperPalette) {
    int defaultColor = DEFAULT_VALUES[WALLPAPER_VIBRANT_INDEX];
    setColorAtIndex(WALLPAPER_VIBRANT_INDEX,
                    wallpaperPalette == null
                        ? defaultColor
                        : wallpaperPalette.getVibrantColor(defaultColor));
  }

  public void addOnChangeListener(final OnChangeListener listener) {
    mListeners.add(listener);
  }

  public void notifyChange() {
    for (OnChangeListener listener : mListeners) {
      listener.onExtractedColorsChanged();
    }
  }

  /**
   * The hotseat's color is defined as follows:
   * - 20% darkMuted or 12% black for super light wallpaper
   * - 25% lightMuted or 18% white for super dark
   * - 40% lightVibrant or 25% white otherwise
   */
  /*public int getDockColor(Context context) {
      if (Utilities.getZimPrefs(context).getTransparentDock()) {
          return Color.TRANSPARENT;
      }
      int hotseatColor;
      boolean shouldUseExtractedColors =
  Utilities.getZimPrefs(context).getDockShouldUseExtractedColors(); if
  (getColor(IS_SUPER_LIGHT, 0) == 1) { if (shouldUseExtractedColors) { int
  baseColor = getColor(HOTSEAT_DARK_MUTED_INDEX,
  getColor(HOTSEAT_DARK_VIBRANT_INDEX, Color.BLACK)); hotseatColor =
  ColorUtils.setAlphaComponent(baseColor, (int) (0.20f * 255)); } else {
              hotseatColor = ColorUtils.setAlphaComponent(Color.BLACK, (int)
  (0.12f * 255));
          }
      } else if (getColor(IS_SUPER_DARK, 0) == 1) {
          if (shouldUseExtractedColors) {
              int baseColor = getColor(HOTSEAT_LIGHT_MUTED_INDEX,
  getColor(HOTSEAT_LIGHT_VIBRANT_INDEX, Color.WHITE)); hotseatColor =
  ColorUtils.setAlphaComponent(baseColor, (int) (0.25f * 255)); } else {
              hotseatColor = ColorUtils.setAlphaComponent(Color.BLACK, (int)
  (0.12f * 255));
          }
      } else {
          if (shouldUseExtractedColors) {
              int baseColor = getColor(HOTSEAT_LIGHT_VIBRANT_INDEX,
  getColor(HOTSEAT_LIGHT_MUTED_INDEX, Color.WHITE)); hotseatColor =
  ColorUtils.setAlphaComponent(baseColor, (int) (0.40f * 255)); } else {
              hotseatColor = ColorUtils.setAlphaComponent(Color.BLACK, (int)
  (0.12f * 255));
          }
      }
      boolean useCustomOpacity =
  Utilities.getZimPrefs(context).getDockShouldUseCustomOpacity(); if
  (useCustomOpacity) { float customOpacity =
  Utilities.getZimPrefs(context).getDockCustomOpacity(); hotseatColor =
  ColorUtils.setAlphaComponent(hotseatColor, (int) (customOpacity * 255));
      }
      return hotseatColor;
  }
  */
  /**
   * Interface for listening for extracted color changes
   */
  public interface OnChangeListener { void onExtractedColorsChanged(); }
}
