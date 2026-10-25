from pathlib import Path
import re

p = Path("src/app/src/main/java/com/expressive/keyboard/VolumePanel.kt")
s = p.read_text(encoding="utf-8")

# Preview bind gets a ringer mode.
s = s.replace(
    'it.bind(s, 36, 100, resolvePanelColors(context, s), animateValue = false)',
    'it.bind(s, 36, 100, resolvePanelColors(context, s), AudioManager.RINGER_MODE_NORMAL, animateValue = false)'
)

# Long-press repeat state.
s = s.replace(
    '    private var passthroughKeyCode = KeyEvent.KEYCODE_UNKNOWN\n',
    '''    private var passthroughKeyCode = KeyEvent.KEYCODE_UNKNOWN
    private var heldVolumeKey = KeyEvent.KEYCODE_UNKNOWN
    private var heldDirection = 0
    private val repeatVolume = object : Runnable {
        override fun run() {
            if (heldVolumeKey == KeyEvent.KEYCODE_UNKNOWN) return
            enterAudioForegroundWindow()
            val stream = preferredStream()
            runCatching { audio.adjustStreamVolume(stream, heldDirection, 0) }
            showPanel(stream)
            main.postDelayed(this, 70L)
        }
    }
'''
)

new_key_handler = '''    override fun onKeyEvent(event: KeyEvent): Boolean {
        val s = VolumePanelPrefs.current(this)
        if (!s.enabled) return false

        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            KeyEvent.KEYCODE_VOLUME_MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return false
        }

        if (passthroughKeyCode == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) passthroughKeyCode = KeyEvent.KEYCODE_UNKNOWN
            return false
        }

        if (event.action == KeyEvent.ACTION_UP) {
            if (heldVolumeKey == event.keyCode) stopVolumeRepeat()
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        // We run our own repeat so OEM key-repeat events cannot make the volume jump twice.
        if (event.repeatCount > 0) return true

        val stream = preferredStream()
        val min = if (Build.VERSION.SDK_INT >= 28) runCatching { audio.getStreamMinVolume(stream) }.getOrDefault(0) else 0
        val max = runCatching { audio.getStreamMaxVolume(stream) }.getOrDefault(15).coerceAtLeast(min + 1)
        val before = runCatching { audio.getStreamVolume(stream) }.getOrDefault(min).coerceIn(min, max)

        enterAudioForegroundWindow()
        runCatching { audio.adjustStreamVolume(stream, direction, 0) }
        val after = runCatching { audio.getStreamVolume(stream) }.getOrDefault(before).coerceIn(min, max)
        val blocked = when (direction) {
            AudioManager.ADJUST_RAISE -> before < max && after == before
            AudioManager.ADJUST_LOWER -> before > min && after == before
            else -> false
        }
        if (blocked) {
            passthroughKeyCode = event.keyCode
            stopVolumeRepeat()
            hidePanel()
            return false
        }

        showPanel(stream)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            startVolumeRepeat(event.keyCode, direction)
        }
        return true
    }

    private fun startVolumeRepeat(keyCode: Int, direction: Int) {
        stopVolumeRepeat()
        heldVolumeKey = keyCode
        heldDirection = direction
        main.postDelayed(repeatVolume, 280L)
    }

    private fun stopVolumeRepeat() {
        main.removeCallbacks(repeatVolume)
        heldVolumeKey = KeyEvent.KEYCODE_UNKNOWN
        heldDirection = 0
    }

'''
s, n = re.subn(
    r'    override fun onKeyEvent\(event: KeyEvent\): Boolean \{.*?\n    private fun preferredStream',
    new_key_handler + '    private fun preferredStream',
    s,
    count=1,
    flags=re.S
)
if n != 1:
    raise SystemExit("Failed to replace onKeyEvent")

# Mode picker callbacks instead of immediate mute toggle.
old = '''            view.onMuteToggle = {
                enterAudioForegroundWindow()
                val active = preferredStream()
                runCatching { audio.adjustStreamVolume(active, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
                showPanel(active)
            }
'''
new = '''            view.onModeMenuVisibilityChanged = { open ->
                updatePanelLayoutForModeMenu(view, open)
                main.removeCallbacks(hide)
                main.postDelayed(hide, if (open) 4200L else s.timeoutMs.toLong())
            }
            view.onSoundModeSelected = { mode ->
                enterAudioForegroundWindow()
                val changed = runCatching {
                    audio.ringerMode = mode
                    true
                }.getOrDefault(false)
                if (!changed) {
                    Toast.makeText(this, "Android не разрешил сменить режим звука", Toast.LENGTH_SHORT).show()
                }
                view.setModeMenuOpen(false)
                updatePanelLayoutForModeMenu(view, false)
                showPanel(preferredStream())
            }
'''
if old not in s:
    raise SystemExit("Mute callback block not found")
s = s.replace(old, new, 1)

# Bind with current ringer mode.
s = s.replace(
    'v.bind(s, value - min, max - min, colors, animateValue = s.animate)',
    'v.bind(s, value - min, max - min, colors, audio.ringerMode, animateValue = s.animate)'
)
s = s.replace(
    'val lp = buildLayoutParams(s)',
    'val lp = buildLayoutParams(s, v.isModeMenuOpen())',
    1
)

# Expand/shrink window when the mode picker opens.
needle = '    private fun hidePanel() {\n'
helper = '''    private fun updatePanelLayoutForModeMenu(view: ExpressiveVolumeView, open: Boolean) {
        if (view.parent == null) return
        val s = VolumePanelPrefs.current(this)
        val lp = buildLayoutParams(s, open)
        params = lp
        runCatching { wm.updateViewLayout(view, lp) }
    }

'''
if needle not in s:
    raise SystemExit("hidePanel marker not found")
s = s.replace(needle, helper + needle, 1)

# Close picker when hiding.
s = s.replace(
    '''        val s = VolumePanelPrefs.current(this)
        v.animate().cancel()
''',
    '''        val s = VolumePanelPrefs.current(this)
        v.animate().cancel()
        if (v.isModeMenuOpen()) {
            v.setModeMenuOpen(false)
            updatePanelLayoutForModeMenu(v, false)
        }
''',
    1
)

# Dynamic overlay width.
s = s.replace(
    'private fun buildLayoutParams(s: VolumePanelSettings): WindowManager.LayoutParams {',
    'private fun buildLayoutParams(s: VolumePanelSettings, modeMenuOpen: Boolean = false): WindowManager.LayoutParams {'
)
s = s.replace(
    '            (s.widthDp * d).roundToInt(),\n            (s.heightDp * d).roundToInt(),',
    '            ((s.widthDp + if (modeMenuOpen) 58f else 0f) * d).roundToInt(),\n            (s.heightDp * d).roundToInt(),',
    1
)

# Stop repeat on destroy.
s = s.replace(
    '    override fun onDestroy() {\n        main.removeCallbacksAndMessages(null)',
    '    override fun onDestroy() {\n        stopVolumeRepeat()\n        main.removeCallbacksAndMessages(null)',
    1
)

# View callback/state additions.
s = s.replace(
    '''    var onFractionChanged: ((Float) -> Unit)? = null
    var onMuteToggle: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onOutsideTouch: (() -> Unit)? = null
''',
    '''    var onFractionChanged: ((Float) -> Unit)? = null
    var onModeMenuVisibilityChanged: ((Boolean) -> Unit)? = null
    var onSoundModeSelected: ((Int) -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onOutsideTouch: (() -> Unit)? = null
'''
)

s = s.replace(
    '''    private var dragging = false
    private var colors = PanelColors''',
    '''    private var dragging = false
    private var modeMenuOpen = false
    private var ringerMode = AudioManager.RINGER_MODE_NORMAL
    private var colors = PanelColors'''
)

s = s.replace(
    '''    private val sliderRect = RectF()
    private val muteRect = RectF()
    private val settingsRect = RectF()
''',
    '''    private val sliderRect = RectF()
    private val muteRect = RectF()
    private val settingsRect = RectF()
    private val vibrateRect = RectF()
    private val silentRect = RectF()
    private val normalRect = RectF()
'''
)

# Bind signature and ringer state.
s = s.replace(
    'fun bind(s: VolumePanelSettings, value: Int, max: Int, palette: PanelColors, animateValue: Boolean = true) {',
    'fun bind(s: VolumePanelSettings, value: Int, max: Int, palette: PanelColors, ringer: Int, animateValue: Boolean = true) {'
)
s = s.replace(
    '''        settings = s
        colors = palette
''',
    '''        settings = s
        colors = palette
        ringerMode = ringer
''',
    1
)

# Public helpers for service.
bind_end = '''        hasBound = true
    }

    private fun dp'''
replacement = '''        hasBound = true
    }

    fun isModeMenuOpen(): Boolean = modeMenuOpen

    fun setModeMenuOpen(open: Boolean) {
        if (modeMenuOpen == open) return
        modeMenuOpen = open
        if (!open) {
            vibrateRect.setEmpty()
            silentRect.setEmpty()
            normalRect.setEmpty()
        }
        invalidate()
    }

    private fun dp'''
if bind_end not in s:
    raise SystemExit("bind end marker missing")
s = s.replace(bind_end, replacement, 1)

# Main panel geometry stays pinned to screen edge when expanded.
old_geom = '''        val outerInset = dp(1f)
        val outer = RectF(outerInset, outerInset, w - outerInset, h - outerInset)
        val radius = min(dp(settings.cornerDp), w / 2f)
        paint.style = Paint.Style.FILL
        paint.color = alpha(colors.background, settings.opacity)
        canvas.drawRoundRect(outer, radius, radius, paint)

        val pad = dp(7f)
        val actionSize = min(w - pad * 2f, dp(44f)).coerceAtLeast(dp(34f))
        val cx = w / 2f
'''
new_geom = '''        val panelWidth = min(dp(settings.widthDp), w)
        val panelLeft = if (settings.position == "RIGHT") w - panelWidth else 0f
        val panelRight = panelLeft + panelWidth
        val outerInset = dp(1f)
        val outer = RectF(panelLeft + outerInset, outerInset, panelRight - outerInset, h - outerInset)
        val radius = min(dp(settings.cornerDp), panelWidth / 2f)
        paint.style = Paint.Style.FILL
        paint.color = alpha(colors.background, settings.opacity)
        canvas.drawRoundRect(outer, radius, radius, paint)

        val pad = dp(7f)
        val actionSize = min(panelWidth - pad * 2f, dp(44f)).coerceAtLeast(dp(34f))
        val cx = (panelLeft + panelRight) / 2f
'''
if old_geom not in s:
    raise SystemExit("geometry block missing")
s = s.replace(old_geom, new_geom, 1)

# Dynamic top icon + draw picker.
old_top = '''        muteRect.set(cx - actionSize / 2f, pad, cx + actionSize / 2f, pad + actionSize)
        drawActionShape(canvas, muteRect.centerX(), muteRect.centerY(), actionSize / 2f, colors.accent, true)
        drawSvgIcon(canvas, insetRect(muteRect, actionSize * 0.25f), settings.muteSvg, DEFAULT_MUTE_SVG, colors.onAccent)
'''
new_top = '''        muteRect.set(cx - actionSize / 2f, pad, cx + actionSize / 2f, pad + actionSize)
        drawActionShape(canvas, muteRect.centerX(), muteRect.centerY(), actionSize / 2f, colors.accent, true)
        val topTemplate = when (ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> DEFAULT_VIBRATE_SVG
            AudioManager.RINGER_MODE_NORMAL -> DEFAULT_BELL_SVG
            else -> DEFAULT_MUTE_SVG
        }
        val topCustom = if (ringerMode == AudioManager.RINGER_MODE_SILENT) settings.muteSvg else ""
        drawSvgIcon(canvas, insetRect(muteRect, actionSize * 0.25f), topCustom, topTemplate, colors.onAccent)
        if (modeMenuOpen) drawModeMenu(canvas, panelLeft, panelRight, pad, actionSize)
'''
if old_top not in s:
    raise SystemExit("top icon block missing")
s = s.replace(old_top, new_top, 1)

# Add picker drawing before insetRect helper.
marker = '    private fun insetRect(rect: RectF, inset: Float)'
picker = '''    private fun drawModeMenu(canvas: Canvas, panelLeft: Float, panelRight: Float, pad: Float, actionSize: Float) {
        val button = min(dp(44f), actionSize)
        val gap = dp(6f)
        val menuCx = if (settings.position == "RIGHT") panelLeft - dp(29f) else panelRight + dp(29f)
        val rects = arrayOf(vibrateRect, silentRect, normalRect)
        val modes = intArrayOf(
            AudioManager.RINGER_MODE_VIBRATE,
            AudioManager.RINGER_MODE_SILENT,
            AudioManager.RINGER_MODE_NORMAL
        )
        val icons = arrayOf(DEFAULT_VIBRATE_SVG, DEFAULT_MUTE_SVG, DEFAULT_BELL_SVG)
        for (i in rects.indices) {
            val top = pad + i * (button + gap)
            val rect = rects[i]
            rect.set(menuCx - button / 2f, top, menuCx + button / 2f, top + button)
            val selected = ringerMode == modes[i]
            drawActionShape(
                canvas,
                rect.centerX(),
                rect.centerY(),
                button / 2f,
                if (selected) colors.accent else colors.surface,
                settings.style == "EXPRESSIVE"
            )
            drawSvgIcon(
                canvas,
                insetRect(rect, button * 0.27f),
                "",
                icons[i],
                if (selected) colors.onAccent else colors.icon
            )
        }
    }

'''
if marker not in s:
    raise SystemExit("insetRect marker missing")
s = s.replace(marker, picker + marker, 1)

# Picker touch selection + mute button opens picker.
old_touch = '''            MotionEvent.ACTION_DOWN -> {
                if (muteRect.contains(event.x, event.y)) {
                    if (settings.haptic) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onMuteToggle?.invoke()
                    return true
                }
'''
new_touch = '''            MotionEvent.ACTION_DOWN -> {
                if (modeMenuOpen) {
                    val selectedMode = when {
                        vibrateRect.contains(event.x, event.y) -> AudioManager.RINGER_MODE_VIBRATE
                        silentRect.contains(event.x, event.y) -> AudioManager.RINGER_MODE_SILENT
                        normalRect.contains(event.x, event.y) -> AudioManager.RINGER_MODE_NORMAL
                        else -> null
                    }
                    if (selectedMode != null) {
                        if (settings.haptic) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        ringerMode = selectedMode
                        setModeMenuOpen(false)
                        onModeMenuVisibilityChanged?.invoke(false)
                        onSoundModeSelected?.invoke(selectedMode)
                        return true
                    }
                }
                if (muteRect.contains(event.x, event.y)) {
                    if (settings.haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    setModeMenuOpen(!modeMenuOpen)
                    onModeMenuVisibilityChanged?.invoke(modeMenuOpen)
                    return true
                }
'''
if old_touch not in s:
    raise SystemExit("touch block missing")
s = s.replace(old_touch, new_touch, 1)

# Add bell/vibrate SVGs.
s = s.replace(
    '        private const val DEFAULT_MUTE_SVG =',
    '''        private const val DEFAULT_VIBRATE_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><g fill="none" stroke="#TINT#" stroke-width="2" stroke-linecap="round"><path d="M8 7v10a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2z"/><path d="M5 9v6M3 10.5v3M19 9v6M21 10.5v3"/></g></svg>"""
        private const val DEFAULT_BELL_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="none" stroke="#TINT#" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" d="M6 9a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6m4 9h4"/></svg>"""
        private const val DEFAULT_MUTE_SVG ='''
)

p.write_text(s, encoding="utf-8")
print("v4 volume patch applied")
