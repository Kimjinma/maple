package com.example.maple.dto.calcinput;

public record CharacterCalcInput(
        String characterName,
        String characterClass,

        // stat(final_stat) 값
        double finalDamagePct, // 최종 데미지
        double bossDamagePct,  // 보스 데미지
        double damagePct,      // 데미지
        double ignoreDefensePct, // 방어율 무시
        double critDamagePct,    // 크리티컬 데미지
        int cooldownNoApplySec,  // 재사용 대기시간 감소(초)

        // item 값
        double mainStatPct,   // 주스탯% + 올스탯%까지 합산된 값
        double allStatPct, //올스탯% (추가옵션 합산된 값)
        double  attackPct, // 공%
        double magicPct  // 마%
) {}