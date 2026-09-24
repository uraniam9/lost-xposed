package dev.lostxposed.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Programmatic views rather than XML or Compose.
 *
 * The settings editor is generated from each feature's declared [dev.lostxposed.core.api.SettingSpec],
 * so there is no fixed layout to inflate — and pulling AndroidX or Compose into the APK for a
 * handful of rows would add a large dependency tree to a module that currently has none.
 *
 * Colours are resolved per call rather than held as constants, because the same value cannot
 * work in both themes: the old hairline `0x22000000` stroke was invisible on a dark
 * background, so every card silently lost its edge.
 */
object Ui {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- colour

    fun isNight(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun pick(context: Context, light: Int, dark: Int) = if (isNight(context)) dark else light

    /** Brand accent. Violet, to sit alongside Lune Bridge and Sonolune. */
    fun accent(context: Context): Int = pick(context, 0xFF5B3FD6.toInt(), 0xFFA996FF.toInt())

    fun foreground(context: Context): Int = pick(context, 0xFF1A1A1A.toInt(), 0xFFF2F2F2.toInt())

    fun muted(context: Context): Int = pick(context, 0xFF5F6368.toInt(), 0xFF9AA0A6.toInt())

    fun ok(context: Context): Int = pick(context, 0xFF1B6B3A.toInt(), 0xFF5BD08A.toInt())

    fun warn(context: Context): Int = pick(context, 0xFF8A6100.toInt(), 0xFFE8B84B.toInt())

    fun danger(context: Context): Int = pick(context, 0xFFB3261E.toInt(), 0xFFF2857D.toInt())

    private fun hairline(context: Context): Int = pick(context, 0x1F000000, 0x33FFFFFF)

    private fun tint(context: Context, colour: Int, alpha: Int): Int =
        (colour and 0x00FFFFFF) or (alpha shl 24)

    // ---------------------------------------------------------------- text

    fun column(context: Context, pad: Int = 20): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(context, pad)
        setPadding(p, p, p, p)
    }

    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(foreground(context))
    }

    fun heading(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text.uppercase()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(muted(context))
        letterSpacing = 0.09f
        setPadding(0, dp(context, 22), 0, dp(context, 8))
    }

    fun body(context: Context, text: String, colour: Int? = null): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(colour ?: foreground(context))
        setLineSpacing(dp(context, 3).toFloat(), 1f)
    }

    /** Body text at reading size, for prose rather than labels. */
    fun prose(context: Context, text: String): TextView = body(context, text).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(dp(context, 6).toFloat(), 1f)
        setPadding(0, 0, 0, dp(context, 10))
    }

    /**
     * A caption with more than one link in it.
     *
     * Spans rather than a row of separate views, so the line wraps as a sentence instead of
     * breaking wherever the views happen to run out of room. Each key is matched literally in
     * [template], so the sentence reads the same in the source as on screen.
     */
    fun linkedCaption(
        context: Context,
        template: String,
        links: Map<String, () -> Unit>,
    ): TextView {
        val spannable = android.text.SpannableString(template)
        links.forEach { (text, action) ->
            val start = template.indexOf(text)
            if (start < 0) return@forEach
            spannable.setSpan(
                object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) = action()

                    override fun updateDrawState(ds: android.text.TextPaint) {
                        // Colour only. An underline under two product names turns a credit
                        // line into a form, and these sit under the app's own title where
                        // nothing else is decorated.
                        ds.color = accent(context)
                        ds.isUnderlineText = false
                    }
                },
                start,
                start + text.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return caption(context, template).apply {
            setText(spannable, TextView.BufferType.SPANNABLE)
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            setPadding(0, dp(context, 4), 0, dp(context, 4))
        }
    }

    fun mono(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTextColor(foreground(context))
        setTextIsSelectable(true)
    }

    fun caption(context: Context, text: String, colour: Int? = null): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colour ?: muted(context))
            setLineSpacing(dp(context, 2).toFloat(), 1f)
        }

    // ---------------------------------------------------------------- pieces

    /**
     * A small pill. Used for the release stage and for each feature's verification state, so
     * that "we have not tested this" is visible at a glance rather than buried in prose.
     */
    fun badge(context: Context, label: String, colour: Int): TextView = TextView(context).apply {
        text = label.uppercase()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.06f
        setTextColor(colour)
        val h = dp(context, 8)
        val v = dp(context, 4)
        setPadding(h, v, h, v)
        background = GradientDrawable().apply {
            cornerRadius = dp(context, 20).toFloat()
            setColor(tint(context, colour, 0x26))
            setStroke(dp(context, 1), tint(context, colour, 0x66))
        }
    }

    fun divider(context: Context): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(context, 1)).apply {
            topMargin = dp(context, 10)
            bottomMargin = dp(context, 10)
        }
        setBackgroundColor(hairline(context))
    }

    fun bullet(context: Context, text: String): TextView =
        body(context, "•  $text").apply { setPadding(0, dp(context, 3), 0, dp(context, 3)) }

    fun button(context: Context, label: String, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    /**
     * A tappable row with a leading label and a trailing hint. Used for everything that
     * navigates or opens a link, so a link off to the web and a link to another screen do not
     * look like two different kinds of thing.
     */
    fun linkRow(
        context: Context,
        label: String,
        detail: String? = null,
        trailing: String = "›",
        colour: Int? = null,
        onClick: () -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = dp(context, 12)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(context, 6)
        }
        background = tappable(context, dp(context, 10))
        isClickable = true
        setOnClickListener { onClick() }

        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                addView(body(context, label, colour))
                detail?.let { addView(caption(context, it)) }
            },
        )
        addView(
            TextView(context).apply {
                text = trailing
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(muted(context))
            },
        )
    }

    /** A tappable block with a hairline border, used for both status and feature rows. */
    fun card(context: Context, onClick: (() -> Unit)? = null): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(context, 16)
            setPadding(p, p, p, p)
            background = if (onClick == null) outline(context) else tappable(context, dp(context, 14))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(context, 10)
            }
            onClick?.let { action ->
                isClickable = true
                setOnClickListener { action() }
            }
        }

    /** A card that carries a state colour, for the one thing on screen that matters most. */
    fun heroCard(context: Context, colour: Int): LinearLayout = card(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(context, 14).toFloat()
            setColor(tint(context, colour, 0x14))
            setStroke(dp(context, 1), tint(context, colour, 0x59))
        }
    }

    private fun outline(context: Context) = GradientDrawable().apply {
        cornerRadius = dp(context, 14).toFloat()
        setStroke(dp(context, 1), hairline(context))
        setColor(Color.TRANSPARENT)
    }

    private fun tappable(context: Context, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(tint(context, accent(context), 0x33)),
        GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setStroke(dp(context, 1), hairline(context))
            setColor(Color.TRANSPARENT)
        },
        null,
    )

    /**
     * A scrolling screen with one action pinned above it.
     *
     * Pinned rather than placed at the end of the list, because the action it carries is
     * "restart the process you just reconfigured" and that is useless if you have to scroll
     * back to it. It sits above the gesture bar, and the scroll view keeps its own bottom
     * padding so the last card is not stuck underneath it.
     */
    fun screenWithAction(
        activity: Activity,
        content: View,
        label: String,
        onClick: () -> Unit,
    ): FrameLayout = FrameLayout(activity).apply {
        val scroller = screen(activity, content)
        addView(scroller)

        val pill = TextView(activity).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            val h = dp(activity, 22)
            val v = dp(activity, 14)
            setPadding(h, v, h, v)
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 28).toFloat()
                setColor(accent(activity))
            }
            elevation = dp(activity, 6).toFloat()
            isClickable = true
            setOnClickListener { onClick() }
        }

        addView(
            pill,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            },
        )

        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            (pill.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                bars.bottom + dp(activity, 16)
            pill.requestLayout()
            // The pill floats over the list, so the list needs room to scroll clear of it.
            scroller.setPadding(0, bars.top, 0, bars.bottom + dp(activity, 84))
            insets
        }
    }

    /**
     * A fixed region above a scrolling one.
     *
     * For anything that has to stay in view while you change what feeds it — the clock
     * preview, which is useless if it scrolls away the moment you reach the setting it is
     * previewing. Putting it in the scroll view also pushed every actual setting below the
     * fold, which is the other half of the problem.
     */
    fun screenWithSticky(
        activity: Activity,
        sticky: View,
        content: View,
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL

        val stickyHolder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(activity, 20)
            setPadding(p, 0, p, dp(activity, 8))
            addView(sticky)
        }
        addView(stickyHolder, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val scroller = ScrollView(activity).apply { addView(content) }
        addView(scroller, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            stickyHolder.setPadding(
                dp(activity, 20),
                bars.top + dp(activity, 12),
                dp(activity, 20),
                dp(activity, 8),
            )
            scroller.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    /** A small tappable pill, for presets offered beside the setting they change. */
    fun chip(context: Context, label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(accent(context))
            val h = dp(context, 14)
            val v = dp(context, 9)
            setPadding(h, v, h, v)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 18).toFloat()
                setColor(tint(context, accent(context), 0x1F))
                setStroke(dp(context, 1), tint(context, accent(context), 0x59))
            }
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                rightMargin = dp(context, 8)
            }
        }

    /** A horizontal strip of chips that scrolls rather than wrapping or clipping. */
    fun chipRow(context: Context): Pair<HorizontalScrollView, LinearLayout> {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(context, 10)
            }
        }
        return scroller to row
    }

    fun spacer(context: Context, height: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(context, height))
    }

    /**
     * The top of a screen: optional back arrow, title, optional badge.
     *
     * Every screen draws its own rather than using the platform action bar. The action bar
     * looked right until a screen scrolled -- with targetSdk 35+ the content ran underneath
     * it -- and it also printed the app label a second time above the app's own header. One
     * header, drawn by the app, in the scroll container, cannot do either.
     */
    fun screenHeader(
        activity: Activity,
        screenTitle: String,
        badgeText: String? = null,
        badgeColour: Int? = null,
        showBack: Boolean = true,
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(activity, 4)
        }

        if (showBack) {
            addView(
                TextView(activity).apply {
                    text = "←"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    setTextColor(foreground(activity))
                    val p = dp(activity, 6)
                    setPadding(0, p, dp(activity, 16), p)
                    isClickable = true
                    setOnClickListener { activity.finish() }
                },
            )
        }

        addView(
            title(activity, screenTitle).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            },
        )
        if (badgeText != null) {
            addView(badge(activity, badgeText, badgeColour ?: warn(activity)))
        }
    }

    /**
     * A scrolling screen that keeps its content clear of the system bars.
     *
     * targetSdk 35+ makes every activity edge-to-edge whether it asked to be or not, so
     * content runs under the status bar and the gesture handle unless the app pads for them.
     * An action bar covers the top inset itself; a screen that hides it has to do that part
     * too, which is what [padTop] selects.
     */
    fun screen(context: Context, content: View, padTop: Boolean = true): ScrollView =
        ScrollView(context).apply {
            // Clipping ON. With it off, scrolled content draws into the padded region and
            // collides with the status bar clock -- which is precisely what the padding was
            // added to prevent. Letting content pass under the bars needs a scrim to stay
            // readable, and a settings screen does not earn that.
            clipToPadding = true
            addView(content)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, if (padTop) bars.top else 0, 0, bars.bottom)
                insets
            }
        }
}
