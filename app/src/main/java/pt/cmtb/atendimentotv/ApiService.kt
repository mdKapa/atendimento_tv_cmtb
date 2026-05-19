package pt.cmtb.atendimentotv

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// ============================================================
// API INTERNA (Laravel)
// ============================================================
interface ApiService {
    @GET("board-data")
    suspend fun fetchBoardData(): BoardData

    @GET("board-version")
    suspend fun fetchBoardVersion(): BoardVersion
}

object ApiClient {
    private const val BASE_URL = "http://10.0.0.73/api/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// ============================================================
// API IPMA — Risco de Incêndio Rural (RCM)
// Documentação: https://api.ipma.pt
//
// Endpoint: GET /open-data/forecast/meteorology/rcm/rcm-d{idDay}.json
//   idDay = 0 (hoje) | 1 (amanhã)
//
// O JSON de resposta tem a estrutura:
// { "dataPrev": "...", "local": { "DICO": { "data": { "rcm": 1 }, ... } } }
//
// DICO de Terras de Bouro = "0313"
// ============================================================

// Dados de um concelho específico dentro do JSON "local"
data class IpmaRcmConcelho(
    @SerializedName("data")      val data: IpmaRcmValor = IpmaRcmValor(),
    @SerializedName("DICO")      val dico: String = "",
    @SerializedName("latitude")  val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0
)

data class IpmaRcmValor(
    @SerializedName("rcm") val rcm: Int = 0  // 1=Reduzido … 5=Máximo
)

// Envelope raiz da resposta RCM
// O campo "local" é um Map<DICO, IpmaRcmConcelho> — chave dinâmica
data class IpmaRcmResposta(
    @SerializedName("dataPrev") val dataPrev: String = "",
    @SerializedName("dataRun")  val dataRun: String = "",
    @SerializedName("fileDate") val fileDate: String = "",
    // Gson deserializa o mapa de concelhos automaticamente
    @SerializedName("local")    val local: Map<String, IpmaRcmConcelho> = emptyMap()
)

interface IpmaApiService {
    // idDay: 0 = hoje, 1 = amanhã
    @GET("open-data/forecast/meteorology/rcm/rcm-d{idDay}.json")
    suspend fun fetchRcm(@Path("idDay") idDay: Int): IpmaRcmResposta

    @GET("open-data/forecast/warnings/warnings_www.json")
    suspend fun fetchAvisos(): List<IpmaAviso>
}

object IpmaClient {

    // DICO de Terras de Bouro = "0313"
    // Fonte: https://www.dgt.pt/ficheiros-abertos/caop/
    const val DICO_TERRAS_DE_BOURO = "0313"
    const val AREA_BRAGA = "BRG"
    const val NOME_LOCAL = "Terras de Bouro"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val service: IpmaApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.ipma.pt/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IpmaApiService::class.java)
    }

    // Código RCM (1-5) → texto português
    fun nivelTexto(rcm: Int): String = when (rcm) {
        1    -> "Reduzido"
        2    -> "Moderado"
        3    -> "Elevado"
        4    -> "Muito Elevado"
        5    -> "Máximo"
        else -> "Sem dados"
    }

    // Código RCM (1-5) → cor HEX
    fun nivelCor(rcm: Int): String = when (rcm) {
        1    -> "#4CAF50"  // verde
        2    -> "#FFC107"  // amarelo
        3    -> "#FF9800"  // laranja
        4    -> "#F44336"  // vermelho
        5    -> "#B71C1C"  // vermelho escuro
        else -> "#888888"  // cinzento (sem dados)
    }

    // Valor numérico para ordenar (maior = mais grave)
    fun nivelOrdem(rcm: Int): Int = when (rcm) {
        1 -> 1
        2 -> 2
        3 -> 3
        4 -> 4
        5 -> 5
        else -> 0
    }
}