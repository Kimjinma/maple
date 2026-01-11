package com.example.maple.dto.stat;

public record DetailStatResponse(
        String characterName,
        String characterClass,

        double criticalDamage,   // 크리티컬 데미지
        double bossDamage,       // 보스 몬스터 데미지
        double damage,           // 데미지
        double finalDamage,      // 최종 데미지
        double ignoreDefense,    // 방어율 무시
        int mainStat,
        int subStat,
        int cooldownReduction,   // 재사용 대기시간 감소(초)
        int attackPower,         // 공격력
        int magicPower           // 마력
) {}
