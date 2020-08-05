package org.zimmob.zimlx.settings

import org.zimmob.zimlx.JavaField
import org.zimmob.zimlx.ZimPreferences

open class GridSize(
    prefs: ZimPreferences,
    rowsKey: String,
    targetObject: Any,
    private val onChangeListener: () -> Unit
) {

    var numRows by JavaField<Int>(targetObject, rowsKey)
    val numRowsOriginal by JavaField<Int>(targetObject, "${rowsKey}Original")

    protected val onChange = {
        applyCustomization()
        onChangeListener.invoke()
    }

    var numRowsPref by prefs.IntPref("pref_$rowsKey", 0, onChange)

    init {
        applyNumRows()
    }

    protected open fun applyCustomization() {
        applyNumRows()
    }

    private fun applyNumRows() {
        numRows = fromPref(numRowsPref, numRowsOriginal)
    }

    fun fromPref(value: Int, default: Int) = if (value != 0) value else default
    fun toPref(value: Int, default: Int) = if (value != default) value else 0
}
