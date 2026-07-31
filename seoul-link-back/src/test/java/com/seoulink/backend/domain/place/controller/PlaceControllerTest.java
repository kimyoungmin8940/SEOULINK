package com.seoulink.backend.domain.place.controller;

import com.seoulink.backend.domain.place.service.PlaceRecommendationService;
import com.seoulink.backend.domain.place.service.PlaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PlaceControllerTest {

    private MockMvc mockMvc;
    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        placeService = mock(PlaceService.class);
        when(placeService.getPlacesByNames(
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of());

        mockMvc = standaloneSetup(new PlaceController(
                placeService,
                mock(PlaceRecommendationService.class)
        )).build();
    }

    @Test
    @DisplayName("POST 본문의 쉼표 포함 장소명을 하나의 이름으로 전달한다")
    void getPlacesByNamesFromRequestBody() throws Exception {
        mockMvc.perform(post("/api/places/by-names")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "names": [
                                    "서울공예박물관",
                                    "꽃,밥에피다 인사동점"
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> namesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(placeService).getPlacesByNames(namesCaptor.capture());
        assertEquals(
                List.of("서울공예박물관", "꽃,밥에피다 인사동점"),
                namesCaptor.getValue()
        );
    }

    @Test
    @DisplayName("기존 GET 장소명 조회 엔드포인트를 유지한다")
    void keepGetPlacesByNamesEndpoint() throws Exception {
        mockMvc.perform(get("/api/places/by-names")
                        .param("names", "서울공예박물관"))
                .andExpect(status().isOk());

        verify(placeService).getPlacesByNames(
                List.of("서울공예박물관")
        );
    }
}
