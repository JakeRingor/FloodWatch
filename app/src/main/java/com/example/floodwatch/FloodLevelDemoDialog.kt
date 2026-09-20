package com.example.floodwatch

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide

/** Local presentation examples; deliberately separate from report capture and submission. */
class FloodLevelDemoDialog : DialogFragment() {
    private data class Scene(val title: String, val folder: String, val levels: List<Int>)
    private val scenes = listOf(
        Scene("B.A. Cruz — front view", "ba_front", (0..3).toList()),
        Scene("B.A. Cruz — side view", "ba_side", (0..4).toList()),
        Scene("Tudela", "tudela", (0..4).toList())
    )
    private var sceneIndex = 0
    private var levelIndex = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        sceneIndex = (savedInstanceState?.getInt("scene") ?: 0).coerceIn(scenes.indices)
        levelIndex = (savedInstanceState?.getInt("level") ?: 0).coerceIn(scenes[sceneIndex].levels.indices)
        val context = requireContext()
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(16))
        }
        fun label(value: String, size: Float = 15f) = TextView(context).apply {
            text = value
            textSize = size
            setPadding(0, dp(8), 0, dp(8))
            content.addView(this)
        }
        label("SIMULATION • PRESENTATION DEMO", 13f)
        label("Prepared images with saved scenario labels. These are not AI predictions or verified water-depth measurements.")
        label("Depth estimation requires clearly readable height markings on the post and a visible waterline. If either is missing or unclear, estimation is unavailable.")
        label("Location / angle")
        val scenePicker = Spinner(context).also { content.addView(it) }
        label("Example")
        val levelPicker = Spinner(context).also { content.addView(it) }
        val result = label("", 20f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val preview = ImageView(context).apply {
            adjustViewBounds = true
            maxHeight = dp(380)
            scaleType = ImageView.ScaleType.FIT_CENTER
            content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        label("Prototype using AI-edited flood examples. Automatic depth measurement is under development. Demo images are not submitted as flood reports.")
        fun render() {
            val scene = scenes[sceneIndex]
            val level = scene.levels[levelIndex]
            val description = if (level == 0) "Dry reference — no simulated flooding" else "Intended level: $level ft — simulated example"
            result.text = description
            preview.contentDescription = "${scene.title}. $description"
            Glide.with(this).load("file:///android_asset/flood_demo/${scene.folder}/$level.jpg")
                .override(1280, 1280).fitCenter().into(preview)
        }
        fun updateLevels() {
            levelPicker.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                scenes[sceneIndex].levels.map { if (it == 0) "Dry reference" else "$it ft scenario (unverified)" })
            levelPicker.setSelection(levelIndex)
            render()
        }
        scenePicker.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, scenes.map { it.title })
        scenePicker.setSelection(sceneIndex)
        scenePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != sceneIndex) {
                    sceneIndex = position
                    levelIndex = 0
                    updateLevels()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        levelPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                levelIndex = position.coerceIn(scenes[sceneIndex].levels.indices)
                render()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        updateLevels()
        return AlertDialog.Builder(context).setTitle("Flood-Level Demo")
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton("Close", null).create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("scene", sceneIndex)
        outState.putInt("level", levelIndex)
        super.onSaveInstanceState(outState)
    }
}
