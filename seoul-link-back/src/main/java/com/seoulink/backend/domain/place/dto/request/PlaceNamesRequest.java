package com.seoulink.backend.domain.place.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PlaceNamesRequest {

    private List<String> names;
}
