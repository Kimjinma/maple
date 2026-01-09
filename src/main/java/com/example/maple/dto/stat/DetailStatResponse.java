package com.example.maple.dto.stat;

public record DetailStatResponse(
        String characterName,
        String characterClass,
        String criticalDamage,  // 크리티컬 데미지
        String bossDamage,      // 보스 공격력
        String damage           // 데미지
) {}