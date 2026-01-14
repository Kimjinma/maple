package com.example.maple.dto.item;

public record ItemOptionSummaryResponse(
        String characterName,
        String characterClass,

        // 직업 기준 자동 합산
        double mainStatPct,
        double allStatPct
) {}
