package thaumcraft.api.golems.seals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;


public class SealPos  {
		public BlockPos pos;
		public Direction face;

		public SealPos(BlockPos pos, Direction face) {
			super();
			this.pos = pos;
			this.face = face;
		}

		@Override
		public int hashCode()
	    {
	        byte b0 = (byte) (face.ordinal()+1);
	        int i = 31 * b0 + pos.getX();
	        i = 31 * i + pos.getY();
	        i = 31 * i + pos.getZ();
	        return i;
	    }

		@Override
		public boolean equals(Object p_equals_1_)
	    {
	        if (this == p_equals_1_)
	        {
	            return true;
	        }
	        else if (!(p_equals_1_ instanceof SealPos))
	        {
	            return false;
	        }
	        else
	        {
	        	SealPos sp = (SealPos)p_equals_1_;
	            return !pos.equals(sp.pos) ? false : face.equals(sp.face);
	        }
	    }
	}
