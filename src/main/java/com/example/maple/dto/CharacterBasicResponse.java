package com.example.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CharacterBasicResponse(
    @JsonProperty("character_name") String characterName,
    @JsonProperty("world_name") String worldName,
    @JsonProperty("character_class") String characterClass,
    @JsonProperty("character_level") Integer characterLevel


) {

}
