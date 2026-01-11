package com.example.maple.dto.stat;

public record DetailStat(
        String name,
        String characterClass,

        int str,
        int dex,
        int intel,
        int luk,

        int mainStat,
        int subStat,

        double criticalDamage, // 크리티컬 데미지 (%)
        double bossDamage,     // 보스 공격력 (%)
        double damage,         // 데미지 (%)
        double finalDamage,    // 최종 데미지 (% or multiplier로 변환 가능)
        double ignoreDefense   // 방어율 무시 (%)
) {}
