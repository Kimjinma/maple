package com.example.maple.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "upgrade_node")
public class UpgradeNode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name="delta_boss_pct", nullable=false)
    private double deltaBossPct;

    @Column(name="delta_atk_pct", nullable=false)
    private double deltaAtkPct;

    @Column(name="delta_stat_pct", nullable=false)
    private double deltaStatPct;

    @Column(name="delta_final_dmg_pct", nullable=false)
    private double deltaFinalDmgPct;

    @Column(name="delta_ied_pct", nullable=false)
    private double deltaIedPct;

    @Column(name="delta_cdmg_pct", nullable=false)
    private double deltaCdmgPct;

    @Column(name="delta_allstat_pct", nullable=false)
    private double deltaAllstatPct;

    @Column(name="delta_cooldown_sec", nullable=false)
    private int deltaCooldownSec;

    @Column(name="cost_meso", nullable=false)
    private long costMeso;

    protected UpgradeNode() {}

    public Long getId() { return id; }
    public String getName() { return name; }

    public double getDeltaBossPct() { return deltaBossPct; }
    public double getDeltaAtkPct() { return deltaAtkPct; }
    public double getDeltaStatPct() { return deltaStatPct; }
    public double getDeltaFinalDmgPct() { return deltaFinalDmgPct; }
    public double getDeltaIedPct() { return deltaIedPct; }
    public double getDeltaCdmgPct() { return deltaCdmgPct; }
    public double getDeltaAllstatPct() { return deltaAllstatPct; }
    public int getDeltaCooldownSec() { return deltaCooldownSec; }

    public long getCostMeso() { return costMeso; }
}
