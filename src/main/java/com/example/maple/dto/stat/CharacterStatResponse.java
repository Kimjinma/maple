package com.example.maple.dto.stat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CharacterStatResponse(
        String date,
        @JsonProperty("character_class") String characterClass,
        @JsonProperty("final_stat") List<FinalStat> finalStat,
        @JsonProperty("remain_ap") Integer remainAp
) {
    public record FinalStat(
            @JsonProperty("stat_name") String statName,
            @JsonProperty("stat_value") String statValue
    ) {
    }
}