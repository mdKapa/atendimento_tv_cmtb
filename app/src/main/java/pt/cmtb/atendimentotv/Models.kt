package pt.cmtb.atendimentotv
import com.google.gson.annotations.SerializedName

// ============================================================
// Equivalente direto ao document_model.dart
// data class = classe imutável, ideal para dados vindos de API
// @SerializedName = como o Gson lê os campos do JSON
// ============================================================

data class CategoriaModel(
    @SerializedName("id")   val id: String,
    @SerializedName("nome") val nome: String
)

data class DocumentoModel(
    @SerializedName("id")           val id: String,
    @SerializedName("categoria_id") val categoriaId: String,
    @SerializedName("titulo")       val titulo: String,
    @SerializedName("data_criacao") val dataCriacao: String,
    // Lista de URLs das páginas — equivalente ao List<String> paginas do Dart
    @SerializedName("paginas")      val paginas: List<String> = emptyList()
)

// Envelope raiz do JSON da API — equivalente ao BoardData do Dart
data class BoardData(
    @SerializedName("categorias") val categorias: List<CategoriaModel> = emptyList(),
    @SerializedName("documentos") val documentos: List<DocumentoModel> = emptyList()
)

// Resposta do endpoint /board-version
data class BoardVersion(
    @SerializedName("version") val version: Int = 0
)