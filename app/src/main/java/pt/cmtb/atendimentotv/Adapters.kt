package pt.cmtb.atendimentotv

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
// ADAPTADOR DE CATEGORIAS
// Grelha 2 colunas × 2 linhas (4 categorias: 3 API + Eventos).
// Categorias sem documentos são ocultadas pelo ViewModel antes
// de chegarem aqui — a grelha mantém sempre 2 colunas.
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
                // Drawable com cantos redondos + cor azul + borda
                itemCategoriaRoot.setBackgroundResource(R.drawable.bg_card_ativo)
                tvNomeCategoria.setTextColor(ctx.getColor(R.color.cat_ativa_texto))
                tvNomeCategoria.setTypeface(null, Typeface.BOLD)
                imgCategoria.setColorFilter(ctx.getColor(R.color.cat_ativa_icone))
            } else {
                // Drawable com cantos redondos + cor escura
                itemCategoriaRoot.setBackgroundResource(R.drawable.bg_card_normal)
                tvNomeCategoria.setTextColor(ctx.getColor(R.color.cat_texto))
                tvNomeCategoria.setTypeface(null, Typeface.NORMAL)
                imgCategoria.setColorFilter(ctx.getColor(R.color.cat_icone_stroke))
            }

            val iconRes = if (categoria.id == CategoriaEstatica.EVENTOS_ID) {
                R.drawable.cat_calendar
            } else {
                getIconForCategoria(categoria.nome)
            }
            imgCategoria.setImageResource(iconRes)
            root.setOnClickListener { onCategoriaClick(categoria) }
        }
    }

    private fun getIconForCategoria(nome: String): Int {
        val lower = nome.lowercase()
        return when {
            lower.contains("informações") || lower.contains("avisos") ->
                R.drawable.cat_avisos
            lower.contains("eventos") || lower.contains("agenda") ->
                R.drawable.cat_calendar
            lower.contains("calendário") ->
                R.drawable.cat_calendar
            lower.contains("serviços") || lower.contains("servicos") ->
                R.drawable.cat_folders
            lower.contains("notícias") || lower.contains("noticias") ->
                R.drawable.cat_folders
            else ->  R.drawable.cat_folders
        }
    }

    class CategoriaDiff : DiffUtil.ItemCallback<CategoriaModel>() {
        override fun areItemsTheSame(old: CategoriaModel, new: CategoriaModel) = old.id == new.id
        override fun areContentsTheSame(old: CategoriaModel, new: CategoriaModel) = old == new
    }

    companion object {
        // 2 colunas fixas — grelha 2×2 (4 categorias)
        fun criarLayoutManager(context: android.content.Context): GridLayoutManager {
            val lm = GridLayoutManager(context, 2)
            // Espaço uniforme de 4dp entre todas as células
            return lm
        }
    }
}

// ============================================================
// ADAPTADOR DE DOCUMENTOS
// Grelha 2 linhas fixas com scroll horizontal.
// GridLayoutManager(spanCount=2, HORIZONTAL) = 2 linhas × N colunas.
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
        val ctx = holder.binding.root.context

        with(holder.binding) {
            tvTituloDocumento.text = doc.titulo
            tvDataDocumento.text = doc.dataCriacao.take(10)

            if (isSelected) {
                // Fundo Ativo
                itemDocumentoRoot.setBackgroundResource(R.drawable.bg_doc_ativo)

                // Título em destaque (Cor da categoria ativa e Bold)
                tvTituloDocumento.setTextColor(ctx.getColor(R.color.cat_ativa_texto))
                tvTituloDocumento.setTypeface(null, Typeface.BOLD)

                tvDataDocumento.setTextColor(ctx.getColor(R.color.texto_secundario))
            } else {
                // Fundo Normal/Inativo
                itemDocumentoRoot.setBackgroundResource(R.drawable.bg_card_normal)

                // Título normal (Cor inativa e sem Bold)
                tvTituloDocumento.setTextColor(ctx.getColor(R.color.cat_texto))
                tvTituloDocumento.setTypeface(null, Typeface.NORMAL)

                tvDataDocumento.setTextColor(ctx.getColor(R.color.texto_inativo))
            }

            root.setOnClickListener { onDocumentoClick(doc) }
        }
    }

    class DocumentoDiff : DiffUtil.ItemCallback<DocumentoModel>() {
        override fun areItemsTheSame(old: DocumentoModel, new: DocumentoModel) = old.id == new.id
        override fun areContentsTheSame(old: DocumentoModel, new: DocumentoModel) = old == new
    }

    companion object {
        // 2 linhas fixas + scroll horizontal
        fun criarLayoutManager(context: android.content.Context): GridLayoutManager =
            GridLayoutManager(context, 1, GridLayoutManager.HORIZONTAL, false)
    }
}