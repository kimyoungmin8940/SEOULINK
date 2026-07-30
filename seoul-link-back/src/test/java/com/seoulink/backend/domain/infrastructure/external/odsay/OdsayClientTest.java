package com.seoulink.backend.domain.infrastructure.external.odsay;

import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient.OdsayApiException;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient.TransitRouteResult;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** ODsay 요청 인코딩, 최단시간 경로 선택, 단위 변환과 오류 처리를 검증한다. */
class OdsayClientTest {

    @Test
    @DisplayName("대중교통 경로 중 최단시간 결과를 km와 분 단위로 반환한다")
    void calculateRouteSelectsFastestPathAndConvertsDistance() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.odsay.com/v1/api");
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(builder).build();
        OdsayClient client = new OdsayClient(
                builder.build(),
                "test+api/key"
        );

        mockServer.expect(requestTo(
                        "https://api.odsay.com/v1/api/searchPubTransPathT"
                                + "?SX=126.978&SY=37.5665"
                                + "&EX=126.977&EY=37.5796"
                                + "&OPT=0&SearchType=0&SearchPathType=0"
                                + "&apiKey=test%2Bapi%2Fkey"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "result": {
                            "searchType": 0,
                            "path": [
                              {
                                "pathType": 2,
                                "info": {
                                  "totalTime": 24,
                                  "totalDistance": 3200
                                }
                              },
                              {
                                "pathType": 3,
                                "info": {
                                  "totalTime": 18,
                                  "totalDistance": 2800
                                }
                              }
                            ]
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        TransitRouteResult result = client.calculateRoute(
                new RouteCoordinate(126.9780, 37.5665),
                new RouteCoordinate(126.9770, 37.5796)
        );

        assertEquals(2.8, result.distanceKm(), 0.000001);
        assertEquals(18.0, result.travelTimeMinutes(), 0.000001);
        assertEquals(TransitPathType.BUS_SUBWAY, result.transitPathType());
        mockServer.verify();
    }

    @Test
    @DisplayName("이미 URL 인코딩된 API 키도 이중 인코딩하지 않는다")
    void calculateRouteAvoidsDoubleEncodingForEncodedApiKey() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.odsay.com/v1/api");
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(builder).build();
        OdsayClient client = new OdsayClient(
                builder.build(),
                "test%2Bapi%2Fkey"
        );

        mockServer.expect(requestTo(
                        "https://api.odsay.com/v1/api/searchPubTransPathT"
                                + "?SX=126.978&SY=37.5665"
                                + "&EX=126.977&EY=37.5796"
                                + "&OPT=0&SearchType=0&SearchPathType=0"
                                + "&apiKey=test%2Bapi%2Fkey"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "result": {
                            "path": [{
                              "pathType": 1,
                              "info": {
                                "totalTime": 12,
                                "totalDistance": 1200
                              }
                            }]
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        TransitRouteResult result = client.calculateRoute(
                new RouteCoordinate(126.9780, 37.5665),
                new RouteCoordinate(126.9770, 37.5796)
        );

        assertEquals(1.2, result.distanceKm(), 0.000001);
        mockServer.verify();
    }

    @Test
    @DisplayName("인증 오류 한 번이 나도 다음 구간은 로컬 차단 없이 다시 호출한다")
    void calculateRouteRetriesLaterPairsAfterAuthenticationFailure() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.odsay.com/v1/api");
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(builder).build();
        OdsayClient client = new OdsayClient(builder.build(), "test-key");

        mockServer.expect(requestTo(
                        "https://api.odsay.com/v1/api/searchPubTransPathT"
                                + "?SX=126.978&SY=37.5665"
                                + "&EX=126.977&EY=37.5796"
                                + "&OPT=0&SearchType=0&SearchPathType=0&apiKey=test-key"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "error": {
                            "code": "500",
                            "msg": "[ApiKeyAuthFailed] ApiKey authentication failed."
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));
        mockServer.expect(requestTo(
                        "https://api.odsay.com/v1/api/searchPubTransPathT"
                                + "?SX=126.977&SY=37.5796"
                                + "&EX=126.9997&EY=37.57"
                                + "&OPT=0&SearchType=0&SearchPathType=0&apiKey=test-key"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "result": {
                            "path": [
                              {
                                "pathType": 2,
                                "info": {
                                  "totalTime": 17,
                                  "totalDistance": 3200
                                }
                              }
                            ]
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OdsayApiException first = assertThrows(
                OdsayApiException.class,
                () -> client.calculateRoute(
                        new RouteCoordinate(126.9780, 37.5665),
                        new RouteCoordinate(126.9770, 37.5796)
                )
        );
        TransitRouteResult second = client.calculateRoute(
                new RouteCoordinate(126.9770, 37.5796),
                new RouteCoordinate(126.9997, 37.5700)
        );

        assertEquals("500", first.getErrorCode());
        assertTrue(first.getApiMessage().contains("ApiKeyAuthFailed"));
        assertEquals(3.2, second.distanceKm());
        assertEquals(17.0, second.travelTimeMinutes());
        assertTrue(client.canAttemptRequest());
        mockServer.verify();
    }

    @Test
    @DisplayName("ODsay 논리 오류는 오류 코드를 보존한 예외로 변환한다")
    void calculateRoutePreservesOdsayErrorCode() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.odsay.com/v1/api");
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(builder).build();
        OdsayClient client = new OdsayClient(builder.build(), "test-key");

        mockServer.expect(requestTo(
                        "https://api.odsay.com/v1/api/searchPubTransPathT"
                                + "?SX=126.978&SY=37.5665"
                                + "&EX=126.977&EY=37.5796"
                                + "&OPT=0&SearchType=0&SearchPathType=0&apiKey=test-key"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "error": [
                            {"code": "-98", "message": "출, 도착지가 700m이내입니다."}
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OdsayApiException exception = assertThrows(
                OdsayApiException.class,
                () -> client.calculateRoute(
                        new RouteCoordinate(126.9780, 37.5665),
                        new RouteCoordinate(126.9770, 37.5796)
                )
        );

        assertEquals("-98", exception.getErrorCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("ODsay 단일 오류 객체의 msg 필드도 오류 코드와 메시지로 변환한다")
    void calculateRouteParsesSingleErrorObjectWithMsg() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.odsay.com/v1/api");
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(builder).build();
        OdsayClient client = new OdsayClient(builder.build(), "test-key");

        mockServer.expect(requestTo(
                        "https://api.odsay.com/v1/api/searchPubTransPathT"
                                + "?SX=126.978&SY=37.5665"
                                + "&EX=126.977&EY=37.5796"
                                + "&OPT=0&SearchType=0&SearchPathType=0&apiKey=test-key"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "error": {
                            "code": "-98",
                            "msg": "출, 도착지가 700m이내입니다."
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OdsayApiException exception = assertThrows(
                OdsayApiException.class,
                () -> client.calculateRoute(
                        new RouteCoordinate(126.9780, 37.5665),
                        new RouteCoordinate(126.9770, 37.5796)
                )
        );

        assertEquals("-98", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("700m"));
        mockServer.verify();
    }

    @Test
    @DisplayName("서버 일일 예산에 도달하면 다음 ODsay 요청을 보내지 않는다")
    void calculateRouteStopsBeforeExceedingLocalDailyBudget() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.odsay.com/v1/api");
        MockRestServiceServer mockServer =
                MockRestServiceServer.bindTo(builder).build();
        OdsayClient client = new OdsayClient(
                builder.build(),
                "test-key",
                1
        );

        mockServer.expect(requestTo(
                        "https://api.odsay.com/v1/api/searchPubTransPathT"
                                + "?SX=126.978&SY=37.5665"
                                + "&EX=126.977&EY=37.5796"
                                + "&OPT=0&SearchType=0&SearchPathType=0&apiKey=test-key"
                ))
                .andRespond(withSuccess(
                        """
                        {
                          "result": {
                            "path": [{
                              "pathType": 1,
                              "info": {
                                "totalTime": 12,
                                "totalDistance": 1200
                              }
                            }]
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        client.calculateRoute(
                new RouteCoordinate(126.9780, 37.5665),
                new RouteCoordinate(126.9770, 37.5796)
        );
        OdsayApiException exception = assertThrows(
                OdsayApiException.class,
                () -> client.calculateRoute(
                        new RouteCoordinate(126.9770, 37.5796),
                        new RouteCoordinate(126.9997, 37.5700)
                )
        );

        assertEquals("LOCAL_DAILY_LIMIT", exception.getErrorCode());
        mockServer.verify();
    }

    @Test
    @DisplayName("API 키가 없으면 ODsay 호출 전에 예외가 발생한다")
    void calculateRouteRejectsMissingApiKey() {
        OdsayClient client = new OdsayClient(
                RestClient.create("https://api.odsay.com/v1/api"),
                ""
        );

        assertFalse(client.isConfigured());
        assertThrows(
                IllegalStateException.class,
                () -> client.calculateRoute(
                        new RouteCoordinate(126.9780, 37.5665),
                        new RouteCoordinate(126.9770, 37.5796)
                )
        );
    }
}
