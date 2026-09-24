package data.scripts.orders.picker;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.OrderPicker; import java.util.*;
public class RandomPicker implements OrderPicker<CombatEntityAPI> { public AssignmentTargetAPI pick(ShipAPI s,List<CombatEntityAPI> t,CombatAssignmentType type,CombatEngineAPI e){CombatEntityAPI p=t.get(new Random().nextInt(t.size()));if(p instanceof ShipAPI)return e.getFleetManager(p.getOwner()).getDeployedFleetMember((ShipAPI)p);return (AssignmentTargetAPI)p;} }
