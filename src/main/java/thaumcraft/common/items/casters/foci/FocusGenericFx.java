package thaumcraft.common.items.casters.foci;

/**
 * Plain, common-safe configuration carrier for a real generic particle burst - the same real shape the true
 * original's own {@code FXDispatcher.GenPart} struct had, kept deliberately as a plain mutable POJO with
 * public fields rather than translated into a {@code record} or builder for exactly that reason (closest
 * real fidelity to the original's own real shape).
 *
 * <p>Deliberately lives in this common-side package, not {@code thaumcraft.client.fx} - {@link
 * thaumcraft.api.casters.FocusEffect#renderParticleFX} is a real override on every real {@code FocusEffect}
 * subclass (loaded and bytecode-verified on BOTH sides, since the gameplay half of these classes runs
 * server-side too), and Java's own verifier resolves every locally-referenced type in a method's body at
 * class-verification time - even for a method that's never actually invoked there. A real, previously-hit
 * crash confirmed this isn't hypothetical: constructing {@code FXGeneric} (a real {@code @OnlyIn(Dist.CLIENT)}
 * type, transitively) directly inside a `FocusEffectXxx.renderParticleFX} body made the whole class fail to
 * verify on a dedicated server ("Attempted to load class ... for invalid dist DEDICATED_SERVER"), even though
 * the method itself is only ever called client-side via {@code ClientFxHandlers}. This config object carries
 * only primitives/arrays - no client type anywhere - so every real {@code renderParticleFX} override can
 * build one safely, then hand it to {@link thaumcraft.client.fx.ClientFxHandlers#spawnGeneric} (a real
 * {@code Dist.CLIENT}-scoped class already proven safe to call from common code, the same real pattern
 * {@code EntityEldritchGolem}'s own arcing decoration already established) to do the actual real
 * construction.
 */
public class FocusGenericFx {

	public int grid = 64;
	public int age;
	// Matches FXGeneric's own real field defaults exactly (spawnR/G/B=1, dr/dg/db=0) - the real "fade to
	// black" look every one of these 10 effects that never calls setRBGColorF at all relies on.
	public float redStart = 1.0f, greenStart = 1.0f, blueStart = 1.0f;
	public float redEnd, greenEnd, blueEnd;
	public float[] alpha = { 1.0f };
	public float[] scale = { 1.0f };
	public float rot, rotstart;
	public boolean loop;
	public int partStart, partNum = 1, partInc = 1;
	public int layer;
	public double slowDown = 0.98;
	public float grav = 1.0f;
	public float randomX, randomY, randomZ;
	public int delay;
}
