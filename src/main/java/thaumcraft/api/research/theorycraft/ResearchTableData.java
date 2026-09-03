package thaumcraft.api.research.theorycraft;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;

/**
 * Replaces the old (1.12.2) {@code ResearchTableData} - the real theorycraft minigame engine behind
 * {@code TileResearchTable}, direct translation. Tracks a single in-progress "theory": how much
 * inspiration is left to spend, per-category progress ({@link #categoryTotals}, each point worth 1% of a
 * full theory), the current drawn card choices, and the aid-granted bonus card pool.
 */
public class ResearchTableData {

	public BlockEntity table;
	public String player;
	public int inspiration;
	public int inspirationStart;
	public int bonusDraws;
	public int placedCards;
	public int aidsChosen;
	public int penaltyStart;
	public ArrayList<Long> savedCards = new ArrayList<>();
	public ArrayList<String> aidCards = new ArrayList<>();
	/** Amount of progress per research category. Each point = 1% of progress towards a full theory. */
	public TreeMap<String, Integer> categoryTotals = new TreeMap<>();
	public ArrayList<String> categoriesBlocked = new ArrayList<>();
	public ArrayList<CardChoice> cardChoices = new ArrayList<>();

	public CardChoice lastDraw;

	public class CardChoice {
		public TheorycraftCard card;
		public String key;
		public boolean fromAid;
		public boolean selected;

		public CardChoice(String key, TheorycraftCard card, boolean aid, boolean selected) {
			this.key = key;
			this.card = card;
			fromAid = aid;
			this.selected = selected;
		}

		@Override
		public String toString() {
			return "key:" + key + " card:" + card.getSeed() + " fromAid:" + fromAid + " selected:" + selected;
		}
	}

	public ResearchTableData(BlockEntity tileResearchTable) {
		table = tileResearchTable;
	}

	public ResearchTableData(Player player2, BlockEntity tileResearchTable) {
		player = player2.getName().getString();
		table = tileResearchTable;
	}

	public boolean isComplete() {
		return inspiration <= 0;
	}

	public boolean hasTotal(String cat) {
		return categoryTotals.containsKey(cat);
	}

	public int getTotal(String cat) {
		return categoryTotals.containsKey(cat) ? categoryTotals.get(cat) : 0;
	}

	public void addTotal(String cat, int amt) {
		int current = categoryTotals.containsKey(cat) ? categoryTotals.get(cat) : 0;
		current += amt;
		if (current <= 0) categoryTotals.remove(cat);
		else categoryTotals.put(cat, current);
	}

	public void addInspiration(int amt) {
		inspiration += amt;
		if (inspiration > inspirationStart) inspiration = inspirationStart;
	}

	public CompoundTag serialize() {
		CompoundTag nbt = new CompoundTag();

		nbt.putString("player", player == null ? "" : player);
		nbt.putInt("inspiration", inspiration);
		nbt.putInt("inspirationStart", inspirationStart);
		nbt.putInt("placedCards", placedCards);
		nbt.putInt("bonusDraws", bonusDraws);
		nbt.putInt("aidsChosen", aidsChosen);
		nbt.putInt("penaltyStart", penaltyStart);

		ListTag savedTag = new ListTag();
		for (Long card : savedCards) {
			CompoundTag gt = new CompoundTag();
			gt.putLong("card", card);
			savedTag.add(gt);
		}
		nbt.put("savedCards", savedTag);

		ListTag categoriesBlockedTag = new ListTag();
		for (String category : categoriesBlocked) {
			CompoundTag gt = new CompoundTag();
			gt.putString("category", category);
			categoriesBlockedTag.add(gt);
		}
		nbt.put("categoriesBlocked", categoriesBlockedTag);

		ListTag categoryTotalsTag = new ListTag();
		for (String category : categoryTotals.keySet()) {
			CompoundTag gt = new CompoundTag();
			gt.putString("category", category);
			gt.putInt("total", categoryTotals.get(category));
			categoryTotalsTag.add(gt);
		}
		nbt.put("categoryTotals", categoryTotalsTag);

		ListTag aidCardsTag = new ListTag();
		for (String mc : aidCards) {
			CompoundTag gt = new CompoundTag();
			gt.putString("aidCard", mc);
			aidCardsTag.add(gt);
		}
		nbt.put("aidCards", aidCardsTag);

		ListTag cardChoicesTag = new ListTag();
		for (CardChoice mc : cardChoices) {
			cardChoicesTag.add(serializeCardChoice(mc));
		}
		nbt.put("cardChoices", cardChoicesTag);

		if (lastDraw != null) nbt.put("lastDraw", serializeCardChoice(lastDraw));

		return nbt;
	}

	public CompoundTag serializeCardChoice(CardChoice mc) {
		CompoundTag nbt = new CompoundTag();
		nbt.putString("cardChoice", mc.key);
		nbt.putBoolean("aid", mc.fromAid);
		nbt.putBoolean("select", mc.selected);
		try {
			nbt.put("cardNBT", mc.card.serialize());
		} catch (Exception e) {
		}
		return nbt;
	}

	public void deserialize(CompoundTag nbt) {
		if (nbt == null) return;
		inspiration = nbt.getInt("inspiration");
		inspirationStart = nbt.getInt("inspirationStart");
		placedCards = nbt.getInt("placedCards");
		bonusDraws = nbt.getInt("bonusDraws");
		aidsChosen = nbt.getInt("aidsChosen");
		penaltyStart = nbt.getInt("penaltyStart");
		player = nbt.getString("player");

		ListTag savedTag = nbt.getList("savedCards", Tag.TAG_COMPOUND);
		savedCards = new ArrayList<>();
		for (int x = 0; x < savedTag.size(); x++) {
			savedCards.add(savedTag.getCompound(x).getLong("card"));
		}

		ListTag categoriesBlockedTag = nbt.getList("categoriesBlocked", Tag.TAG_COMPOUND);
		categoriesBlocked = new ArrayList<>();
		for (int x = 0; x < categoriesBlockedTag.size(); x++) {
			categoriesBlocked.add(categoriesBlockedTag.getCompound(x).getString("category"));
		}

		ListTag categoryTotalsTag = nbt.getList("categoryTotals", Tag.TAG_COMPOUND);
		categoryTotals = new TreeMap<>();
		for (int x = 0; x < categoryTotalsTag.size(); x++) {
			CompoundTag nbtdata = categoryTotalsTag.getCompound(x);
			categoryTotals.put(nbtdata.getString("category"), nbtdata.getInt("total"));
		}

		ListTag aidCardsTag = nbt.getList("aidCards", Tag.TAG_COMPOUND);
		aidCards = new ArrayList<>();
		for (int x = 0; x < aidCardsTag.size(); x++) {
			aidCards.add(aidCardsTag.getCompound(x).getString("aidCard"));
		}

		ListTag cardChoicesTag = nbt.getList("cardChoices", Tag.TAG_COMPOUND);
		cardChoices = new ArrayList<>();
		for (int x = 0; x < cardChoicesTag.size(); x++) {
			CardChoice cc = deserializeCardChoice(cardChoicesTag.getCompound(x));
			if (cc != null) cardChoices.add(cc);
		}

		lastDraw = deserializeCardChoice(nbt.getCompound("lastDraw"));
	}

	public CardChoice deserializeCardChoice(CompoundTag nbt) {
		if (nbt == null || nbt.isEmpty()) return null;
		String key = nbt.getString("cardChoice");
		TheorycraftCard tc = generateCardWithNBT(key, nbt.getCompound("cardNBT"));
		if (tc == null) return null;
		return new CardChoice(key, tc, nbt.getBoolean("aid"), nbt.getBoolean("select"));
	}

	private boolean isCategoryBlocked(String cat) {
		return categoriesBlocked.contains(cat);
	}

	public void drawCards(int draw, Player pe) {
		if (draw == 3) {
			if (bonusDraws > 0) {
				bonusDraws--;
			} else {
				draw = 2;
			}
		}
		cardChoices.clear();
		player = pe.getName().getString();
		ArrayList<String> availCats = getAvailableCategories(pe);
		ArrayList<String> drawnCards = new ArrayList<>();
		boolean aidDrawn = false;
		int failsafe = 0;
		while (draw > 0 && failsafe < 10000) {
			failsafe++;
			if (!aidDrawn && !aidCards.isEmpty() && pe.getRandom().nextFloat() <= .25) {
				int idx = pe.getRandom().nextInt(aidCards.size());
				String key = aidCards.get(idx);
				TheorycraftCard card = generateCard(key, -1, pe);
				if (card == null || card.getInspirationCost() > inspiration || isCategoryBlocked(card.getResearchCategory())) continue;

				if (drawnCards.contains(key)) continue;
				drawnCards.add(key);
				cardChoices.add(new CardChoice(key, card, true, false));
				aidCards.remove(idx);
			} else {
				try {
					String[] cards = TheorycraftManager.cards.keySet().toArray(new String[] {});
					int idx = pe.getRandom().nextInt(cards.length);
					TheorycraftCard card = generateCard(cards[idx], -1, pe);
					if (card == null || card.isAidOnly() || card.getInspirationCost() > inspiration) continue;
					if (card.getResearchCategory() != null) {
						boolean found = false;
						for (String cn : availCats) {
							if (cn.equals(card.getResearchCategory())) {
								found = true;
								break;
							}
						}
						if (!found) continue;
					}

					if (drawnCards.contains(cards[idx])) continue;
					drawnCards.add(cards[idx]);
					cardChoices.add(new CardChoice(cards[idx], card, false, false));
				} catch (Exception e) {
					continue;
				}
			}
			draw--;
		}
	}

	private TheorycraftCard generateCard(String key, long seed, Player pe) {
		if (key == null) return null;
		Class<TheorycraftCard> tcc = TheorycraftManager.cards.get(key);
		if (tcc == null) return null;
		TheorycraftCard tc = null;
		try {
			tc = tcc.getDeclaredConstructor().newInstance();
			if (seed < 0) {
				if (pe != null) tc.setSeed(pe.getRandom().nextLong());
				else tc.setSeed(System.nanoTime());
			} else {
				tc.setSeed(seed);
			}
			if (pe != null && !tc.initialize(pe, this)) return null;
		} catch (Exception e) {
		}
		return tc;
	}

	private TheorycraftCard generateCardWithNBT(String key, CompoundTag nbt) {
		if (key == null) return null;
		Class<TheorycraftCard> tcc = TheorycraftManager.cards.get(key);
		if (tcc == null) return null;
		TheorycraftCard tc = null;
		try {
			tc = tcc.getDeclaredConstructor().newInstance();
			tc.deserialize(nbt);
		} catch (Exception e) {
		}
		return tc;
	}

	public void initialize(Player player1, Set<String> aids) {
		inspirationStart = getAvailableInspiration(player1);
		inspiration = inspirationStart - aids.size();

		for (String muk : aids) {
			ITheorycraftAid mu = TheorycraftManager.aids.get(muk);
			if (mu != null) {
				for (Class<TheorycraftCard> clazz : mu.getCards()) {
					aidCards.add(clazz.getName());
				}
			}
		}
	}

	public ArrayList<String> getAvailableCategories(Player player) {
		ArrayList<String> cats = new ArrayList<>();
		for (String rck : ResearchCategories.researchCategories.keySet()) {
			ResearchCategory rc = ResearchCategories.getResearchCategory(rck);
			if (rc == null || isCategoryBlocked(rck)) continue;
			if (rc.researchKey == null || ThaumcraftCapabilities.knowsResearchStrict(player, rc.researchKey)) {
				cats.add(rck);
			}
		}
		return cats;
	}

	public static int getAvailableInspiration(Player player) {
		float tot = 5;
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		for (String s : knowledge.getResearchList()) {
			if (ThaumcraftCapabilities.knowsResearchStrict(player, s)) {
				ResearchEntry re = ResearchCategories.getResearch(s);
				if (re == null) continue;
				if (re.hasMeta(EnumResearchMeta.SPIKY)) tot += .5f;
				if (re.hasMeta(EnumResearchMeta.HIDDEN)) tot += .1f;
			}
		}
		return Math.min(15, Math.round(tot));
	}
}
