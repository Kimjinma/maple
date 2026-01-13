package com.example.maple.dto.item;

public record ItemOptionSummaryResponse(
        String characterName,
        String characterClass,

        // 아이템 total_option 합 (정수)
        int strFlat,
        int dexFlat,
        int intFlat,
        int lukFlat,
        int attackFlat,
        int magicFlat,

        // 잠재/에디에서 나온 % 합
        double strPct,
        double dexPct,
        double intPct,
        double lukPct,
        double allStatPct,
        double attackPct,
        double magicPct,

        // 잠재/에디에서 나온 기타 옵션(있으면)
        double bossDamagePct,
        double damagePct,
        double critDamagePct,
        double ignoreDefensePct,
        double finalDamagePct,

        // 쿨감(초)
        int cooldownReductionSec
) {}
