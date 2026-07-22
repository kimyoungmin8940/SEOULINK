package com.seoulink.backend.infrastructure.external.openroute;

import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteMatrixResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Matrix API 요청 형식과 거리·시간 단위 변환, API 키 검증을 확인한다. */
class OpenRouteServiceClientTest {

    @Test
    @DisplayName("Matrix API 거리와 시간을 km와 분 단위로 변환한다")
    void calculateMatrixConvertsDistanceAndDuration() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openrouteservice.org");
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(builder).build();
        OpenRouteServiceClient client = new OpenRouteServiceClient(
                builder.build(),
                "test-api-key"
        );

        mockServer.expect(requestTo(
                        "https://api.openrouteservice.org/v2/matrix/foot-walking"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "test-api-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "distances": [[0.0, 1.2], [1.2, 0.0]],
                          "durations": [[0.0, 900.0], [900.0, 0.0]]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        RouteMatrixResult result = client.calculateMatrix(
                "foot-walking",
                List.of(
                        new RouteCoordinate(126.9780, 37.5665),
                        new RouteCoordinate(126.9770, 37.5796)
                )
        );

        assertEquals(1.2, result.getDistanceKm(0, 1), 0.000001);
        assertEquals(15.0, result.getTravelTimeMinutes(0, 1), 0.000001);
        mockServer.verify();
    }

    @Test
    @DisplayName("요청별 자동차 프로필을 Matrix API 경로에 적용한다")
    void calculateMatrixUsesRequestedDrivingProfile() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openrouteservice.org");
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(builder).build();
        OpenRouteServiceClient client = new OpenRouteServiceClient(
                builder.build(),
                "test-api-key"
        );

        mockServer.expect(requestTo(
                        "https://api.openrouteservice.org/v2/matrix/driving-car"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "distances": [[0.0, 1.4], [1.4, 0.0]],
                          "durations": [[0.0, 360.0], [360.0, 0.0]]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        RouteMatrixResult result = client.calculateMatrix(
                "driving-car",
                List.of(
                        new RouteCoordinate(126.9780, 37.5665),
                        new RouteCoordinate(126.9770, 37.5796)
                )
        );

        assertEquals(6.0, result.getTravelTimeMinutes(0, 1), 0.000001);
        mockServer.verify();
    }

    @Test
    @DisplayName("API 키가 없으면 외부 요청 전에 예외가 발생한다")
    void calculateMatrixRejectsMissingApiKey() {
        OpenRouteServiceClient client = new OpenRouteServiceClient(
                RestClient.create("https://api.openrouteservice.org"),
                ""
        );

        assertFalse(client.isConfigured());
        assertThrows(
                IllegalStateException.class,
                () -> client.calculateMatrix(
                        "foot-walking",
                        List.of(
                                new RouteCoordinate(126.9780, 37.5665),
                                new RouteCoordinate(126.9770, 37.5796)
                        )
                )
        );
    }
}
