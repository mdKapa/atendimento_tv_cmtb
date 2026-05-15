package pt.cmtb.atendimentotv

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

// ============================================================
// Equivalente ao IPdfBoardRepository + ApiPdfBoardRepository
//
// Retrofit funciona por interfaces: defines as chamadas HTTP
// como funções, e o Retrofit gera o código automaticamente.
// suspend fun = função assíncrona (equivalente ao async/await Dart)
// ============================================================

interface ApiService {

    // Equivalente ao _dio.get('/board-data')
    @GET("board-data")
    suspend fun fetchBoardData(): BoardData

    // Equivalente ao _dio.get('/board-version')
    @GET("board-version")
    suspend fun fetchBoardVersion(): BoardVersion
}

// Singleton que cria e guarda uma única instância do Retrofit
// Equivalente ao ApiPdfBoardRepository com o Dio configurado
object ApiClient {

    private const val BASE_URL = "http://10.0.0.73/api/"

    // Cria o cliente HTTP com timeouts — equivalente ao BaseOptions do Dio
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        // Interceptor de logging — vês os pedidos HTTP no Logcat (como o debugPrint do Flutter)
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