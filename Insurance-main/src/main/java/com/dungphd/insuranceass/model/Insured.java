package com.dungphd.insuranceass.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insured {
    private String insuredId;
    private String name;
    private String type;
    private String email;
    private String phone;
    private String address;
}
