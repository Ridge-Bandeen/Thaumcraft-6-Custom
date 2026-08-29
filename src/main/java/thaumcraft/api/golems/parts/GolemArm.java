package thaumcraft.api.golems.parts;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemAPI;


public class GolemArm
{
    protected static GolemArm[] arms;
    public byte id;
    public String key;
    public String[] research;
    public ResourceLocation icon;
    public Object[] components;
    public EnumGolemTrait[] traits;
    public IArmFunction function;
    public PartModel model;
    private static byte lastID;

    public GolemArm(String key, String[] research, ResourceLocation icon, PartModel model, Object[] comp, EnumGolemTrait[] tags) {
        this.key = key;
        this.research = research;
        this.icon = icon;
        components = comp;
        traits = tags;
        this.model = model;
        function = null;
    }

    public GolemArm(String key, String[] research, ResourceLocation icon, PartModel model, Object[] comp, IArmFunction function, EnumGolemTrait[] tags) {
        this(key, research, icon, model, comp, tags);
        this.function = function;
    }

    public static void register(GolemArm thing) {
        thing.id = GolemArm.lastID;
        ++GolemArm.lastID;
        if (thing.id >= GolemArm.arms.length) {
            GolemArm[] temp = new GolemArm[thing.id + 1];
            System.arraycopy(GolemArm.arms, 0, temp, 0, GolemArm.arms.length);
            GolemArm.arms = temp;
        }
        GolemArm.arms[thing.id] = thing;
    }

    /** See {@link thaumcraft.api.aspects.Aspect#getLocalizedDescription()} for why this returns a Component now. */
    public Component getLocalizedName() {
        return Component.translatable("golem.arm." + key.toLowerCase());
    }

    public Component getLocalizedDescription() {
        return Component.translatable("golem.arm.text." + key.toLowerCase());
    }

    public static GolemArm[] getArms() {
        return GolemArm.arms;
    }

    static {
        GolemArm.arms = new GolemArm[1];
        GolemArm.lastID = 0;
    }

    public interface IArmFunction extends IGenericFunction
    {
        void onMeleeAttack(IGolemAPI p0, Entity p1);

        void onRangedAttack(IGolemAPI p0, LivingEntity p1, float p2);

        RangedAttackGoal getRangedAttackAI(RangedAttackMob p0);
    }
}
