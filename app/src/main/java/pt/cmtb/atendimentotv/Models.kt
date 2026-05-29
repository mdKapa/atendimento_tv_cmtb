package pt.cmtb.atendimentotv
import com.google.gson.annotations.SerializedName

// Modelos originais mantidos (CategoriaModel, DocumentoModel, etc...)
data class CategoriaModel(
    @SerializedName("id")   val id: String,
    @SerializedName("nome") val nome: String
)

/** Categoria fixa na grelha (não vem da API). Futuro: URL configurável pela API. */
object CategoriaEstatica {
    const val EVENTOS_ID = "__eventos_estatico__"
    val EVENTOS = CategoriaModel(id = EVENTOS_ID, nome = "Eventos")
}

data class DocumentoModel(
    @SerializedName("id")           val id: String,
    @SerializedName("categoria_id") val categoriaId: String,
    @SerializedName("titulo")       val titulo: String,
    @SerializedName("data_criacao") val dataCriacao: String,
    @SerializedName("paginas")      val paginas: List<String> = emptyList()
)

data class BoardData(
    @SerializedName("categorias") val categorias: List<CategoriaModel> = emptyList(),
    @SerializedName("documentos") val documentos: List<DocumentoModel> = emptyList()
)

data class BoardVersion(
    @SerializedName("version") val version: Int = 0
)

// ============================================================
// IPMA - Estados de UI (Tempo + Risco de Incêndio)
// ============================================================

// Estado combinado para popular um widget completo "Hoje" ou "Amanhã"
data class IpmaWidgetState(
    val titulo: String,
    val idTipoTempo: Int = 0,
    val descricaoTempo: String = "A carregar...",
    val tempMaxMin: String = "-- | --",
    val tempoIconRes: Int = android.R.drawable.ic_menu_help,
    val riscoNivel: String = "A carregar...",
    val classRisco: Int = 0,
    val riscoCor: String = "#888888"
)

data class IpmaAviso(
    @SerializedName("idAreaAviso") val idAreaAviso: String,
    @SerializedName("startTime")   val startTime: String,
    @SerializedName("endTime")     val endTime: String,
    @SerializedName("awarenessLevel") val awarenessLevel: String,
    @SerializedName("awarenessTypeName") val awarenessTypeName: String,
    @SerializedName("description") val description: String
)

// ============================================================
// API de Previsão do Tempo (IPMA)
// ============================================================
data class IpmaForecastResponse(
    @SerializedName("owner") val owner: String,
    @SerializedName("country") val country: String,
    @SerializedName("forecastDate") val forecastDate: String,
    @SerializedName("dataUpdate") val dataUpdate: String,
    @SerializedName("data") val data: List<IpmaForecastData>
)

data class IpmaForecastData(
    @SerializedName("precipitaProb") val precipitaProb: String,
    @SerializedName("tMin") val tMin: String,
    @SerializedName("tMax") val tMax: String,
    @SerializedName("predWindDir") val predWindDir: String,
    @SerializedName("idWeatherType") val idWeatherType: Int,
    @SerializedName("classWindSpeed") val classWindSpeed: Int,
    @SerializedName("longitude") val longitude: String,
    @SerializedName("latitude") val latitude: String,
    @SerializedName("globalIdLocal") val globalIdLocal: Int
)