package thaumcraft.api.casters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;



public class FocusPackage implements IFocusElement {

	@Override
	public String getResearch() {
		return null;
	}

	public Level world;
	private LivingEntity caster;
	private UUID casterUUID;

	private float power = 1;
	private int complexity = 0;

	int index;
	UUID uid;

	public List<IFocusElement> nodes =  Collections.synchronizedList(new ArrayList<>());

	public FocusPackage() {	}

	public FocusPackage(LivingEntity caster) {
		super();
		world = caster.level();
		this.caster = caster;
		casterUUID = caster.getUUID();
	}

	@Override
	public String getKey() {
		return "thaumcraft.PACKAGE";
	}

	@Override
	public EnumUnitType getType() {
		return EnumUnitType.PACKAGE;
	}

	public int getComplexity() {
		return complexity;
	}

	public void setComplexity(int complexity) {
		this.complexity = complexity;
	}

	public UUID getUniqueID() {
		return uid;
	}

	public void setUniqueID(UUID id) {
		uid = id;
	}

	public int getExecutionIndex() {
		return index;
	}

	public void setExecutionIndex(int idx) {
		index = idx;
	}

	public void addNode(IFocusElement e) {
		nodes.add(e);
	}

	public UUID getCasterUUID() {
		if (caster!=null) casterUUID = caster.getUUID();
		return casterUUID;
	}

	public void setCasterUUID(UUID casterUUID) {
		this.casterUUID = casterUUID;
	}

	/**
	 * Was a two-step lookup (world.getPlayerEntityByUUID(...), then a fallback scan of every alive
	 * EntityLivingBase in the world) - modern ServerLevel#getEntity(UUID) finds any entity (player or
	 * not) by UUID directly, so that's now just one call covering both of the old cases.
	 */
	public LivingEntity getCaster() {
		try {
			if (caster==null && world instanceof ServerLevel serverLevel) {
				Entity e = serverLevel.getEntity(getCasterUUID());
				if (e instanceof LivingEntity le) caster = le;
			}
		} catch (Exception e) {}
		return caster;
	}

	public FocusEffect[] getFocusEffects() {
		return getFocusEffectsPackage(this);
	}

	private FocusEffect[] getFocusEffectsPackage(FocusPackage fp) {
		ArrayList<FocusEffect> out = new ArrayList<>();
		for (IFocusElement el:fp.nodes) {
			if (el instanceof FocusEffect) out.add((FocusEffect) el);
			else
			if (el instanceof FocusPackage) {
				for (FocusEffect fep:getFocusEffectsPackage((FocusPackage) el))
					out.add(fep);
			} else
			if (el instanceof FocusModSplit) {
				for (FocusPackage fsp:((FocusModSplit)el).getSplitPackages())
					for (FocusEffect fep:getFocusEffectsPackage(fsp))
						out.add(fep);
			}
		}
		return out.toArray(new FocusEffect[]{});
	}

	/**
	 * Was reconstructed from a raw dimension id NBT-side (via the now-gone Forge DimensionManager); a
	 * FocusPackage never actually spans more than one Level in practice, so the caller (who by
	 * definition already knows which Level it's operating in) supplies it directly instead.
	 */
	public void deserialize(CompoundTag nbt, Level level) {
		uid = nbt.hasUUID("uid") ? nbt.getUUID("uid") : null;
		index = nbt.getInt("index");
		world = level;
		setCasterUUID(nbt.hasUUID("casterUUID") ? nbt.getUUID("casterUUID") : null);
		power = nbt.getFloat("power");
		complexity = nbt.getInt("complexity");

		ListTag nodelist = nbt.getList("nodes", Tag.TAG_COMPOUND);
		nodes.clear();
		for (int x=0;x<nodelist.size();x++) {
			CompoundTag nodenbt = nodelist.getCompound(x);
			EnumUnitType ut = EnumUnitType.valueOf(nodenbt.getString("type"));
			if (ut!=null) {
				if (ut==EnumUnitType.PACKAGE) {
					FocusPackage fp = new FocusPackage();
					fp.deserialize(nodenbt.getCompound("package"), level);
					nodes.add(fp);
					break;
				} else {
					IFocusElement fn = FocusEngine.getElement(nodenbt.getString("key"));
					if (fn!=null) {
						if (fn instanceof FocusNode) {
							((FocusNode)fn).initialize();
							if (((FocusNode)fn).getSettingList()!=null)
								for (String ns : ((FocusNode)fn).getSettingList()) {
									((FocusNode)fn).getSetting(ns).setValue(nodenbt.getInt("setting."+ns));
								}

							if (fn instanceof FocusModSplit) {
								((FocusModSplit)fn).deserialize(nodenbt.getCompound("packages"), level);
							}
						}
						addNode(fn);
					}
				}
			}
		}

	}

	public CompoundTag serialize() {
		CompoundTag nbt = new CompoundTag();
		if (uid!=null) nbt.putUUID("uid", uid);
		nbt.putInt("index", index);
		if (getCasterUUID() != null) nbt.putUUID("casterUUID", getCasterUUID());
		nbt.putFloat("power", power);
		nbt.putInt("complexity", complexity);

		//nodes
		ListTag nodelist = new ListTag();
		synchronized (nodes) {
			for (IFocusElement node:nodes) {
				if (node==null || node.getType()==null) continue;
				CompoundTag nodenbt = new CompoundTag();
				nodenbt.putString("type", node.getType().name());
				nodenbt.putString("key", node.getKey());
				if (node.getType()==EnumUnitType.PACKAGE) {
					nodenbt.put("package", ((FocusPackage)node).serialize());
					nodelist.add(nodenbt);
					break;
				} else {
					if (node instanceof FocusNode && ((FocusNode)node).getSettingList()!=null)
						for (String ns : ((FocusNode)node).getSettingList()) {
							nodenbt.putInt("setting."+ns, ((FocusNode)node).getSettingValue(ns));
						}
					if (node instanceof FocusModSplit) {
						nodenbt.put("packages", ((FocusModSplit)node).serialize());
					}
					nodelist.add(nodenbt);
				}
			}
		}
		nbt.put("nodes", nodelist);

		return nbt;
	}

	public float getPower() {
		return power;
	}

	public void multiplyPower(float pow) {
		power *= pow;
	}

	public FocusPackage copy(LivingEntity caster) {
		FocusPackage fp = new FocusPackage(caster);
		fp.deserialize(serialize(), caster.level());
		return fp;
	}

	/**
	 * Real bug found by an end-to-end GameTest, not by playing the game: a {@code FocusPackage} built via
	 * {@code ItemFocus#getPackage(ItemStack)} uses the no-arg constructor (never sets {@code caster}), and
	 * neither {@code ItemCaster#use} nor this method used to set it either - only {@code getCasterUUID()}
	 * relies on {@code caster} already being non-null to derive it, so {@link #getCaster()} silently
	 * returned null down the whole real casting path, which every {@code FocusMedium}'s raytrace bails out
	 * on ({@code if (level == null || caster == null) return null;}). Every "verified via runServer" check
	 * this session only checked boot cleanliness (recipe counts, no crash) - none of them ever actually
	 * cast a focus at a target, so this had been silently broken the whole time. Fixed by setting
	 * {@code this.caster} here too - {@code initialize} is exactly the "wire this package up to a real
	 * caster" step, so this is the natural, minimal fix.
	 */
	public void initialize(LivingEntity caster) {
		world=caster.level();
		this.caster = caster;
		IFocusElement node = nodes.get(0);
		if (node instanceof FocusMediumRoot && ((FocusMediumRoot)node).supplyTargets()==null) {
			((FocusMediumRoot)node).setupFromCaster(caster);
		}
	}

	public int getSortingHelper() {
		String s="";
		for (IFocusElement k: nodes) {
			s+=k.getKey();
			if (k instanceof FocusNode && ((FocusNode)k).getSettingList()!=null)
				for (String ns : ((FocusNode)k).getSettingList()) {
					s += ""+((FocusNode)k).getSettingValue(ns);
				}
		}
		return s.hashCode();
	}





}
