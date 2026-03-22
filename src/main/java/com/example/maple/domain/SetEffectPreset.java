package com.example.maple.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "set_effect_preset")
@Getter
@Setter
@NoArgsConstructor
public class SetEffectPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "set_group", length = 50)
    private String setGroup;

    @Column(name = "req_count")
    private Integer reqCount;

    private int attackPower;
    private int magicPower;
    private int mainStat;
    private int allStat;
    private int bossDamage;
    private int ignoreDefense;
    private int criticalDamage;
    private int damage;
    private int maxHp;
}
