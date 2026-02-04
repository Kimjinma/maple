package com.example.maple.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "upgrade_node")
@Getter
@Setter
@NoArgsConstructor
public class UpgradeNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String slot;

    @Column(name = "seat")
    private Integer seat;

    @Column(name = "exclusive_group", length = 50)
    private String exclusiveGroup;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "starforce")
    private Integer starforce;

    @Column(name = "set_key", length = 50)
    private String setKey;

    // ===== 환산 영향 스펙(목표치) =====
    @Column(name = "t_main_stat_pct", nullable = false)
    private double tMainStatPct;

    @Column(name = "t_all_stat_pct", nullable = false)
    private double tAllStatPct;

    @Column(name = "t_atk_pct", nullable = false)
    private double tAtkPct;

    @Column(name = "t_magic_pct", nullable = false)
    private double tMagicPct;

    @Column(name = "t_main_stat_flat", nullable = false)
    private int tMainStatFlat;

    @Column(name = "t_atk_flat", nullable = false)
    private int tAtkFlat;

    @Column(name = "t_magic_flat", nullable = false)
    private int tMagicFlat;

    @Column(name = "t_boss_pct", nullable = false)
    private double tBossPct;

    @Column(name = "t_dmg_pct", nullable = false)
    private double tDmgPct;

    @Column(name = "t_ied_pct", nullable = false)
    private double tIedPct;

    @Column(name = "t_cdmg_pct", nullable = false)
    private double tCdmgPct;

    @Column(name = "t_cooldown_sec", nullable = false)
    private int tCooldownSec;

    @Column(name = "cost_meso", nullable = false)
    private long costMeso;

    @Column(name = "potential_rank", length = 20)
    private String potentialRank; // "L", "U", "E", "R"

    @Column(name = "additional_rank", length = 20)
    private String additionalRank;

    @Column(name = "ui_potential_main_stats", length = 20)
    private String uiPotentialMainStats;

    @Column(name = "ui_additional_atk")
    private Integer uiAdditionalAtk;

    @Column(name = "ui_additional_main_stat_pct")
    private Integer uiAdditionalMainStatPct;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
