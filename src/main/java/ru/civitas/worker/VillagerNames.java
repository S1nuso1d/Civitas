package ru.civitas.worker;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;

/** Deterministic-format, randomly generated names for Civitas residents. */
public final class VillagerNames {
    private static final String[] PREFIXES = {
            "Ал", "Бор", "Вар", "Год", "Дар", "Ел", "Жан", "Зор", "Ив", "Кир",
            "Лад", "Мир", "Нар", "Ор", "Рад", "Свя", "Тих", "Яр"
    };
    private static final String[] SUFFIXES = {
            "ек", "ан", "ир", "ол", "ус", "им", "ар", "он", "ена", "ослав",
            "ина", "ей", "ора", "ия"
    };

    private VillagerNames() { }

    public static String generate(RandomSource random) {
        return PREFIXES[random.nextInt(PREFIXES.length)]
                + SUFFIXES[random.nextInt(SUFFIXES.length)];
    }

    public static void applyName(Villager villager, RandomSource random) {
        if (villager == null || random == null || villager.getCustomName() != null) return;
        villager.setCustomName(Component.literal(generate(random)));
        villager.setCustomNameVisible(false);
    }
}
