package com.example.maple.dto.eff;

public record EfficiencyResponse(
        String characterName,
        String characterClass,
        int baseR,
        int bossDef,
        double boss1,
        double atk1,
        double stat1,
        double atk10,
        double fd1,
        double ied1,
        double cdmg1,
        double all1,
        double cooldown1,
        double sub10,
        double stat10
) {}


