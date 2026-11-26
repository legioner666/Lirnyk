package app.lirnyk.core.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * Builds simple settings-style screens in code: section titles, explanations, big buttons, switches and choice rows.
 * Keeps the secondary screens consistent without a layout file per row.
 */
class Form(private val context: Context, private val root: LinearLayout) {
    private val d = context.resources.displayMetrics.density

    fun clear() = root.removeAllViews()

    fun section(text: String): TextView = add(TextView(context, null, 0, R.style.Lirnyk_Header).apply {
        this.text = text
        textSize = 24f
        setPadding(0, (20 * d).toInt(), 0, (6 * d).toInt())
    })

    fun body(text: String, secondary: Boolean = true): TextView = add(TextView(context, null, 0, if (secondary) R.style.Lirnyk_Small else R.style.Lirnyk_Body).apply {
        this.text = text
        setPadding(0, (4 * d).toInt(), 0, (4 * d).toInt())
    })

    fun button(text: String, icon: Int? = null, onClick: () -> Unit): TextView = add(TextView(context, null, 0, R.style.Lirnyk_Button).apply {
        this.text = text
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        icon?.let { setCompoundDrawablesRelativeWithIntrinsicBounds(it, 0, 0, 0); compoundDrawablePadding = (12 * d).toInt() }
        setOnClickListener { onClick() }
    }, topMargin = 8)

    /** A row with a label and a trailing small button (e.g. a picked folder with "Remove"). */
    fun rowWithAction(label: String, detail: String?, action: String, onAction: () -> Unit) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context, null, 0, R.style.Lirnyk_Body).apply { text = label; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
        detail?.let { texts.addView(TextView(context, null, 0, R.style.Lirnyk_Small).apply { text = it }) }
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(context, null, 0, R.style.Lirnyk_Button).apply { text = action; setOnClickListener { onAction() } })
        add(row, topMargin = 8)
    }

    fun toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch = add(Switch(context).apply {
        text = label
        textSize = 20f
        setTextColor(context.getColor(R.color.lk_text))
        isChecked = checked
        minHeight = context.resources.getDimensionPixelSize(R.dimen.lk_touch)
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }, topMargin = 8)

    /** One-of-N choice shown as a row of buttons; the chosen one is in the accent colour. */
    fun <T> choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { i, (value, label) ->
            val b = TextView(context, null, 0, R.style.Lirnyk_Button).apply {
                text = label
                setTextColor(context.getColor(if (value == selected) R.color.lk_accent else R.color.lk_text))
                setOnClickListener { onSelect(value) }
            }
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = (8 * d).toInt() })
        }
        add(row, topMargin = 8)
    }

    private fun <V : View> add(v: V, topMargin: Int = 0): V {
        root.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { this.topMargin = (topMargin * d).toInt() })
        return v
    }
}
