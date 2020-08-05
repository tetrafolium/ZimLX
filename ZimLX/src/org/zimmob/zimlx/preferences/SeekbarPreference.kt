package org.zimmob.zimlx.preferences

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.launcher3.R
import com.android.launcher3.Utilities

open class SeekbarPreference
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    Preference(context, attrs, defStyleAttr),
    SeekBar.OnSeekBarChangeListener,
    View.OnCreateContextMenuListener,
    MenuItem.OnMenuItemClickListener {

    private var mSeekbar: SeekBar? = null
    var mValueText: TextView? = null
    var min: Float = 0.toFloat()
    var max: Float = 0.toFloat()
    var current: Float = 0.toFloat()
    var defaultValue: Float = 0.toFloat()
    private var multiplier: Int = 0
    private var format: String? = null
    var steps: Int = 100
    private var lastPersist = Float.NaN

    open val allowResetToDefault = true

    private val handlerThread = HandlerThread("debounce").apply { start() }
    private val persistHandler = Handler(handlerThread.looper)

    init {
        layoutResource = R.layout.preference_seekbar
        init(context, attrs!!)
    }

    private fun init(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.SeekbarPreference)
        min = ta.getFloat(R.styleable.SeekbarPreference_minValue, 0f)
        max = ta.getFloat(R.styleable.SeekbarPreference_maxValue, 100f)
        multiplier = ta.getInt(R.styleable.SeekbarPreference_summaryMultiplier, 1)
        format = ta.getString(R.styleable.SeekbarPreference_summaryFormat)
        defaultValue = ta.getFloat(R.styleable.SeekbarPreference_defaultSeekbarValue, min)
        steps = ta.getInt(R.styleable.SeekbarPreference_steps, 100)
        if (format == null) {
            format = "%.2f"
        }
        ta.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val view = holder.itemView
        mSeekbar = view.findViewById(R.id.seekbar)
        mValueText = view.findViewById(R.id.txtValue)
        mSeekbar!!.max = steps

        mSeekbar!!.setOnSeekBarChangeListener(this)
        val stateList = ColorStateList.valueOf(Utilities.getZimPrefs(context).accentColor)
        mSeekbar!!.thumbTintList = stateList
        mSeekbar!!.progressTintList = stateList
        mSeekbar!!.progressBackgroundTintList = stateList

        current = getPersistedFloat(defaultValue)
        val progress = ((current - min) / ((max - min) / steps))
        mSeekbar!!.progress = Math.round(progress)

        if (allowResetToDefault) view.setOnCreateContextMenuListener(this)
        updateSummary()
    }

    fun setValue(value: Float) {
        current = value
        persistFloat(value)
        updateDisplayedValue()
    }

    protected open fun updateDisplayedValue() {
        mSeekbar?.setOnSeekBarChangeListener(null)
        val progress = ((current - min) / ((max - min) / steps))
        mSeekbar!!.progress = Math.round(progress)
        updateSummary()
        mSeekbar?.setOnSeekBarChangeListener(this)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        current = min + (max - min) / steps * progress
        current = Math.round(current * 100f) / 100f // round to .00 places
        updateSummary()

        persistFloat(current)
    }

    protected open fun updateSummary() {
        mValueText!!.text = String.format(format!!, current * multiplier)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        persistFloat(current)
    }

    override fun persistFloat(value: Float): Boolean {
        if (value == lastPersist) return true
        lastPersist = value
        return super.persistFloat(value)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        menu.setHeaderTitle(title)
        menu.add(0, 0, 0, R.string.reset_to_default)
        for (i in (0 until menu.size())) {
            menu.getItem(i).setOnMenuItemClickListener(this)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        setValue(defaultValue)
        return true
    }
}
