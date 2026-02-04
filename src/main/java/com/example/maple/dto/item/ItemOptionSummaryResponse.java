package com.example.maple.dto.item;

import java.util.Map;

public record ItemOptionSummaryResponse(
                String characterName,
                String characterClass,

                // 직업 기준 자동 합산 (전체)
                double mainStatPct,
                double allStatPct,
                int attackPct,
                int magicPct,

                // 슬롯별 요약 정보 (부위별 교체 계산용)
                Map<String, SlotOption> slotOptions) {
        public SlotOption getOptionBySlot(String slot) {
                if (slotOptions == null)
                        return null;
                return slotOptions.get(slot);
        }

        public record SlotOption(
                        String itemName,
                        double mainStatPct,
                        int mainStat,
                        int attackPower,
                        int magicPower,
                        int attackPct,
                        int magicPct,
                        double bossDamage,
                        double damage,
                        double ignoreDefense,
                        double criticalDamage) {
        }
}
