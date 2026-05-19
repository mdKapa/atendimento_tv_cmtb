package pt.cmtb.atendimentotv

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * ItemDecoration que aplica espaçamento uniforme em grelhas de N colunas.
 * Garante que todas as células têm o mesmo espaço entre si e com as bordas.
 */
class GridSpacingDecoration(
    private val spanCount: Int,
    private val spacing: Int   // em píxeis
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount

        // Distribuição uniforme: cada célula recebe spacing proporcional à sua coluna
        outRect.left   = column * spacing / spanCount
        outRect.right  = spacing - (column + 1) * spacing / spanCount
        outRect.top    = if (position < spanCount) 0 else spacing
        outRect.bottom = 0
    }
}