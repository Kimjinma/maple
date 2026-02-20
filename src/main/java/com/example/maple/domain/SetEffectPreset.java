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


    private int attackPower;
    private int magicPower;

    private int mainStat;
    private int allStat;

    private int bossDamage; // %
    private int ignoreDefense;
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
