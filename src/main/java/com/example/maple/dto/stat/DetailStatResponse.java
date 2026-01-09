package com.example.maple.dto.stat;

public record DetailStatResponse(
        String characterName,
        String characterClass,
        String criticalDamage,  // 크리티컬 데미지
        String bossDamage,      // 보스 공격력
        String damage,           // 데미지
        String Finaldamage, //최종데미지
        String ignoredefense, //방무
        String mainStat,
        String subStat,
        String Cooldownreduction,//쿨감
        String attackpower, //공격력
        String horsepower //마력






) {}