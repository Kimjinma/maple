package com.example.maple.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "set_effect_preset")
@Getter
@NoArgsConstructor
public class SetEffectPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // 예: "3카 5앱", "3카 5아케인"

    // 합산된 세트효과 수치 (사용자가 DB에 입력할 값)

    private int attackPower;
    private int magicPower;

    private int mainStat; // 편의상 주스탯 (또는 올스탯과 분리)
    private int allStat;

    private int bossDamage; // %
    private int ignoreDefense; // % (Note: 여러 줄일 경우 자체적으로 합산된 수치 or 단일 줄. 로직 단순화를 위해 단일값으로 관리 권장)
    private int criticalDamage; // %
    private int damage; // %

    public SetEffectPreset(String name, int attackPower, int magicPower, int mainStat, int allStat, int bossDamage,
            int ignoreDefense, int criticalDamage, int damage) {
        this.name = name;
        this.attackPower = attackPower;
        this.magicPower = magicPower;
        this.mainStat = mainStat;
        this.allStat = allStat;
        this.bossDamage = bossDamage;
        this.ignoreDefense = ignoreDefense;
        this.criticalDamage = criticalDamage;
        this.damage = damage;
    }
}
