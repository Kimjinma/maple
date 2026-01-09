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

        CharacterBasicResponse basic = nexonApiClient.getBasic(ocidResponse.ocid());

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

        CharacterStatResponse stat = nexonApiClient.getStat(ocidResponse.ocid(), date);
        if (stat == null || stat.finalStat() == null) {
            throw new IllegalArgumentException("스탯 조회 실패: " + name);
        }

        Map<String, String> m = stat.finalStat().stream()
                .filter(Objects::nonNull)
                .filter(s -> s.statName() != null && !s.statName().isBlank())
                .filter(s -> s.statValue() != null) // value null 방지
                .collect(Collectors.toMap(
                        s -> s.statName().trim(),
                        CharacterStatResponse.FinalStat::statValue,
                        (a, b) -> b
                ));
        String criticalDamage = require(m, "크리티컬 데미지");
        String bossDamage = require(m, "보스 몬스터 데미지");
        String damage = require(m, "데미지");
        String Finaldamage = require(m, "최종 데미지");
        String ignoredefense = require(m, "방어율 무시");
        String Cooldownreduction = require(m, "재사용 대기시간 감소 (초)");
        String attackpower = require(m, "공격력");
        String horsepower = require(m, "마력");

        String job = stat.characterClass();
        JobStat jobStat = JobStat.from(job);
        String mainStat = m.get(jobStat.getMainStat());
        String subStat = m.get(jobStat.getSubStat());

        return new DetailStatResponse(
                name,
                stat.characterClass(),
                criticalDamage,
                bossDamage,
                damage,
                Finaldamage,
                ignoredefense,
                mainStat,
                subStat,
                Cooldownreduction,
                attackpower,
                horsepower
        );

    }

    private String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) throw new IllegalArgumentException("스탯 키 없음: " + key);
        return v;
    }

}