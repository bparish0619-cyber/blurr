package com.example.blurr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.sin
import kotlin.random.Random

class AudioWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    // --- Configuration Constants to Tune the Look ---
    private val waveCount = 7
    private val minIdleAmplitude = 0.15f
    private val maxWaveHeightScale = 0.25f

    private val waveColors = intArrayOf(
        "#8A2BE2".toColorInt(), // BlueViolet
        "#4169E1".toColorInt(), // RoyalBlue
        "#FF1493".toColorInt(), // DeepPink
        "#9370DB".toColorInt(), // MediumPurple
        "#00BFFF".toColorInt(), // DeepSkyBlue
        "#FF69B4".toColorInt(), // HotPink
        "#DA70D6".toColorInt()  // Orchid
    )
    // --- End of Configuration ---

    // --- Add these new properties ---
    private var amplitudeAnimator: ValueAnimator? = null
    private val amplitudeTransitionDuration = 500L // Duration for the smooth transition in milliseconds
    // ---

    private val wavePaints = mutableListOf<Paint>()
    private val wavePaths = mutableListOf<Path>()
    private val waveFrequencies: FloatArray
    private val wavePhaseShifts: FloatArray

    // --- NEW: Added properties for more randomness ---
    private val waveSpeeds: FloatArray
    private val waveAmplitudeMultipliers: FloatArray
    // --- End of New Properties ---

    private var audioAmplitude = minIdleAmplitude

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // Initialize all property arrays
        waveFrequencies = FloatArray(waveCount)
        wavePhaseShifts = FloatArray(waveCount)
        waveSpeeds = FloatArray(waveCount)
        waveAmplitudeMultipliers = FloatArray(waveCount)

        for (i in 0 until waveCount) {
            // Assign unique random properties to each wave
            waveFrequencies[i] = Random.nextFloat() * 0.6f + 0.8f // Unique wave shape
            wavePhaseShifts[i] = Random.nextFloat() * (Math.PI * 2).toFloat() // Unique starting position
            waveSpeeds[i] = Random.nextFloat() * 0.02f + 0.01f // Unique horizontal speed
            waveAmplitudeMultipliers[i] = Random.nextFloat() * 0.5f + 0.8f // Unique height multiplier (80% to 130%)

            wavePaints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = waveColors[i % waveColors.size]
                alpha = 100

            })
            wavePaths.add(Path())
        }

        // The animator now acts as a "ticker" to drive the animation frames.
        ValueAnimator.ofFloat(0f, 1f).apply { // The values don't matter, just the callback.
            interpolator = LinearInterpolator()
            duration = 5000 // A long duration for a smooth continuous loop
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                // --- CHANGE #2: UPDATE EACH WAVE'S PHASE INDEPENDENTLY ---
                for (i in 0 until waveCount) {
                    // Increment each wave's horizontal position by its unique speed
                    wavePhaseShifts[i] = (wavePhaseShifts[i] + waveSpeeds[i])
                }
                invalidate() // Redraw the view on every frame
                // --- END OF CHANGE #2 ---
            }
            start()
        }
    }

    // --- REPLACE the old setAmplitude function with this new one ---
    /**
     * Smoothly animates the wave's amplitude to a new target level.
     * @param target The target amplitude level (0.0f for idle, 1.0f for full).
     */
    fun setTargetAmplitude(target: Float) {
        val targetAmplitude = minIdleAmplitude + (target * maxWaveHeightScale)

        // Cancel any previous animation and start a new one
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(audioAmplitude, targetAmplitude).apply {
            duration = amplitudeTransitionDuration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                // This gets called on every frame of the animation, updating the height smoothly
                audioAmplitude = animation.animatedValue as Float
            }
            start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (i in 0 until waveCount) {
            wavePaths[i].reset()
            wavePaths[i].moveTo(0f, height.toFloat())

            // --- CHANGE #3: USE THE UNIQUE AMPLITUDE MULTIPLIER ---
            // Calculate the max height for this specific wave
            val waveMaxHeight = height * audioAmplitude * waveAmplitudeMultipliers[i]
            // --- END OF CHANGE #3 ---

            for (x in 0..width step 5) {
                // The sine input now just uses the continuously updated phase shift
                val sineInput = (x * (Math.PI * 2 / width) * waveFrequencies[i]) + wavePhaseShifts[i]
                val sineOutput = (sin(sineInput) * 0.5f + 0.5f)
                val y = height - (waveMaxHeight * sineOutput)
                wavePaths[i].lineTo(x.toFloat(), y.toFloat())
            }
            wavePaths[i].lineTo(width.toFloat(), height.toFloat())
            wavePaths[i].close()
            canvas.drawPath(wavePaths[i], wavePaints[i])
        }
    }
}