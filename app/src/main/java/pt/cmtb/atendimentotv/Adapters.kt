package pt.cmtb.atendimentotv

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.cmtb.atendimentotv.databinding.ItemCategoriaBinding
import pt.cmtb.atendimentotv.databinding.ItemDocumentoBinding

// ============================================================
// ADAPTADOR DE CATEGORIAS — Tarefa 3: grelha 2 colunas
// O GridLayoutManager com spanCount=2 é configurado na
// MainActivity, não aqui. O adapter é igual ao anterior.
// ============================================================
class CategoriasAdapter(
    private val onCategoriaClick: (CategoriaModel) -> Unit
) : ListAdapter<CategoriaModel, CategoriasAdapter.ViewHolder>(CategoriaDiff()) {

    private var categoriaAtivaId: String? = null

    fun setCategoriaAtiva(id: String?) {
        val anterior = categoriaAtivaId
        categoriaAtivaId = id
        if (anterior != id) notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemCategoriaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoriaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val categoria = getItem(position)
        val isSelected = categoria.id == categoriaAtivaId

        val ctx = holder.binding.root.context
        with(holder.binding) {
            tvNomeCategoria.text = categoria.nome

            if (isSelected) {
                itemCategoriaRoot.setBackgroundColor(ctx.getColor(R.color.cat_ativa_bg))
                tvNomeCategoria.setTextColor(ctx.getColor(R.color.cat_ativa_texto))
                tvNomeCategoria.setTypeface(null, Typeface.BOLD)
                imgCategoria.setColorFilter(ctx.getColor(R.color.cat_ativa_icone))
            } else {
                itemCategoriaRoot.setBackgroundColor(ctx.getColor(R.color.bg_card))
                tvNomeCategoria.setTextColor(ctx.getColor(R.color.texto_inativo))
                tvNomeCategoria.setTypeface(null, Typeface.NORMAL)
                imgCategoria.setColorFilter(ctx.getColor(R.color.cat_icone_stroke))
            }

            imgCategoria.setImageResource(getIconForCategoria(categoria.nome))
            root.setOnClickListener { onCategoriaClick(categoria) }
        }
    }

    private fun getIconForCategoria(nome: String): Int {
        val lower = nome.lowercase()
        return when {
            lower.contains("editais") || lower.contains("avisos") ->
                android.R.drawable.ic_menu_send
            lower.contains("cultura") || lower.contains("lazer") ->
                android.R.drawable.ic_menu_gallery
            lower.contains("turismo") ->
                android.R.drawable.ic_menu_mapmode
            lower.contains("serviços") || lower.contains("servicos") ->
                android.R.drawable.ic_menu_preferences
            lower.contains("notícias") || lower.contains("noticias") ->
                android.R.drawable.ic_menu_info_details
            else -> android.R.drawable.ic_menu_agenda
        }
    }

    class CategoriaDiff : DiffUtil.ItemCallback<CategoriaModel>() {
        override fun areItemsTheSame(old: CategoriaModel, new: CategoriaModel) = old.id == new.id
        override fun areContentsTheSame(old: CategoriaModel, new: CategoriaModel) = old == new
    }
}

// ============================================================
// ADAPTADOR DE DOCUMENTOS — Tarefa 4: grelha 2 linhas + scroll horizontal
//
// A magia está no MainActivity onde configuramos:
//   GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
//
// Isto significa: 2 spans na vertical (2 linhas fixas),
// crescimento para a direita (horizontal), scroll horizontal.
// Cada item ocupa 1 span. Com 8 docs → 4 colunas × 2 linhas.
// Com 12 docs → 6 colunas × 2 linhas, com scroll para a direita.
// ============================================================
class DocumentosAdapter(
    private val onDocumentoClick: (DocumentoModel) -> Unit
) : ListAdapter<DocumentoModel, DocumentosAdapter.ViewHolder>(DocumentoDiff()) {

    private var documentoAtivoId: String? = null

    fun setDocumentoAtivo(id: String?) {
        val anterior = documentoAtivoId
        documentoAtivoId = id
        if (anterior != id) notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemDocumentoBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val doc = getItem(position)
        val isSelected = doc.id == documentoAtivoId

        with(holder.binding) {
            tvTituloDocumento.text = doc.titulo
            // Mostra só a data sem horas — "2026-05-15T00:00:00" → "2026-05-15"
            tvDataDocumento.text = doc.dataCriacao.take(10)

            if (isSelected) {
                itemDocumentoRoot.setBackgroundColor(Color.parseColor("#FF1D4D79"))
                tvTituloDocumento.setTextColor(Color.WHITE)
                tvTituloDocumento.setTypeface(null, Typeface.BOLD)
                tvDataDocumento.setTextColor(Color.parseColor("#FFB3B3B3"))
            } else {
                itemDocumentoRoot.setBackgroundColor(Color.parseColor("#FF1A1A1A"))
                tvTituloDocumento.setTextColor(Color.parseColor("#FFB3B3B3"))
                tvTituloDocumento.setTypeface(null, Typeface.NORMAL)
                tvDataDocumento.setTextColor(Color.parseColor("#FF888888"))
            }

            root.setOnClickListener { onDocumentoClick(doc) }
        }
    }

    class DocumentoDiff : DiffUtil.ItemCallback<DocumentoModel>() {
        override fun areItemsTheSame(old: DocumentoModel, new: DocumentoModel) = old.id == new.id
        override fun areContentsTheSame(old: DocumentoModel, new: DocumentoModel) = old == new
    }

    // Função auxiliar chamada pela MainActivity para criar o LayoutManager correto
    companion object {
        fun criarLayoutManager(context: android.content.Context): GridLayoutManager {
            // spanCount=2 + HORIZONTAL = 2 linhas fixas com scroll para a direita
            return GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
        }
    }
}