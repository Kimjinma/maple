package com.example.maple.service;

import com.example.maple.domain.JobStat;
import com.example.maple.dto.CharacterBasicResponse;
import com.example.maple.dto.calcinput.CharacterCalcInput;
import com.example.maple.dto.eff.EfficiencyResponse;
import com.example.maple.dto.item.ItemOptionSummaryResponse;
import com.example.maple.dto.item.ItemStat;
import com.example.maple.dto.stat.CharacterStatResponse;
import com.example.maple.dto.stat.DetailStatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CharacterService {

    private final NexonCachedService cached;

    public CharacterService(NexonCachedService cached) {
        this.cached = cached;
    }


    public CharacterBasicResponse getBasicByName(String name) {
        String ocid = cached.getOcid(name);
        return cached.getBasic(ocid);
    }


    public ItemStat getItemStat(String name) {
        String ocid = cached.getOcid(name);
        return cached.getItem(ocid);
    }


    public DetailStatResponse getDetailStatByName(String name) {

        String ocid = cached.getOcid(name);
        CharacterStatResponse stat = cached.getStat(ocid);

        Map<String, String> m = stat.finalStat().stream()
                .filter(Objects::nonNull)
                .filter(s -> s.statName() != null && !s.statName().isBlank())
                .collect(Collectors.toMap(
                        s -> s.statName().trim(),
                        s -> s.statValue() == null ? "0" : s.statValue().trim(),
                        (a, b) -> b
                ));

        double criticalDamage = toDouble(m, "크리티컬 데미지");
        double bossDamage = toDouble(m, "보스 몬스터 데미지");
        double damage = toDouble(m, "데미지");
        double finalDamage = toDouble(m, "최종 데미지");
        double ignoreDefense = toDouble(m, "방어율 무시");

        int cooldownReduction = toInt(m, "재사용 대기시간 감소 (초)");
        int attackPower = toInt(m, "공격력");
        int magicPower = toInt(m, "마력");

        JobStat jobStat = JobStat.from(stat.characterClass());
        int mainStat = toInt(m, jobStat.getMainStat());
        int subStat  = toInt(m, jobStat.getSubStat());


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


    public ItemOptionSummaryResponse getItemOptionSummary(String name, String characterClass) {

        String ocid = cached.getOcid(name);
        ItemStat itemStat = cached.getItem(ocid);
        JobStat jobStat = JobStat.from(characterClass);

        double allStatPct = 0.0;
        double mainStatPct = 0.0;
        int attackPct = 0;
        int magicPct = 0;

        for (ItemStat.ItemEquipment eq : itemStat.itemequipment()) {
            if (eq == null) continue;

            if (eq.item_total_option() != null) {
                allStatPct += toDoubleSafe(eq.item_total_option().all_stat());
            }

            String[] lines = {
                    eq.potential_option_1(),
                    eq.potential_option_2(),
                    eq.potential_option_3(),
                    eq.additional_potential_option_1(),
                    eq.additional_potential_option_2(),
                    eq.additional_potential_option_3()
            };

            for (String line : lines) {
                allStatPct += pickPct(line, "올스탯");
                mainStatPct += pickPct(line, jobStat.getMainStat());
                attackPct += pickPctInt(line, "공격력");
                magicPct += pickPctInt(line, "마력");
            }
        }

        return new ItemOptionSummaryResponse(
                name,
                characterClass,
                mainStatPct + allStatPct,
                allStatPct,
                attackPct,
                magicPct
        );
    }


    public CharacterCalcInput getCalcInput(String name) {

        DetailStatResponse stat = getDetailStatByName(name);
        ItemOptionSummaryResponse item =
                getItemOptionSummary(name, stat.characterClass());

        return new CharacterCalcInput(
                stat.characterName(),
                stat.characterClass(),
                stat.finalDamage(),
                stat.bossDamage(),
                stat.damage(),
                stat.ignoreDefense(),
                stat.criticalDamage(),
                stat.cooldownReduction(),
                item.mainStatPct(),
                item.allStatPct(),
                item.attackPct(),
                item.magicPct()
        );
    }


    public EfficiencyResponse getEfficiency(String name, int baseR, int bossDef) {

        // stat 1회 + item 1회 (둘 다 캐시)
        DetailStatResponse detail = getDetailStatByName(name);
        ItemOptionSummaryResponse item =
                getItemOptionSummary(name, detail.characterClass());

        CharacterCalcInput calc = new CharacterCalcInput(
                detail.characterName(),
                detail.characterClass(),
                detail.finalDamage(),
                detail.bossDamage(),
                detail.damage(),
                detail.ignoreDefense(),
                detail.criticalDamage(),
                detail.cooldownReduction(),
                item.mainStatPct(),
                item.allStatPct(),
                item.attackPct(),
                item.magicPct()
        );

        boolean isMage = JobStat.from(calc.characterClass()) == JobStat.MAGE;

        EfficiencyCalculator calculator = new EfficiencyCalculator(bossDef);
        return calculator.calc(calc, detail, baseR, isMage);
    }


    private double toDouble(Map<String, String> m, String key) {
        return Double.parseDouble(m.getOrDefault(key, "0"));
    }

    private int toInt(Map<String, String> m, String key) {
        return (int) Math.round(Double.parseDouble(m.getOrDefault(key, "0")));
    }

    private double pickPct(String line, String key) {
        if (line == null || key == null) return 0.0;
        String t = line.replace(" ", "");
        if (!t.contains(key) || !t.contains("%")) return 0.0;
        return extractPercent(t);
    }

    private double extractPercent(String s) {
        String num = s.replaceAll("[^0-9.+-]", "");
        return num.isBlank() ? 0.0 : Double.parseDouble(num);
    }

    private int pickPctInt(String line, String key) {
        if (line == null || key == null) return 0;
        String t = line.replace(" ", "");
        if (!t.contains(key) || !t.contains("%")) return 0;
        String num = t.replaceAll("[^0-9+-]", "");
        return num.isBlank() ? 0 : Integer.parseInt(num);
    }

    private double toDoubleSafe(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return 0.0; }
    }
}
