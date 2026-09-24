package data.scripts.orders.suffix;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.OrderSuffix;
public class PrioritySuffix implements OrderSuffix { public float getFloatResultWithTarget(AssignmentTargetAPI t,String[] p,Object c,CombatEngineAPI e){return Float.parseFloat(p[0]);} public Object getCustomResultWithTarget(AssignmentTargetAPI t,String[] p,Object c,CombatEngineAPI e){return null;} }
