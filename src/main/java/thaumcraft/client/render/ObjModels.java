package thaumcraft.client.render;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * The original's own OBJ loader ({@code thaumcraft.client.lib.obj.WavefrontObject}, behind {@code AdvancedModelLoader}):
 * an OBJ file read as named groups ({@code g} / {@code o}) of polygons that a renderer draws one group at a time, each
 * with its own texture and transform - which baked block models can't do. As the original: texture V is flipped
 * ({@code 1 - v}), each face gets its computed normal, and texture coordinates are pulled 0.0005 towards the face's
 * centre so neighbouring texels don't bleed in. Models are read on first use and kept; {@link #clear} forgets them
 * (resource reloads).
 */
public final class ObjModels {

	/** One loaded model: its groups in file order. */
	public static final class Model {
		final Map<String, List<Face>> groups = new LinkedHashMap<>();

		public Iterable<String> groupNames() {
			return groups.keySet();
		}

		public boolean hasGroup(String name) {
			return groups.containsKey(name);
		}

		/** Draws one group (nothing if the model has no such group). */
		public void renderGroup(String name, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
			List<Face> faces = groups.get(name);
			if (faces == null) return;
			Matrix4f m = pose.pose();
			Matrix3f n = pose.normal();
			for (Face f : faces) {
				Vector3f normal = n.transform(new Vector3f(f.nx, f.ny, f.nz));
				int count = f.x.length;
				// quads are drawn as they are; triangles repeat their last corner (the consumers here take quads)
				int emit = count == 3 ? 4 : count;
				for (int i = 0; i < emit; i++) {
					int j = Math.min(i, count - 1);
					consumer.addVertex(m, f.x[j], f.y[j], f.z[j]).setColor(r, g, b, a).setUv(f.u[j], f.v[j]).setOverlay(overlay)
							.setLight(light).setNormal(normal.x, normal.y, normal.z);
				}
			}
		}
	}

	static final class Face {
		float[] x, y, z, u, v;
		float nx, ny, nz;
	}

	private static final Map<ResourceLocation, Model> CACHE = new ConcurrentHashMap<>();
	private static final Model EMPTY = new Model();

	private ObjModels() {
	}

	public static void clear() {
		CACHE.clear();
	}

	public static Model get(ResourceLocation location) {
		return CACHE.computeIfAbsent(location, ObjModels::load);
	}

	private static Model load(ResourceLocation location) {
		try {
			var resource = Minecraft.getInstance().getResourceManager().getResource(location);
			if (resource.isEmpty()) {
				Thaumcraft.LOGGER.warn("Missing OBJ model {}", location);
				return EMPTY;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {
				return parse(reader);
			}
		} catch (Exception e) {
			Thaumcraft.LOGGER.warn("Couldn't read OBJ model {}", location, e);
			return EMPTY;
		}
	}

	/** Reads an OBJ file's groups (kept separate from loading for tests). */
	static Model parse(BufferedReader reader) throws java.io.IOException {
		Model model = new Model();
		List<float[]> verts = new ArrayList<>();
		List<float[]> uvs = new ArrayList<>();
		List<Face> current = null;
		{
			{
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) continue;
					String[] t = line.split("\\s+");
					switch (t[0]) {
						case "v" -> verts.add(new float[] { Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3]) });
						case "vt" -> uvs.add(new float[] { Float.parseFloat(t[1]), 1.0f - Float.parseFloat(t[2]) });
						case "g", "o" -> current = model.groups.computeIfAbsent(t.length > 1 ? t[1] : "", k -> new ArrayList<>());
						case "f" -> {
							if (current == null) current = model.groups.computeIfAbsent("", k -> new ArrayList<>());
							current.add(face(t, verts, uvs));
						}
						default -> {
						}
					}
				}
			}
		}
		return model;
	}

	private static Face face(String[] t, List<float[]> verts, List<float[]> uvs) {
		int count = t.length - 1;
		Face f = new Face();
		f.x = new float[count];
		f.y = new float[count];
		f.z = new float[count];
		f.u = new float[count];
		f.v = new float[count];
		for (int i = 0; i < count; i++) {
			String[] idx = t[i + 1].split("/");
			float[] p = verts.get(index(idx[0], verts.size()));
			f.x[i] = p[0];
			f.y[i] = p[1];
			f.z[i] = p[2];
			if (idx.length > 1 && !idx[1].isEmpty()) {
				float[] uv = uvs.get(index(idx[1], uvs.size()));
				f.u[i] = uv[0];
				f.v[i] = uv[1];
			}
		}
		// the original's calculateFaceNormal: (v1 - v0) x (v2 - v0), normalised
		Vector3f a = new Vector3f(f.x[1] - f.x[0], f.y[1] - f.y[0], f.z[1] - f.z[0]);
		Vector3f b = new Vector3f(f.x[2] - f.x[0], f.y[2] - f.y[0], f.z[2] - f.z[0]);
		Vector3f n = a.cross(b);
		if (n.lengthSquared() > 0) n.normalize();
		f.nx = n.x;
		f.ny = n.y;
		f.nz = n.z;
		// the original's 0.0005 texture inset towards the face's average coordinate
		float au = 0;
		float av = 0;
		for (int i = 0; i < count; i++) {
			au += f.u[i];
			av += f.v[i];
		}
		au /= count;
		av /= count;
		for (int i = 0; i < count; i++) {
			f.u[i] += f.u[i] > au ? -5.0e-4f : 5.0e-4f;
			f.v[i] += f.v[i] > av ? -5.0e-4f : 5.0e-4f;
		}
		return f;
	}

	private static int index(String s, int size) {
		int i = Integer.parseInt(s);
		return i < 0 ? size + i : i - 1;
	}
}
