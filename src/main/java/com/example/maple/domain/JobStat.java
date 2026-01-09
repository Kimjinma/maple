package com.example.maple.domain;

import lombok.Getter;

public enum JobStat {

    WARRIOR(
            new String[]{"히어로", "팔라딘", "다크나이트", "데몬슬레이어", "아란", "카이저"},
            "STR", "DEX"
    ),
    MAGE(
            new String[]{"아크메이지", "비숍", "불독", "썬콜", "루미너스", "일리움"},
            "INT", "LUK"
    ),
    THIEF(
            new String[]{"나이트로드", "섀도어", "듀얼블레이드", "팬텀"},
            "LUK", "DEX"
    ),
    ARCHER(
            new String[]{"보우마스터", "신궁", "패스파인더", "윈드브레이커"},
            "DEX", "STR"
    ),
    PIRATE(
            new String[]{"바이퍼", "캡틴", "캐논슈터", "아크", "엔젤릭버스터"},
            "DEX", "STR"
    );

    private final String[] jobNames; // 직업 이름들
    @Getter
    private final String mainStat;
    @Getter
    private final String subStat;

    JobStat(String[] jobNames, String mainStat, String subStat) {
        this.jobNames = jobNames;
        this.mainStat = mainStat;
        this.subStat = subStat;
    }

    public static JobStat from(String characterClass) {
        for (JobStat js : values()) {
            for (String name : js.jobNames) {
                if (characterClass.contains(name)) {
                    return js;
                }
            }
        }
        throw new IllegalArgumentException("알 수 없는 직업: " + characterClass);
    }
}
