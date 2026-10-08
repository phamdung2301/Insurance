package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Location {
    private Integer locationId;
    private String address;
    @Builder.Default
    private List<Coverage> coverages = new ArrayList<>();
}
