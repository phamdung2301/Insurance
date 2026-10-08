package com.dungphd.insuranceass.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        text = text.trim();
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            try {
                LocalDate ld = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception ex) {
                throw new IOException("Cannot parse date/instant: " + text, ex);
            }
        }
    }
}
