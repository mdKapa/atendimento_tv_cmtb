package pt.cmtb.atendimentotv

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.cmtb.atendimentotv.databinding.ItemCategoriaBinding
import pt.cmtb.atendimentotv.databinding.ItemDocumentoBinding

// ============================================================
// ADAPTADOR DE CATEGORIAS
// Equivalente ao itemBuilder do ListView das categorias
//
// ListAdapter detecta automaticamente o que mudou na lista
// e só redesenha os itens necessários — muito eficiente na TV
// ============================================================

class CategoriasAdapter(
    private val onCategoriaClick: (CategoriaModel) -> Unit
) : ListAdapter<CategoriaModel, CategoriasAdapter.ViewHolder>(CategoriaDiff()) {

    // ID da categoria atualmente selecionada
    private var categoriaAtivaId: String? = null

    fun setCategoriaAtiva(id: String?) {
        val anterior = categoriaAtivaId
        categoriaAtivaId = id
        // Só redesenha os itens que mudaram de estado (selecionado ↔ normal)
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

        with(holder.binding) {
            tvNomeCategoria.text = categoria.nome

            // Equivalente ao gradient/color do Container do Flutter
            if (isSelected) {
                itemCategoriaRoot.setBackgroundColor(Color.parseColor("#FF1D4D79"))
                tvNomeCategoria.setTextColor(Color.WHITE)
                tvNomeCategoria.setTypeface(null, android.graphics.Typeface.BOLD)
                imgCategoria.setColorFilter(Color.WHITE)
            } else {
                itemCategoriaRoot.setBackgroundColor(Color.parseColor("#FF1A1A1A"))
                tvNomeCategoria.setTextColor(Color.parseColor("#FFB3B3B3"))
                tvNomeCategoria.setTypeface(null, android.graphics.Typeface.NORMAL)
                imgCategoria.setColorFilter(Color.parseColor("#FF888888"))
            }

            // Ícone baseado no nome — equivalente ao _getIconForCategory do Flutter
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

    // DiffUtil: o algoritmo que compara listas para saber o que mudou
    class CategoriaDiff : DiffUtil.ItemCallback<CategoriaModel>() {
        override fun areItemsTheSame(old: CategoriaModel, new: CategoriaModel) =
            old.id == new.id
        override fun areContentsTheSame(old: CategoriaModel, new: CategoriaModel) =
            old == new
    }
}

// ============================================================
// ADAPTADOR DE DOCUMENTOS
// Equivalente ao DocumentCard — lista horizontal
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
            tvDataDocumento.text = doc.dataCriacao.take(10) // mostra só a data, sem horas

            if (isSelected) {
                itemDocumentoRoot.setBackgroundColor(Color.parseColor("#FF1D4D79"))
                tvTituloDocumento.setTextColor(Color.WHITE)
                tvTituloDocumento.setTypeface(null, android.graphics.Typeface.BOLD)
                tvDataDocumento.setTextColor(Color.parseColor("#FFB3B3B3"))
            } else {
                itemDocumentoRoot.setBackgroundColor(Color.parseColor("#FF1A1A1A"))
                tvTituloDocumento.setTextColor(Color.parseColor("#FFB3B3B3"))
                tvTituloDocumento.setTypeface(null, android.graphics.Typeface.NORMAL)
                tvDataDocumento.setTextColor(Color.parseColor("#FF888888"))
            }

            root.setOnClickListener { onDocumentoClick(doc) }
        }
    }

    class DocumentoDiff : DiffUtil.ItemCallback<DocumentoModel>() {
        override fun areItemsTheSame(old: DocumentoModel, new: DocumentoModel) =
            old.id == new.id
        override fun areContentsTheSame(old: DocumentoModel, new: DocumentoModel) =
            old == new
    }
}