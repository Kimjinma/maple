package com.example.maple.service;


import com.example.maple.client.NexonApiClient;
import com.example.maple.domain.JobStat;
import com.example.maple.dto.CharacterBasicResponse;
import com.example.maple.dto.ocid.OcidResponse;
import com.example.maple.dto.stat.CharacterStatResponse;
import com.example.maple.dto.stat.DetailStatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.math.NumberUtils.toDouble;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

@Service
public class CharacterService {

    private final NexonApiClient nexonApiClient;

    public CharacterService(NexonApiClient nexonApiClient) {
        this.nexonApiClient = nexonApiClient;
    }

    public CharacterBasicResponse getBasicByName(String name) {
        OcidResponse ocidResponse = nexonApiClient.getOcid(name);

        if (ocidResponse == null || ocidResponse.ocid() == null) {
            throw new IllegalArgumentException("ocid 조회 실패: " + name);
        }

        CharacterBasicResponse basic = nexonApiClient. getBasic(ocidResponse.ocid());

        if (basic == null) {
            throw new IllegalArgumentException("기본정보 조회 실패: " + name);
        }

        return basic;
    }

    public DetailStatResponse getDetailStatByName(String name, String date) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name이 비었습니다.");
        }
        name = name.trim();

        OcidResponse ocidResponse = nexonApiClient.getOcid(name);
        if (ocidResponse == null || ocidResponse.ocid() == null) {
            throw new IllegalArgumentException("ocid 조회 실패: " + name);
        }

        CharacterStatResponse stat = nexonApiClient.getStat(ocidResponse.ocid());
        if (stat == null || stat.finalStat() == null) {
            throw new IllegalArgumentException("스탯 조회 실패: " + name);
        }

        Map<String, String> m = stat.finalStat().stream()
                .filter(Objects::nonNull)
                .filter(s -> s.statName() != null && !s.statName().isBlank())
                .collect(Collectors.toMap(
                        s -> s.statName().trim(),
                        s -> s.statValue() == null ? "0" : s.statValue().trim(),
                        (a, b) -> b
                ));

        double criticalDamage   = toDouble(require(m, "크리티컬 데미지"));
        double bossDamage       = toDouble(require(m, "보스 몬스터 데미지"));
        double damage           = toDouble(require(m, "데미지"));
        double finalDamage      = toDouble(require(m, "최종 데미지"));
        double ignoreDefense    = toDouble(require(m, "방어율 무시"));

        int cooldownReduction   = toInt(require(m, "재사용 대기시간 감소 (초)"));
        int attackPower         = toInt(require(m, "공격력"));
        int magicPower          = toInt(require(m, "마력")); // horsepower → magicPower 추천

        String job = stat.characterClass();
        JobStat jobStat = JobStat.from(job);

// jobStat.getMainStat()는 "STR"/"DEX" 같은 '키'를 주고,
// m.get(...)는 그 키의 '값(문자열)'을 줍니다. → 숫자로 변환
        int mainStat = toInt(m.getOrDefault(jobStat.getMainStat(), "0"));
        int subStat  = toInt(m.getOrDefault(jobStat.getSubStat(), "0"));

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



}