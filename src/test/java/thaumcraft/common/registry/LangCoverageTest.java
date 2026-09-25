package thaumcraft.common.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Every registered Thaumcraft item, block, entity type, mob effect and the creative tab must have an English
 * name in {@code en_us.json} - found 2026-09-25 in the first real playtest, where the creative tab showed raw
 * keys like {@code item.thaumcraft.crimson_plate_chest} for a large share of the mod's content (the port's
 * lang file had been built up only for the names a chunk happened to touch). The full list of what is missing
 * is also written to {@code build/lang-missing.txt} for easy copying.
 */
class LangCoverageTest {

	private static final String NAMESPACE = "thaumcraft";

	@Test
	void everyRegisteredObjectHasAnEnglishName() throws Exception {
		JsonObject lang;
		try (InputStream in = LangCoverageTest.class.getResourceAsStream("/assets/thaumcraft/lang/en_us.json")) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}

		List<String> missing = new ArrayList<>();
		BuiltInRegistries.ITEM.entrySet().forEach(e -> {
			if (e.getKey().location().getNamespace().equals(NAMESPACE)) check(lang, missing, "item", e.getKey().location().getPath(), e.getValue().getDescriptionId());
		});
		BuiltInRegistries.BLOCK.entrySet().forEach(e -> {
			if (e.getKey().location().getNamespace().equals(NAMESPACE)) check(lang, missing, "block", e.getKey().location().getPath(), e.getValue().getDescriptionId());
		});
		BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(e -> {
			if (e.getKey().location().getNamespace().equals(NAMESPACE)) check(lang, missing, "entity", e.getKey().location().getPath(), e.getValue().getDescriptionId());
		});
		BuiltInRegistries.MOB_EFFECT.entrySet().forEach(e -> {
			if (e.getKey().location().getNamespace().equals(NAMESPACE)) check(lang, missing, "effect", e.getKey().location().getPath(), e.getValue().getDescriptionId());
		});
		BuiltInRegistries.CREATIVE_MODE_TAB.entrySet().forEach(e -> {
			if (e.getKey().location().getNamespace().equals(NAMESPACE) && e.getValue().getDisplayName().getContents() instanceof TranslatableContents key) {
				check(lang, missing, "creative_tab", e.getKey().location().getPath(), key.getKey());
			}
		});

		Path out = Path.of("build", "lang-missing.txt");
		Files.createDirectories(out.getParent());
		Files.write(out, missing);
		assertTrue(missing.isEmpty(), missing.size() + " registered objects have no English name (see build/lang-missing.txt): " + missing);
	}

	private static void check(JsonObject lang, List<String> missing, String kind, String id, String key) {
		if (!lang.has(key)) missing.add(kind + "\t" + id + "\t" + key);
	}
}
