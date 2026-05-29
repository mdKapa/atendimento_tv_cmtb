package pt.cmtb.atendimentotv

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * Escala o conteúdo ao máximo dentro do palco sem distorcer:
 * o primeiro eixo a encostar (largura ou altura) define o tamanho.
 * Equivalente a fitCenter, mas garante matrix explícita após layout/Glide.
 */
class StageFitImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        applyFitMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyFitMatrix()
    }

    private fun applyFitMatrix() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth
        val dh = d.intrinsicHeight
        if (dw <= 0 || dh <= 0 || width <= 0 || height <= 0) return

        val scale = minOf(width.toFloat() / dw, height.toFloat() / dh)
        val dx = (width - dw * scale) / 2f
        val dy = (height - dh * scale) / 2f

        imageMatrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
    }
}
