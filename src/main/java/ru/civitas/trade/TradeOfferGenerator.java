package ru.civitas.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.civitas.data.EconomyHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class TradeOfferGenerator {
    private record Group(int min, int max, Item[] items) { }
    private static final Group[] GROUPS = {
        new Group(16, 48, new Item[]{Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG}),
        new Group(16, 48, new Item[]{Items.COBBLESTONE, Items.STONE, Items.ANDESITE, Items.DIORITE, Items.GRANITE}),
        new Group(8, 24, new Item[]{Items.BREAD, Items.WHEAT, Items.POTATO, Items.CARROT, Items.BEETROOT}),
        new Group(4, 12, new Item[]{Items.IRON_INGOT, Items.GOLD_INGOT, Items.COAL}),
        new Group(4, 12, new Item[]{Items.STICK, Items.PAPER, Items.LEATHER, Items.STRING})
    };
    private TradeOfferGenerator() { }

    public static List<TradeOffer> generate(UUID partnerId, long day, TradePersonality personality, EconomyHistory history) {
        long seed = partnerId.getMostSignificantBits() ^ Long.rotateLeft(partnerId.getLeastSignificantBits(), 17) ^ day;
        Random random = new Random(seed);
        int wood = history == null ? 0 : history.deltaWood(7);
        int stone = history == null ? 0 : history.deltaStone(7);
        int food = history == null ? 0 : history.deltaFood(7);
        int surplusIndex = wood >= stone && wood >= food ? 0 : stone >= food ? 1 : 2;
        int deficitIndex = food <= wood && food <= stone ? 2 : stone <= wood ? 1 : 0;
        List<TradeOffer> result = new ArrayList<>();
        int attempts = 0;
        while (result.size() < personality.offerCount && attempts++ < personality.offerCount * 8) {
            int wantIndex = random.nextInt(4) == 0 ? random.nextInt(GROUPS.length) : surplusIndex;
            int offerIndex = random.nextInt(4) == 0 ? random.nextInt(GROUPS.length) : deficitIndex;
            if (wantIndex == offerIndex) continue;
            Group wantGroup = GROUPS[wantIndex], offerGroup = GROUPS[offerIndex];
            int wantCount = scaled(wantGroup, personality.wantMultiplier, random);
            int offerCount = scaled(offerGroup, personality.offerMultiplier, random);
            result.add(new TradeOffer(new ItemStack(pick(wantGroup, random), wantCount),
                    new ItemStack(pick(offerGroup, random), offerCount), 2 + random.nextInt(3)));
        }
        return List.copyOf(result);
    }

    private static int scaled(Group group, double multiplier, Random random) {
        return Math.max(1, Math.min(64, (int)Math.round((group.min + random.nextInt(group.max - group.min + 1)) * multiplier)));
    }
    private static Item pick(Group group, Random random) { return group.items[random.nextInt(group.items.length)]; }
}