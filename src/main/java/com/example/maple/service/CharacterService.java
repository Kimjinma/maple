package com.example.maple.service;


import com.example.maple.client.NexonApiClient;
import com.example.maple.domain.JobStat;
import com.example.maple.dto.CharacterBasicResponse;
import com.example.maple.dto.item.ItemOptionSummaryResponse;
import com.example.maple.dto.item.ItemStat;
import com.example.maple.dto.ocid.OcidResponse;
import com.example.maple.dto.stat.CharacterStatResponse;
import com.example.maple.dto.stat.DetailStatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Service
public class CharacterService {

    private final NexonApiClient nexonApiClient;

    public CharacterService(NexonApiClient nexonApiClient) {
        this.nexonApiClient = nexonApiClient;
    }
    private String resolveOcid(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name이 비었습니다.");
        }
        name = name.trim();

        OcidResponse ocidResponse = nexonApiClient.getOcid(name);
        if (ocidResponse == null || ocidResponse.ocid() == null) {
            throw new IllegalArgumentException("ocid 조회 실패: " + name);
        }
        return ocidResponse.ocid();
    }
    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name이 비었습니다.");
        }
        return name.trim();
    }

    public CharacterBasicResponse getBasicByName(String name) {
        String n = normalizeName(name);
        String ocid = resolveOcid(n);

        CharacterBasicResponse basic = nexonApiClient.getBasic(ocid);
        if (basic == null) {
            throw new IllegalArgumentException("기본정보 조회 실패: " + n);
        }
        return basic;
    }


    public DetailStatResponse getDetailStatByName(String name) {
        String n = normalizeName(name);
        String ocid = resolveOcid(n);

        CharacterStatResponse stat = nexonApiClient.getStat(ocid);
        if (stat == null || stat.finalStat() == null) {
            throw new IllegalArgumentException("스탯 조회 실패: " + n);
        }





        Map<String, String> m = stat.finalStat().stream()
                .filter(Objects::nonNull)
                .filter(s -> s.statName() != null && !s.statName().isBlank())
                .collect(Collectors.toMap(
                        s -> s.statName().trim(),
                        s -> s.statValue() == null ? "0" : s.statValue().trim(),
                        (a, b) -> b
                ));

        double criticalDamage = toDouble(require(m, "크리티컬 데미지"));
        double bossDamage = toDouble(require(m, "보스 몬스터 데미지"));
        double damage = toDouble(require(m, "데미지"));
        double finalDamage = toDouble(require(m, "최종 데미지"));
        double ignoreDefense = toDouble(require(m, "방어율 무시"));

        int cooldownReduction = toInt(require(m, "재사용 대기시간 감소 (초)"));
        int attackPower = toInt(require(m, "공격력"));
        int magicPower = toInt(require(m, "마력")); // horsepower → magicPower 추천

        String job = stat.characterClass();
        JobStat jobStat = JobStat.from(job);

// jobStat.getMainStat()는 "STR"/"DEX" 같은 '키'를 주고,
// m.get(...)는 그 키의 '값(문자열)'을 줍니다. → 숫자로 변환
        int mainStat = toInt(m.getOrDefault(jobStat.getMainStat(), "0"));
        int subStat = toInt(m.getOrDefault(jobStat.getSubStat(), "0"));

        return new DetailStatResponse(
                name,
                stat.characterClass(),
                criticalDamage,
                bossDamage,
                damage,
                finalDamage,
                ignoreDefense,
                mainStat,
                subStat,
                cooldownReduction,
                attackPower,
                magicPower
        );

    }

    private String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) throw new IllegalArgumentException("스탯 키 없음: " + key);
        return v;
    }

    private int toInt(String s) {
        if (s == null || s.isBlank()) return 0;
        return (int) Math.round(Double.parseDouble(s.trim()));
    }

    private double toDouble(String s) {
        if (s == null || s.isBlank()) return 0.0;
        return Double.parseDouble(s.trim());
    }
    public ItemStat getItemStat(String name) {
        String ocid = resolveOcid(name);

        ItemStat itemStat = nexonApiClient.getItemStat(ocid);
        if (itemStat == null || itemStat.itemequipment() == null) {
            throw new IllegalArgumentException("아이템 조회 실패: " + name);
        }
        return itemStat;
    }

    public ItemOptionSummaryResponse getItemOptionSummary(String name) {
        String ocid = resolveOcid(name);

        CharacterStatResponse stat = nexonApiClient.getStat(ocid);
        if (stat == null || stat.characterClass() == null) {
            throw new IllegalArgumentException("직업 조회 실패: " + name);
        }

        String clazz = stat.characterClass();
        JobStat jobStat = JobStat.from(clazz);

        ItemStat itemStat = nexonApiClient.getItemStat(ocid);
        if (itemStat == null || itemStat.itemequipment() == null) {
            throw new IllegalArgumentException("아이템 조회 실패: " + name);
        }

        double allStatPct = 0.0;   // "올스탯 +6%"
        double mainStatPct = 0.0;  // 직업 메인스탯% (STR/DEX/INT/LUK 중 하나)

        for (ItemStat.ItemEquipment eq : itemStat.itemequipment()) {
            if (eq == null) continue;

            // 잠재
            allStatPct += pickPct(eq.potential_option_1(), "올스탯");
            allStatPct += pickPct(eq.potential_option_2(), "올스탯");
            allStatPct += pickPct(eq.potential_option_3(), "올스탯");

            mainStatPct += pickPct(eq.potential_option_1(), jobStat.getMainStat());
            mainStatPct += pickPct(eq.potential_option_2(), jobStat.getMainStat());
            mainStatPct += pickPct(eq.potential_option_3(), jobStat.getMainStat());

            // 에디
            allStatPct += pickPct(eq.additional_potential_option_1(), "올스탯");
            allStatPct += pickPct(eq.additional_potential_option_2(), "올스탯");
            allStatPct += pickPct(eq.additional_potential_option_3(), "올스탯");

            mainStatPct += pickPct(eq.additional_potential_option_1(), jobStat.getMainStat());
            mainStatPct += pickPct(eq.additional_potential_option_2(), jobStat.getMainStat());
            mainStatPct += pickPct(eq.additional_potential_option_3(), jobStat.getMainStat());
        }
        double mainTotal = mainStatPct + allStatPct;

        return new ItemOptionSummaryResponse(
                name,
                clazz,
                mainTotal,
                allStatPct

        );

    }
    private double pickPct(String line, String key) {
        if (line == null || key == null) return 0.0;

        String t = line.replace(" ", "");

        // 올스탯
        if (key.equals("올스탯")) {
            if (!t.contains("올스탯") || !t.contains("%")) return 0.0;
            return extractPercent(t);
        }

        // 주스탯 (STR/DEX/INT/LUK)
        if (!t.contains(key) || !t.contains("%")) return 0.0;
        return extractPercent(t);
    }

    private double extractPercent(String s) {
        // "STR+12%" -> "12"
        String num = s.replaceAll("[^0-9.+-]", "");
        if (num.isBlank()) return 0.0;
        return Double.parseDouble(num);
    }

}

