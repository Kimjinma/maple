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
                        (a, b) -> b));

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
        int subStat = toInt(m, jobStat.getSubStat());
        int combatPower = toInt(m, "전투력");

        return new DetailStatResponse(
                name,
                stat.characterClass(),
                criticalDamage + DopingConstants.CRIT_DMG_PCT,
                bossDamage + DopingConstants.BOSS_PCT,
                damage + DopingConstants.DAMAGE_PCT,
                finalDamage,
                ignoreDefense,
                mainStat,
                subStat,
                cooldownReduction,
                attackPower + DopingConstants.ATK_FLAT,
                magicPower + DopingConstants.ATK_FLAT,
                combatPower);
    }

    public ItemOptionSummaryResponse getItemOptionSummary(String name, String characterClass) {

        String ocid = cached.getOcid(name);
        ItemStat itemStat = cached.getItem(ocid);
        JobStat jobStat = JobStat.from(characterClass);

        double allStatPct = 0.0;
        double mainStatPct = 0.0;
        int attackPct = 0;
        int magicPct = 0;

        java.util.Map<String, ItemOptionSummaryResponse.SlotOption> slotOptions = new java.util.HashMap<>();

        for (ItemStat.ItemEquipment eq : itemStat.itemequipment()) {
            if (eq == null)
                continue;

            double itemAllPct = 0;
            if (eq.item_total_option() != null) {
                itemAllPct = toDoubleSafe(eq.item_total_option().all_stat());
                allStatPct += itemAllPct;
            }

            String[] lines = {
                    eq.potential_option_1(),
                    eq.potential_option_2(),
                    eq.potential_option_3(),
                    eq.additional_potential_option_1(),
                    eq.additional_potential_option_2(),
                    eq.additional_potential_option_3()
            };

            double itemMainPct = 0;
            int itemAtkPct = 0;
            int itemMagPct = 0;

            for (String line : lines) {
                double a = pickPct(line, "올스탯");
                double m = pickPct(line, jobStat.getMainStat());
                int ap = pickPctInt(line, "공격력");
                int mp = pickPctInt(line, "마력");

                itemAllPct += a;
                itemMainPct += m;
                itemAtkPct += ap;
                itemMagPct += mp;

                allStatPct += a;
                mainStatPct += m;
                attackPct += ap;
                magicPct += mp;
            }

            // 개별 슬롯 정보 저장
            int itemMainFlat = 0;
            int itemAtkFlat = 0;
            int itemMagFlat = 0;
            double itemBoss = 0;
            double itemDmg = 0;
            double itemIed = 0;

            if (eq.item_total_option() != null) {
                itemMainFlat = toIntFromStr(jobStat.getMainStat(), eq.item_total_option());
                itemAtkFlat = toIntSafe(eq.item_total_option().attack_power());
                itemMagFlat = toIntSafe(eq.item_total_option().magic_power());
                itemBoss = toDoubleSafe(eq.item_total_option().boss_damage());
                itemDmg = toDoubleSafe(eq.item_total_option().damage());
                itemIed = toDoubleSafe(eq.item_total_option().ignore_monster_armor());
            }

            // 시드링(특수반지) 교체 방지 로직
            boolean isSeedRing = (eq.special_ring_level() != null && eq.special_ring_level() > 0)
                    || (eq.item_name() != null && (eq.item_name().contains("리스트레인트") || eq.item_name().contains("웨폰퍼프")
                            || eq.item_name().contains("오라웨폰") || eq.item_name().contains("크라이시스")
                            || eq.item_name().contains("컨티뉴어스")
                            || eq.item_name().contains("링마스터")));

            slotOptions.put(eq.item_equipment_slot(), new ItemOptionSummaryResponse.SlotOption(
                    eq.item_name(),
                    itemMainPct + itemAllPct,
                    itemMainFlat,
                    itemAtkFlat,
                    itemMagFlat,
                    itemAtkPct,
                    itemMagPct,
                    itemBoss,
                    itemDmg,
                    itemIed,
                    0.0, 
                    isSeedRing));
        }

        return new ItemOptionSummaryResponse(
                name,
                characterClass,
                mainStatPct,
                allStatPct,
                (int) (attackPct + DopingConstants.ATK_PCT),
                (int) (magicPct + DopingConstants.ATK_PCT),
                slotOptions);
    }

    private int toIntFromStr(String statName, ItemStat.ItemEquipment.ItemTotalOption opt) {
        return switch (statName) {
            case "STR" -> toIntSafe(opt.str());
            case "DEX" -> toIntSafe(opt.dex());
            case "INT" -> toIntSafe(opt.intel());
            case "LUK" -> toIntSafe(opt.luk());
            default -> 0;
        };
    }

    private int toIntSafe(String s) {
        if (s == null || s.isBlank())
            return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public CharacterCalcInput getCalcInput(String name) {

        DetailStatResponse stat = getDetailStatByName(name);
        ItemOptionSummaryResponse item = getItemOptionSummary(name, stat.characterClass());

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
                item.magicPct());
    }

    public EfficiencyResponse getEfficiency(String name, int baseR, int bossDef) {

        // stat 1회 + item 1회 (둘 다 캐시)
        DetailStatResponse detail = getDetailStatByName(name);
        ItemOptionSummaryResponse item = getItemOptionSummary(name, detail.characterClass());

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
                item.magicPct());

        boolean isMage = JobStat.from(calc.characterClass()).usesMagic();

        EfficiencyCalculatorV3 calculator = new EfficiencyCalculatorV3(bossDef);
        return calculator.calc(calc, detail, baseR, isMage);
    }

    private double toDouble(Map<String, String> m, String key) {
        return Double.parseDouble(m.getOrDefault(key, "0"));
    }

    private int toInt(Map<String, String> m, String key) {
        return (int) Math.round(Double.parseDouble(m.getOrDefault(key, "0")));
    }

    private double pickPct(String line, String key) {
        if (line == null || key == null)
            return 0.0;
        String t = line.replace(" ", "");
        if (!t.contains(key) || !t.contains("%"))
            return 0.0;
        return extractPercent(t);
    }

    private double extractPercent(String s) {
        String num = s.replaceAll("[^0-9.+-]", "");
        return num.isBlank() ? 0.0 : Double.parseDouble(num);
    }

    private int pickPctInt(String line, String key) {
        if (line == null || key == null)
            return 0;
        String t = line.replace(" ", "");
        if (!t.contains(key) || !t.contains("%"))
            return 0;
        String num = t.replaceAll("[^0-9+-]", "");
        return num.isBlank() ? 0 : Integer.parseInt(num);
    }

    private double toDoubleSafe(String s) {
        if (s == null || s.isBlank())
            return 0.0;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
