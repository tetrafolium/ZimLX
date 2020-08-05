package com.android.launcher3.dynamicui;

import android.content.Context;
import android.graphics.Color;
import android.util.Pair;

import com.android.launcher3.compat.WallpaperColorsCompat;
import com.android.launcher3.compat.WallpaperManagerCompat;

import java.util.ArrayList;

import static android.app.WallpaperManager.FLAG_SYSTEM;

public class WallpaperColorInfo implements WallpaperManagerCompat.OnColorsChangedListenerCompat {

    private static final int FALLBACK_COLOR = Color.WHITE;
    private static final Object sInstanceLock = new Object();
    private static WallpaperColorInfo sInstance;
    private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();
    private final WallpaperManagerCompat mWallpaperManager;
    private final ColorExtractionAlgorithm mExtractionType;
    private int mMainColor;
    private int mSecondaryColor;
    private boolean mIsDark;
    private boolean mSupportsDarkText;
    private OnThemeChangeListener mOnThemeChangeListener;

    private WallpaperColorInfo(final Context context) {
        mWallpaperManager = WallpaperManagerCompat.getInstance(context);
        mWallpaperManager.addOnColorsChangedListener(this);
        mExtractionType = ColorExtractionAlgorithm.newInstance(context);
        update(mWallpaperManager.getWallpaperColors(FLAG_SYSTEM));
    }

    public static WallpaperColorInfo getInstance(final Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new WallpaperColorInfo(context.getApplicationContext());
            }
            return sInstance;
        }
    }

    public int getMainColor() {
        return mMainColor;
    }

    public int getSecondaryColor() {
        return mSecondaryColor;
    }

    public boolean isDark() {
        return mIsDark;
    }

    public boolean supportsDarkText() {
        return mSupportsDarkText;
    }

    @Override
    public void onColorsChanged(final WallpaperColorsCompat colors, final int which) {
        if ((which & FLAG_SYSTEM) != 0) {
            boolean wasDarkTheme = mIsDark;
            boolean didSupportDarkText = mSupportsDarkText;
            update(colors);
            notifyChange(wasDarkTheme != mIsDark || didSupportDarkText != mSupportsDarkText);
        }
    }

    private void update(final WallpaperColorsCompat wallpaperColors) {
        Pair<Integer, Integer> colors = mExtractionType.extractInto(wallpaperColors);
        if (colors != null) {
            mMainColor = colors.first;
            mSecondaryColor = colors.second;
        } else {
            mMainColor = FALLBACK_COLOR;
            mSecondaryColor = FALLBACK_COLOR;
        }
        mSupportsDarkText = wallpaperColors != null && (wallpaperColors.getColorHints()
                            & WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) > 0;
        mIsDark = wallpaperColors != null && (wallpaperColors.getColorHints()
                                              & WallpaperColorsCompat.HINT_SUPPORTS_DARK_THEME) > 0;
    }

    public void setOnThemeChangeListener(final OnThemeChangeListener onThemeChangeListener) {
        this.mOnThemeChangeListener = onThemeChangeListener;
    }

    public void addOnChangeListener(final OnChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeOnChangeListener(final OnChangeListener listener) {
        mListeners.remove(listener);
    }

    public void notifyChange(final boolean themeChanged) {
        if (themeChanged) {
            if (mOnThemeChangeListener != null) {
                mOnThemeChangeListener.onThemeChanged();
            }
        } else {
            for (OnChangeListener listener : mListeners) {
                listener.onExtractedColorsChanged(this);
            }
        }
    }

    public interface OnChangeListener {
        void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo);
    }

    public interface OnThemeChangeListener {
        void onThemeChanged();
    }

}
