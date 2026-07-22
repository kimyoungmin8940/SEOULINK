package com.seoulink.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 외부 API 호출에 사용하는 HTTP 클라이언트를 설정한다.
 */
@Configuration
public class HttpClientConfig {

    // 외부 API 지연이 전체 추천 요청을 오래 붙잡지 않도록 연결·응답 제한시간을 분리한다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    /**
     * OpenRouteService 전용 RestClient를 생성한다.
     *
     * <p>API 키는 클라이언트 설정에 저장하지 않고 실제 요청을 보내는
     * {@code OpenRouteServiceClient}가 환경변수에서 읽어 헤더에 추가한다.</p>
     *
     * @param baseUrl OpenRouteService 기본 주소
     * @return OpenRouteService 전용 RestClient
     */
    @Bean("openRouteServiceRestClient")
    public RestClient openRouteServiceRestClient(
            @Value("${external.openroute.base-url}") String baseUrl
    ) {
        return createRestClient(baseUrl);
    }

    /**
     * ODsay 대중교통 길찾기 전용 RestClient를 생성한다.
     *
     * <p>Server API Key는 {@code OdsayClient}가 환경변수에서 읽어 요청 쿼리에만
     * 추가하며, 이 공통 설정이나 로그에는 저장하지 않는다.</p>
     */
    @Bean("odsayRestClient")
    public RestClient odsayRestClient(
            @Value("${external.odsay.base-url}") String baseUrl
    ) {
        return createRestClient(baseUrl);
    }

    /** 외부 서비스별 기본 주소에 동일한 연결·응답 제한시간을 적용한다. */
    private RestClient createRestClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
