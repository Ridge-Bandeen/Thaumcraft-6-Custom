package thaumcraft.api.golems.parts;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.golems.EnumGolemTrait;


public class GolemMaterial
{
    protected static GolemMaterial[] materials;
    public byte id;
    public String key;
    public String[] research;
    public ResourceLocation texture;
    public int itemColor;
    public int healthMod;
    public int armor;
    public int damage;
    public ItemStack componentBase;
    public ItemStack componentMechanism;
    public EnumGolemTrait[] traits;
    private static byte lastID;

    public GolemMaterial(String key, String[] research, ResourceLocation texture, int itemColor, int hp, int armor, int damage, ItemStack compb, ItemStack compm, EnumGolemTrait[] tags) {
        this.key = key;
        this.research = research;
        this.texture = texture;
        this.itemColor = itemColor;
        componentBase = compb;
        componentMechanism = compm;
        healthMod = hp;
        this.armor = armor;
        traits = tags;
        this.damage = damage;
    }

    public static void register(GolemMaterial thing) {
        thing.id = GolemMaterial.lastID;
        ++GolemMaterial.lastID;
        if (thing.id >= GolemMaterial.materials.length) {
            GolemMaterial[] temp = new GolemMaterial[thing.id + 1];
            System.arraycopy(GolemMaterial.materials, 0, temp, 0, GolemMaterial.materials.length);
            GolemMaterial.materials = temp;
        }
        GolemMaterial.materials[thing.id] = thing;
    }

    /** See {@link thaumcraft.api.aspects.Aspect#getLocalizedDescription()} for why this returns a Component now. */
    public Component getLocalizedName() {
        return Component.translatable("golem.material." + key.toLowerCase());
    }

    public Component getLocalizedDescription() {
        return Component.translatable("golem.material.text." + key.toLowerCase());
    }

    public static GolemMaterial[] getMaterials() {
        return GolemMaterial.materials;
    }

    static {
        GolemMaterial.materials = new GolemMaterial[1];
        GolemMaterial.lastID = 0;
    }
}
