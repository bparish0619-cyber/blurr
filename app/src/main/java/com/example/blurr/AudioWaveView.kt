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
    private val waveCount = 7 // Increased number of waves
    private val minIdleAmplitude = 0.05f // The small, constant wave when silent
    private val maxWaveHeightScale = 0.4f // Reduced max height relative to view (e.g., 40%)

    // New color palette with shades of blue, purple, and pink
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

    private val wavePaints = mutableListOf<Paint>()
    private val wavePaths = mutableListOf<Path>()
    private val waveFrequencies: FloatArray
    private val wavePhaseShifts: FloatArray
    private val waveStrokeWidths: FloatArray

    private var phase = 0f
    private var audioAmplitude = minIdleAmplitude // Start with the idle amplitude

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // Initialize arrays for dynamic properties
        waveFrequencies = FloatArray(waveCount)
        wavePhaseShifts = FloatArray(waveCount)
        waveStrokeWidths = FloatArray(waveCount)

        for (i in 0 until waveCount) {
            // Generate random properties for each wave to make them unique and non-overlapping
            waveFrequencies[i] = Random.nextFloat() * 0.6f + 0.8f // Random frequency
            wavePhaseShifts[i] = Random.nextFloat() * (Math.PI * 2).toFloat() // Random phase shift
            waveStrokeWidths[i] = Random.nextFloat() * 5f + 2f // Random stroke width

            wavePaints.add(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = waveStrokeWidths[i]
                color = waveColors[i % waveColors.size] // Cycle through our beautiful colors
                maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
            })
            wavePaths.add(Path())
        }

        // Create a continuous animation loop to move the waves horizontally
        ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            interpolator = LinearInterpolator()
            duration = 3000 // Slow down the horizontal movement for a more elegant feel
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate() // Redraw the view on every animation frame
            }
            start()
        }
    }

    /**
     * This public method allows the service to update the wave's height based on TTS audio.
     */
    fun setAmplitude(newAmplitude: Float) {
        // This formula ensures a minimum idle wave and scales the max height
        this.audioAmplitude = minIdleAmplitude + (newAmplitude * maxWaveHeightScale)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw each of the waves
        for (i in 0 until waveCount) {
            wavePaths[i].reset()
            wavePaths[i].moveTo(0f, height.toFloat())

            // The maximum height the wave can reach, now controlled by the new amplitude logic
            val maxWaveHeight = height * audioAmplitude

            for (x in 0..width step 5) {
                // The core sine wave calculation using the wave's unique properties
                val sineInput = (x * (Math.PI * 2 / width) * waveFrequencies[i]) + phase + wavePhaseShifts[i]

                // The sine result is mapped from [-1, 1] to [0, 1] so the wave is always above the baseline
                val sineOutput = (sin(sineInput) * 0.5f + 0.5f)

                val y = height - (maxWaveHeight * sineOutput)

                wavePaths[i].lineTo(x.toFloat(), y.toFloat())
            }
            canvas.drawPath(wavePaths[i], wavePaints[i])
        }
    }
}