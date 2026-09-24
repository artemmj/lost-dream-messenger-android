package com.example.messenger.data

object ApiConfig {
    // 10.0.2.2 — это localhost машины-хоста, доступный из эмулятора
    const val BASE_HTTP = "http://10.0.2.2:8000/api/v1/"
    const val BASE_WS = "ws://10.0.2.2:8000/ws/"

    /**
     * AllowedHostsOriginValidator снимает handshake с 403, если заголовка Origin нет
     * вообще: браузеры шлют его сами, OkHttp — только по запросу.
     */
    const val WS_ORIGIN = "http://10.0.2.2:8000"
}
