package com.example.maple.domain;

import lombok.Getter;

public enum JobStat {

    WARRIOR(
            new String[]{"히어로", "팔라딘", "다크나이트", "데몬슬레이어", "아란", "카이저","제로","소울마스터","아델","미하일","블래스터",
                    "렌","스트라이커","은월","아크","바이퍼","캐논슈터"},
            "STR", "DEX",
            AtkType.ATTACK
    ),
    MAGE(
            new String[]{"배틀메이지", "비숍", "아크메이지(불,독)", "아크메이지(썬,콜)", "루미너스", "일리움","라라","에반","플레임위자드","키네시스"},
            "INT", "LUK",
            AtkType.MAGIC
    ),
    THIEF(
            new String[]{"나이트로드", "섀도어", "듀얼블레이드", "팬텀","호영","칼리","나이트워커"},
            "LUK", "DEX",
            AtkType.ATTACK
    ),
    ARCHER(
            new String[]{"보우마스터", "신궁", "패스파인더", "윈드브레이커","카인","와일드헌터","메르세데스"},
            "DEX", "STR",
            AtkType.ATTACK
    ),
    PIRATE(
            new String[]{ "캡틴", "엔젤릭버스터","메카닉"},
            "DEX", "STR",
            AtkType.ATTACK
    );

    public enum AtkType { ATTACK, MAGIC }

    private final String[] jobNames;

    @Getter
    private final String mainStat;
    @Getter
    private final String subStat;

    @Getter
    private final AtkType atkType;

    JobStat(String[] jobNames, String mainStat, String subStat, AtkType atkType) {
        this.jobNames = jobNames;
        this.mainStat = mainStat;
        this.subStat = subStat;
        this.atkType = atkType;
    }

    public boolean usesMagic() {
        return atkType == AtkType.MAGIC;
    }

    public static JobStat from(String characterClass) {
        for (JobStat js : values()) {
            for (String name : js.jobNames) {
                if (characterClass != null && characterClass.contains(name)) {
                    return js;
                }
            }
        }
        throw new IllegalArgumentException("알 수 없는 직업: " + characterClass);
    }
}
