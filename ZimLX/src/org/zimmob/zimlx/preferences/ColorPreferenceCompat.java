package org.zimmob.zimlx.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;

import com.android.launcher3.R;
import com.jaredrummler.android.colorpicker.ColorPanelView;
import com.jaredrummler.android.colorpicker.ColorPickerDialog;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;
import com.jaredrummler.android.colorpicker.ColorShape;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Created by saul on 04-25-18.
 * Project ZimLX
 * henriquez.saul@gmail.com
 */
public class ColorPreferenceCompat extends Preference implements ColorPickerDialogListener {

    private static final int SIZE_NORMAL = 0;
    private static final int SIZE_LARGE = 1;

    private OnShowDialogListener onShowDialogListener;
    private int color = Color.BLACK;
    private boolean showDialog;
    @ColorPickerDialog.DialogType
    private
    int dialogType;
    private int colorShape;
    private boolean allowPresets;
    private boolean allowCustom;
    private boolean showAlphaSlider;
    private boolean showColorShades;
    private int previewSize;
    private int[] presets;
    private int dialogTitle;

    public ColorPreferenceCompat(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ColorPreferenceCompat(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    private void init(final AttributeSet attrs) {
        setPersistent(true);
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ColorPreference);
        showDialog = a.getBoolean(R.styleable.ColorPreference_cpv_showDialog, true);
        //noinspection WrongConstant
        dialogType = a.getInt(R.styleable.ColorPreference_cpv_dialogType, ColorPickerDialog.TYPE_PRESETS);
        colorShape = a.getInt(R.styleable.ColorPreference_cpv_colorShape, ColorShape.CIRCLE);
        allowPresets = a.getBoolean(R.styleable.ColorPreference_cpv_allowPresets, true);
        allowCustom = a.getBoolean(R.styleable.ColorPreference_cpv_allowCustom, true);
        showAlphaSlider = a.getBoolean(R.styleable.ColorPreference_cpv_showAlphaSlider, false);
        showColorShades = a.getBoolean(R.styleable.ColorPreference_cpv_showColorShades, true);
        previewSize = a.getInt(R.styleable.ColorPreference_cpv_previewSize, SIZE_NORMAL);
        final int presetsResId = a.getResourceId(R.styleable.ColorPreference_cpv_colorPresets, 0);
        dialogTitle = a.getResourceId(R.styleable.ColorPreference_cpv_dialogTitle, R.string.cpv_default_title);
        if (presetsResId != 0) {
            presets = getContext().getResources().getIntArray(presetsResId);
        } else {
            presets = ColorPickerDialog.MATERIAL_COLORS;
        }
        if (colorShape == ColorShape.CIRCLE) {
            setWidgetLayoutResource(
                previewSize == SIZE_LARGE ? R.layout.cpv_preference_circle_large : R.layout.cpv_preference_circle);
        } else {
            setWidgetLayoutResource(
                previewSize == SIZE_LARGE ? R.layout.cpv_preference_square_large : R.layout.cpv_preference_square
            );
        }
        a.recycle();
    }

    public ColorPickerDialog getDialog() {
        return ColorPickerDialog.newBuilder()
               .setDialogType(dialogType)
               .setDialogTitle(dialogTitle)
               .setColorShape(colorShape)
               .setPresets(presets)
               .setAllowPresets(allowPresets)
               .setAllowCustom(allowCustom)
               .setShowAlphaSlider(showAlphaSlider)
               .setShowColorShades(showColorShades)
               .setColor(color)
               .create();
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ColorPanelView preview = (ColorPanelView) holder.findViewById(R.id.cpv_preference_preview_color_panel);
        if (preview != null) {
            preview.setColor(color);
        }
    }

    @Override
    protected void onSetInitialValue(final boolean restorePersistedValue, final Object defaultValue) {
        if (restorePersistedValue) {
            color = getPersistedInt(0xFF000000);
        } else {
            color = (Integer) defaultValue;
            persistInt(color);
        }
    }

    @Override
    protected Object onGetDefaultValue(final TypedArray a, final int index) {
        return a.getInteger(index, Color.BLACK);
    }

    @Override
    public void onColorSelected(final int dialogId, final @ColorInt int color) {
        saveValue(color);
    }

    @Override
    public void onDialogDismissed(final int dialogId) {
        // no-op
    }

    /**
     * Set the new color
     *
     * @param color The newly selected color
     */
    public void saveValue(final @ColorInt int color) {
        this.color = color;
        persistInt(this.color);
        notifyChanged();
        callChangeListener(color);
    }

    /**
     * Get the colors that will be shown in the {@link ColorPickerDialog}.
     *
     * @return An array of color ints
     */
    public int[] getPresets() {
        return presets;
    }

    /**
     * Set the colors shown in the {@link ColorPickerDialog}.
     *
     * @param presets An array of color ints
     */
    public void setPresets(final @NonNull int[] presets) {
        this.presets = presets;
    }

    /**
     * The listener used for showing the {@link ColorPickerDialog}.
     * Call {@link #saveValue(int)} after the user chooses a color.
     * If this is set then it is up to you to show the dialog.
     *
     * @param listener The listener to show the dialog
     */
    public void setOnShowDialogListener(final OnShowDialogListener listener) {
        onShowDialogListener = listener;
    }

    /**
     * The tag used for the {@link ColorPickerDialog}.
     *
     * @return The tag
     */
    public String getFragmentTag() {
        return "color_" + getKey();
    }

    private interface OnShowDialogListener {

        void onShowColorPickerDialog(String title, int currentColor);
    }

}
