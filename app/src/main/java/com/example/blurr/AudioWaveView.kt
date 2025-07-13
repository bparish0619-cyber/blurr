package com.example.blurr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class AudioWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    // --- Configuration Constants ---
    private val waveCount = 7
    private val minIdleAmplitude = 0.15f
    private val maxWaveHeightScale = 0.25f // Increased for more impressive peaks
    private val amplitudeTransitionDuration = 500L
    private val realtimweAmplitudeTransitionDuration = 100L
    // --- NEW: Jitter configuration ---4
    private val maxSpeedIncrease = 4.0f // At full amplitude, waves move 1x + 4x = 5x faster

    private val jitterAmount = 0.1f // How much vertical "randomness" to add

    private val waveColors = intArrayOf(
        "#8A2BE2".toColorInt(), "#4169E1".toColorInt(), "#FF1493".toColorInt(),
        "#9370DB".toColorInt(), "#00BFFF".toColorInt(), "#FF69B4".toColorInt(),
        "#DA70D6".toColorInt()
    )

    private var amplitudeAnimator: ValueAnimator? = null
    private val wavePaints = mutableListOf<Paint>()
    private val wavePaths = mutableListOf<Path>()
    private val waveFrequencies: FloatArray
    private val wavePhaseShifts: FloatArray
    private val waveSpeeds: FloatArray
    private val waveAmplitudeMultipliers: FloatArray

    private var audioAmplitude = minIdleAmplitude

    init {
        // Use hardware acceleration for smoother rendering if possible
        setLayerType(LAYER_TYPE_HARDWARE, null)

        waveFrequencies = FloatArray(waveCount)
        wavePhaseShifts = FloatArray(waveCount)
        waveSpeeds = FloatArray(waveCount)
        waveAmplitudeMultipliers = FloatArray(waveCount)

        // --- CHANGED: Paint setup now includes a blur effect ---
        val blurRadius = 15f // Adjust for more or less blur
        val blurFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until waveCount) {
            waveFrequencies[i] = Random.nextFloat() * 0.6f + 0.8f
            wavePhaseShifts[i] = Random.nextFloat() * (Math.PI * 2).toFloat()
            waveSpeeds[i] = Random.nextFloat() * 0.02f + 0.01f
            waveAmplitudeMultipliers[i] = Random.nextFloat() * 0.5f + 0.8f

            wavePaints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = waveColors[i % waveColors.size]
                alpha = 120 // Slightly more opaque
                maskFilter = blurFilter // Apply the soft blur
            })
            wavePaths.add(Path())
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            interpolator = LinearInterpolator()
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                // Calculate a speed multiplier based on the current vertical amplitude
                val speedFactor = 1.0f + (audioAmplitude * maxSpeedIncrease)

                for (i in 0 until waveCount) {
                    // Update phase using the base speed and the new speed factor
                    wavePhaseShifts[i] += (waveSpeeds[i] * speedFactor)
                }
                invalidate()
            }
            start()
        }
//        ValueAnimator.ofFloat(0f, 1f).apply {
//            interpolator = LinearInterpolator()
//            duration = 5000
//            repeatCount = ValueAnimator.INFINITE
//            addUpdateListener {
//                for (i in 0 until waveCount) {
//                    wavePhaseShifts[i] = (wavePhaseShifts[i] + waveSpeeds[i])
//                }
//                invalidate()
//            }
//            start()
//        }
    }

    // --- CHANGED: This is now truly REAL-TIME and much more efficient ---
    /**
     * Instantly sets the amplitude for real-time visualization.
     * This is now highly efficient and bypasses animation for immediate feedback.
     * @param amplitude The raw amplitude from the visualizer (0.0 to 1.0).
     */
    fun setRealtimeAmplitude(amplitude: Float) {
        // Using a power function makes the waves more sensitive to quieter sounds
        val scaledAmplitude = amplitude.pow(1.5f).coerceIn(0.0f, 1.0f)
        val targetAmplitude = minIdleAmplitude + (scaledAmplitude * maxWaveHeightScale)
        // No animation, just set the value. The main animator's invalidate() will handle the redraw.
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(audioAmplitude, targetAmplitude).apply {
            duration = amplitudeTransitionDuration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                audioAmplitude = animation.animatedValue as Float
            }
            start()
        }
    }

    /**
     * Smoothly animates the wave's amplitude to a new target level.
     * Used for the initial "power up" and final "power down" effect.
     * @param target The target amplitude level (0.0f for idle, 1.0f for full).
     */
    fun setTargetAmplitude(target: Float) {
        val targetAmplitude = minIdleAmplitude + (target * maxWaveHeightScale)
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(audioAmplitude, targetAmplitude).apply {
            duration = realtimweAmplitudeTransitionDuration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                audioAmplitude = animation.animatedValue as Float
            }
            start()
        }
    }

    // --- CHANGED: onSizeChanged now sets up our gradients ---
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // This is the best place to create gradients, as we know the view's height.
        for(i in 0 until waveCount) {
            val paint = wavePaints[i]
            val color = waveColors[i % waveColors.size]
            // Create a gradient from the wave's color to transparent
            paint.shader = LinearGradient(
                0f, h / 2f, 0f, h.toFloat(),
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (i in 0 until waveCount) {
            wavePaths[i].reset()
            wavePaths[i].moveTo(0f, height.toFloat())

            val waveMaxHeight = height * audioAmplitude * waveAmplitudeMultipliers[i]

            // --- NEW: Add a subtle, random jitter for a more organic feel ---
            val currentJitter = (Random.nextFloat() - 0.5f) * waveMaxHeight * jitterAmount

            for (x in 0..width step 5) {
                val sineInput = (x * (Math.PI * 2 / width) * waveFrequencies[i]) + wavePhaseShifts[i]
                val sineOutput = (sin(sineInput) * 0.5f + 0.5f)
                val y = height - (waveMaxHeight * sineOutput) + currentJitter
                wavePaths[i].lineTo(x.toFloat(), y.toFloat())
            }

            wavePaths[i].lineTo(width.toFloat(), height.toFloat())
            wavePaths[i].close()
            canvas.drawPath(wavePaths[i], wavePaints[i])
        }
    }
}