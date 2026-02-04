package com.example.maple.dto.seteffect;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SetEffectResponse(
        @JsonProperty("set_effect") List<SetEffect> setEffect) {
    public record SetEffect(
            @JsonProperty("set_name") String setName,
            @JsonProperty("total_set_count") int totalSetCount,
            @JsonProperty("set_effect_info") List<SetEffectInfo> setEffectInfo) {
    }

    public record SetEffectInfo(
            @JsonProperty("set_count") int setCount,
            @JsonProperty("set_option") String setOption) {
    }
}
