package thaumcraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The golem OBJ files read as the original's WavefrontObject did: named groups, flipped V, unit face normals. */
class ObjModelsTest {

	private static ObjModels.Model read(String name) throws Exception {
		var in = ObjModelsTest.class.getResourceAsStream("/assets/thaumcraft/models/obj/" + name + ".obj");
		try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
			return ObjModels.parse(r);
		}
	}

	@Test
	void groupsAreKeptByName() throws Exception {
		List<String> claws = new ArrayList<>();
		read("golem_arms_claws").groupNames().forEach(claws::add);
		assertEquals(List.of("base", "claw001", "claw002"), claws);
		List<String> base = new ArrayList<>();
		read("golem_base").groupNames().forEach(base::add);
		assertEquals(List.of("arm", "chest", "waist", "flag"), base);
	}

	@Test
	void facesHaveUnitNormalsAndInsetUvs() throws Exception {
		ObjModels.Model m = read("golem_base");
		int faces = 0;
		for (String g : m.groupNames()) {
			for (ObjModels.Face f : m.groups.get(g)) {
				faces++;
				float len = (float) Math.sqrt(f.nx * f.nx + f.ny * f.ny + f.nz * f.nz);
				assertEquals(1.0f, len, 1.0e-4f);
				for (float u : f.u) assertTrue(u >= -0.001f && u <= 1.001f);
			}
		}
		assertTrue(m.groups.get("flag").size() == 2, "the original's flag group has its two faces");
		assertTrue(faces > 0);
	}
}
