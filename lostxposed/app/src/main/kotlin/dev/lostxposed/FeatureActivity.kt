package dev.lostxposed

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import java.util.Locale
import kotlin.math.roundToInt
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.SeekBar
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import dev.lostxposed.core.api.FeatureDescriptor
import dev.lostxposed.core.api.FeatureId
import dev.lostxposed.core.api.SettingSpec
import dev.lostxposed.core.api.Template
import dev.lostxposed.core.config.ConfigSchema
import dev.lostxposed.core.config.ConfigWriter
import dev.lostxposed.core.config.MapConfigStore
import dev.lostxposed.core.config.StoreConfigSource
import dev.lostxposed.features.smartstatusbar.ClockRenderer
import dev.lostxposed.features.smartstatusbar.SmartStatusBarFeature
import dev.lostxposed.entry.xposed.Features
import dev.lostxposed.ui.Ui

/**
 * A settings editor generated from whatever the feature declares.
 *
 * Nothing here knows about any particular feature. Adding a setting to a feature gives it an
 * editor for free, and renaming one cannot leave a stale control behind — which is the whole
 * reason [SettingSpec] exists rather than seven hand-written screens.
 */
class FeatureActivity : Activity() {

    private lateinit var descriptor: FeatureDescriptor
    private lateinit var writer: ConfigWriter
    private lateinit var packageField: EditText

    private val controls = mutableMapOf<SettingSpec, () -> String?>()
    private val fillers = mutableMapOf<SettingSpec, (String) -> Unit>()
    private var preview: android.widget.TextView? = null
    private val cards = mutableMapOf<SettingSpec, LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra(EXTRA_FEATURE)
        val found = Features.registry.registrations.firstOrNull { it.descriptor.id.value == id }
        if (found == null) {
            finish()
            return
        }
        descriptor = found.descriptor
        writer = ConfigWriter.open(this)

        actionBar?.hide()

        val form = buildForm()
        setContentView(
            sticky?.let { Ui.screenWithSticky(this, it, form) }
                ?: Ui.screen(this, form, padTop = true),
        )
    }

    /** Pinned above the scroll when a feature has something worth keeping in view. */
    private var sticky: LinearLayout? = null

    private fun buildForm(): LinearLayout = Ui.column(this).apply {
        if (descriptor.id == SmartStatusBarFeature.ID) {
            sticky = Ui.column(this@FeatureActivity, pad = 0).apply {
                addView(Ui.screenHeader(this@FeatureActivity, descriptor.name))
                addView(previewCard())
            }
        } else {
            addView(Ui.screenHeader(this@FeatureActivity, descriptor.name))
        }
        addView(Ui.caption(this@FeatureActivity, descriptor.description))
        addView(
            Ui.caption(
                this@FeatureActivity,
                "${descriptor.category} · ${descriptor.stability} · risk ${descriptor.riskTier}",
            ),
        )

        descriptor.detail?.let { detail ->
            addView(Ui.heading(this@FeatureActivity, "What this does"))
            detail.split("\n\n").forEach {
                addView(Ui.prose(this@FeatureActivity, it))
            }
        }

        // Shown only when something is still unproven. Explaining that a feature works is
        // a paragraph nobody asked for.
        val status = FeatureStatus.of(descriptor.id)
        if (status.state != FeatureStatus.State.VERIFIED) {
            addView(Ui.heading(this@FeatureActivity, "What has been tested"))
            addView(
                Ui.body(
                    this@FeatureActivity,
                    status.detail,
                    when (status.state) {
                        FeatureStatus.State.UNCONFIRMED -> Ui.warn(this@FeatureActivity)
                        else -> Ui.muted(this@FeatureActivity)
                    },
                ),
            )
        }

        if (descriptor.settings.isEmpty()) {
            addView(Ui.heading(this@FeatureActivity, "Settings"))
            addView(Ui.body(this@FeatureActivity, "This feature has nothing to configure."))
            return@apply
        }

        val perPackage = descriptor.settings.any { it.perPackage }
        if (perPackage) {
            addView(Ui.heading(this@FeatureActivity, "Applies to"))
            packageField = EditText(this@FeatureActivity).apply {
                setText(ConfigSchema.ANY_PACKAGE)
                hint = "package name, or * for all"
                inputType = InputType.TYPE_CLASS_TEXT
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
            addView(packageField)
            addView(
                Ui.caption(
                    this@FeatureActivity,
                    "A per-package value overrides the * default.",
                ),
            )
        } else {
            // Not added to the layout: a feature whose settings are all global has no
            // package to apply them to, and showing the field only invites the question.
            packageField = EditText(this@FeatureActivity).apply { setText(ConfigSchema.ANY_PACKAGE) }
        }

        val orphanPresets = descriptor.templates.filter { template ->
            descriptor.settings.none { it.key == template.anchor }
        }
        if (orphanPresets.isNotEmpty()) {
            addView(Ui.heading(this@FeatureActivity, "Start from"))
            orphanPresets.forEach { template ->
                addView(
                    Ui.linkRow(
                        this@FeatureActivity,
                        template.name,
                        template.detail,
                        trailing = "+",
                    ) { fill(template) },
                )
            }
        }

        addView(Ui.heading(this@FeatureActivity, "Settings"))
        descriptor.settings.forEach { spec -> addView(controlFor(spec)) }
        onControlChanged()

        addView(Ui.spacer(this@FeatureActivity, 12))
        addView(Ui.button(this@FeatureActivity, "Save") { save() })
        addView(
            Ui.button(
                this@FeatureActivity,
                // Every setting on this feature is global, so "for this package" would be
                // asking about a package the feature does not have.
                if (perPackage) "Clear for this package" else "Clear these settings",
            ) { clear() },
        )
        addView(
            Ui.caption(
                this@FeatureActivity,
                "Settings are read when a process starts — ${descriptor.restartHint} for changes " +
                    "to take effect.",
            ),
        )
        addView(Ui.spacer(this@FeatureActivity, 24))
    }

    private fun controlFor(spec: SettingSpec): LinearLayout {
        val target = packageTarget()
        val current = writer.read(descriptor.id, target, spec.key)

        return Ui.card(this).apply {
            addView(Ui.body(this@FeatureActivity, spec.label))
            spec.help?.let { addView(Ui.caption(this@FeatureActivity, it)) }

            when {
                spec.type == SettingSpec.Type.BOOLEAN -> {
                    val toggle = Switch(this@FeatureActivity).apply {
                        isChecked = current as? Boolean ?: (spec.default == "true")
                        setOnCheckedChangeListener { _, _ -> onControlChanged() }
                    }
                    addView(toggle)
                    controls[spec] = { toggle.isChecked.toString() }
                    fillers[spec] = { toggle.isChecked = it.toBoolean() }
                }

                spec.type == SettingSpec.Type.ENUM -> {
                    val spinner = Spinner(this@FeatureActivity).apply {
                        adapter = ArrayAdapter(
                            this@FeatureActivity,
                            android.R.layout.simple_spinner_dropdown_item,
                            spec.options,
                        )
                        val selected = (current as? String) ?: spec.default
                        spec.options.indexOf(selected).takeIf { it >= 0 }?.let { setSelection(it) }
                        onItemSelectedListener =
                            object : android.widget.AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(
                                    parent: android.widget.AdapterView<*>?,
                                    view: android.view.View?,
                                    position: Int,
                                    id: Long,
                                ) = onControlChanged()

                                override fun onNothingSelected(
                                    parent: android.widget.AdapterView<*>?,
                                ) = Unit
                            }
                    }
                    addView(spinner)
                    controls[spec] = { spinner.selectedItem as? String }
                    fillers[spec] = { value ->
                        spec.options.indexOf(value).takeIf { it >= 0 }?.let(spinner::setSelection)
                    }
                }

                spec.type == SettingSpec.Type.IME_LIST -> addImePicker(this, spec, current)

                spec.isSlider -> addSlider(this, spec, current)

                else -> {
                    val field = EditText(this@FeatureActivity).apply {
                        setText(current?.toString() ?: "")
                        hint = spec.default ?: "not set"
                        inputType = when (spec.type) {
                            SettingSpec.Type.INT -> InputType.TYPE_CLASS_NUMBER
                            SettingSpec.Type.FLOAT ->
                                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            else -> InputType.TYPE_CLASS_TEXT
                        }
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                        addTextChangedListener(
                            object : android.text.TextWatcher {
                                override fun afterTextChanged(s: android.text.Editable?) =
                                    onControlChanged()

                                override fun beforeTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    count: Int,
                                    after: Int,
                                ) = Unit

                                override fun onTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    before: Int,
                                    count: Int,
                                ) = Unit
                            },
                        )
                    }
                    addView(field)
                    controls[spec] = { field.text?.toString()?.trim()?.ifEmpty { null } }
                    fillers[spec] = { field.setText(it) }
                }
            }

            addPresetsFor(this, spec)
            cards[spec] = this
        }
    }

    /**
     * One place every control reports to, so the preview and the greying cannot drift from
     * what is actually on screen.
     *
     * This is why changing the style did not change the preview: the preview only refreshed
     * when you tapped it, so a spinner selection left it showing the previous style's text.
     */
    private fun onControlChanged() {
        applyEnablement()
        refreshPreview()
    }

    /** Grey out the settings whose controlling value is not currently selected. */
    private fun applyEnablement() {
        cards.forEach { (spec, card) ->
            val dependency = spec.enabledWhen ?: return@forEach
            val controlling = descriptor.settings.firstOrNull { it.key == dependency.key }
            val value = controlling?.let { controls[it]?.invoke() } ?: controlling?.default
            setEnabledDeep(card, value in dependency.values)
        }
    }

    private fun setEnabledDeep(view: android.view.View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.38f
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) setEnabledDeep(view.getChildAt(i), enabled)
        }
    }

    /**
     * The presets belonging to one setting, as chips under its control.
     *
     * Beside the thing they change rather than in a section of their own. A colour preset
     * next to the colour field is obvious; the same preset at the top of the screen is
     * something you have to go looking for, and a separate section pushes every actual
     * setting below the fold.
     */
    private fun addPresetsFor(parent: LinearLayout, spec: SettingSpec) {
        val mine = descriptor.templates.filter { it.anchor == spec.key }
        if (mine.isEmpty()) return

        val (scroller, row) = Ui.chipRow(this)
        mine.forEach { template ->
            row.addView(Ui.chip(this, template.name) { fill(template) })
        }
        parent.addView(scroller)
        parent.addView(
            Ui.caption(
                this,
                mine.joinToString("  ·  ") { it.detail.substringBefore('.') },
            ),
        )
    }

    /**
     * A slider, the value it is on, and a way back to "unset".
     *
     * Unset has to stay reachable. A slider always has a position, so without that button
     * there is no way to say "leave the system value alone" once you have touched it, and the
     * app would quietly force whatever the slider happened to be resting on.
     */
    private fun addSlider(parent: LinearLayout, spec: SettingSpec, current: Any?) {
        val min = spec.min ?: 0f
        val max = spec.max ?: 1f
        val step = spec.step ?: 0.05f
        val steps = ((max - min) / step).roundToInt().coerceAtLeast(1)

        val readout = Ui.caption(this, "")
        var isSet = current != null
        val bar = SeekBar(this).apply {
            this.max = steps
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        fun valueNow() = min + bar.progress * step

        fun show() {
            readout.text = if (isSet) {
                format(spec, valueNow())
            } else {
                "not set \u2014 the system value is left alone"
            }
        }

        val startValue = (current as? Number)?.toFloat()
            ?: spec.default?.toFloatOrNull()
            ?: ((min + max) / 2f)
        bar.progress = ((startValue - min) / step).roundToInt().coerceIn(0, steps)

        bar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(v: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) isSet = true
                    show()
                    if (fromUser) onControlChanged()
                }

                override fun onStartTrackingTouch(v: SeekBar?) = Unit

                override fun onStopTrackingTouch(v: SeekBar?) = Unit
            },
        )

        parent.addView(bar)
        parent.addView(readout)
        parent.addView(
            Ui.button(this, "Leave this alone") {
                isSet = false
                show()
            },
        )
        show()

        controls[spec] = { if (isSet) format(spec, valueNow()) else null }
        fillers[spec] = { value ->
            value.toFloatOrNull()?.let {
                bar.progress = ((it - min) / step).roundToInt().coerceIn(0, steps)
                isSet = true
                show()
            }
        }
    }

    private fun format(spec: SettingSpec, value: Float): String =
        if (spec.type == SettingSpec.Type.INT) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }

    /**
     * The keyboards actually enabled on this device, by name.
     *
     * The stored setting is still a list of package names, because that is what the hook
     * matches on. Asking somebody to type `com.touchtype.swiftkey` is asking them to go and
     * find it out first.
     */
    private fun addImePicker(parent: LinearLayout, spec: SettingSpec, current: Any?) {
        val chosen = (current as? String).orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()

        controls[spec] = { chosen.joinToString(",").ifEmpty { null } }

        val keyboards = runCatching {
            getSystemService(InputMethodManager::class.java)
                .enabledInputMethodList
                .map { it.packageName to it.loadLabel(packageManager).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())

        if (keyboards.isEmpty()) {
            parent.addView(Ui.caption(this, "No keyboards could be listed on this device."))
            return
        }

        val boxes = keyboards.map { (pkg, label) ->
            val box = CheckBox(this).apply {
                text = label
                isChecked = pkg in chosen
                setOnCheckedChangeListener { _, on -> if (on) chosen += pkg else chosen -= pkg }
            }
            parent.addView(box)
            parent.addView(Ui.caption(this, pkg))
            pkg to box
        }

        fillers[spec] = { value ->
            val wanted = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            boxes.forEach { (pkg, box) -> box.isChecked = pkg in wanted }
        }
    }

    /** Fills the form from a template. Deliberately does not save. */
    private fun fill(template: Template) {
        var filled = 0
        var skipped = 0
        template.values.forEach { (key, raw) ->
            val spec = descriptor.settings.firstOrNull { it.key == key }
            val value = spec?.let { resolveTemplateValue(it, raw) }
            val filler = spec?.let { fillers[it] }
            if (value != null && filler != null) {
                filler(value)
                filled++
            } else {
                skipped++
            }
        }
        onControlChanged()
        toast(
            "Filled $filled field(s) from \"${template.name}\"" +
                (if (skipped > 0) ", $skipped could not be resolved on this device" else "") +
                ". Press Save to keep it.",
        )
    }

    /**
     * `%85` means 85% of what this device reports for that setting's declared baseline.
     *
     * A preset saying 357dpi is advice for one phone and wrong on the next. A preset saying
     * 85% travels, which is the only way it can be useful to somebody else.
     */
    private fun resolveTemplateValue(spec: SettingSpec, raw: String): String? {
        if (!raw.startsWith("%")) return raw
        val percent = raw.removePrefix("%").toFloatOrNull() ?: return null
        val base = baselineValue(spec.baseline) ?: return null
        return format(spec, base * percent / 100f)
    }

    private fun baselineValue(baseline: SettingSpec.Baseline): Float? = when (baseline) {
        SettingSpec.Baseline.DENSITY_DPI -> resources.configuration.densityDpi.toFloat()
        SettingSpec.Baseline.FONT_SCALE -> resources.configuration.fontScale
        SettingSpec.Baseline.REFRESH_RATE ->
            runCatching { display?.supportedModes?.maxOf { it.refreshRate } }.getOrNull()

        SettingSpec.Baseline.NONE -> null
    }

    /**
     * What the status bar will actually say, rendered here by the same class the hook uses.
     *
     * Checking a mixer line otherwise costs a SystemUI restart and a walk back to the status
     * bar, which means you find out what `{seconds}` looks like well after you stopped caring.
     */
    private fun previewCard() = Ui.card(this) { refreshPreview() }.apply {
        addView(
            Ui.caption(
                this@FeatureActivity,
                "What the status bar will show with the settings below. Tap to refresh.",
            ),
        )
        addView(Ui.spacer(this@FeatureActivity, 8))
        preview = Ui.body(this@FeatureActivity, "").apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
        }
        addView(preview)
    }

    private fun refreshPreview() {
        val view = preview ?: return
        val target = packageTarget()

        // Built from the controls as they stand, not from what is saved, so the preview
        // answers "what will this do" rather than "what did I already commit".
        val pending = controls.mapNotNull { (spec, read) ->
            read()?.let { ConfigSchema.key(descriptor.id, target, spec.key) to it }
        }.toMap()

        val source = StoreConfigSource(MapConfigStore(pending), descriptor.id, target)
        view.text = runCatching {
            ClockRenderer(SmartStatusBarFeature.Settings.from(source))
                .text(fallback = "14:05")
                ?: "(unchanged — the system clock, restyled if you set a style below)"
        }.getOrElse { "cannot render: ${it.javaClass.simpleName}" }
    }

    private fun packageTarget(): String =
        packageField.text?.toString()?.trim()?.ifEmpty { null } ?: ConfigSchema.ANY_PACKAGE

    // ConfigSchema.ANY_PACKAGE is a storage-key sentinel ("*"), not a word for a person to read.
    // It leaked straight into two toasts as the literal asterisk before this existed.
    private fun targetPhrase(target: String): String =
        if (target == ConfigSchema.ANY_PACKAGE) "every app" else "\"$target\""

    private fun save() {
        val target = packageTarget()
        var written = 0
        var rejected: String? = null

        controls.forEach { (spec, read) ->
            val raw = read()
            if (raw == null) {
                // Empty means "leave alone", so remove rather than storing a zero that would
                // read back as an override.
                writer.remove(descriptor.id, target, spec.key)
                return@forEach
            }
            val ok = when (spec.type) {
                SettingSpec.Type.BOOLEAN ->
                    writer.putBoolean(descriptor.id, target, spec.key, raw.toBoolean()).let { true }

                SettingSpec.Type.INT -> raw.toIntOrNull()
                    ?.also { writer.putInt(descriptor.id, target, spec.key, it) } != null

                SettingSpec.Type.FLOAT -> raw.toFloatOrNull()
                    ?.also { writer.putFloat(descriptor.id, target, spec.key, it) } != null

                else -> writer.putString(descriptor.id, target, spec.key, raw).let { true }
            }
            if (ok) written++ else rejected = spec.label
        }

        refreshPreview()
        toast(
            rejected?.let { "\"$it\" is not a valid number — nothing saved for it" }
                ?: "Saved $written setting(s) for ${targetPhrase(target)} — ${descriptor.restartHint}",
        )
    }

    private fun clear() {
        val target = packageTarget()
        writer.removeAll(descriptor.id, target)
        toast(
            if (descriptor.settings.any { it.perPackage }) {
                "Cleared for ${targetPhrase(target)} — ${descriptor.restartHint}"
            } else {
                "Cleared — ${descriptor.restartHint}"
            },
        )
        recreate()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_FEATURE = "feature"
    }
}
