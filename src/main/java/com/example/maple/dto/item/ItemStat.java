package com.example.maple.dto.item;

import com.example.maple.dto.stat.CharacterStatResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ItemStat(
                @JsonProperty("item_equipment") List<ItemEquipment> itemequipment

) {
        public record ItemEquipment(

                        String item_equipment_part,
                        String item_equipment_slot,
                        String item_name,
                        String potential_option_1,
                        String potential_option_2,
                        String potential_option_3,
                        String additional_potential_option_1,
                        String additional_potential_option_2,
                        String additional_potential_option_3,
                        Integer special_ring_level,
                        ItemTotalOption item_total_option

        ) {
                public record ItemTotalOption(
                                String str,
                                String dex,
                                @JsonProperty("int") String intel,
                                String luk,
                                String attack_power,
                                String magic_power,
                                String boss_damage,
                                String damage,
                                String ignore_monster_armor,
                                String all_stat) {
                }
        }
}
