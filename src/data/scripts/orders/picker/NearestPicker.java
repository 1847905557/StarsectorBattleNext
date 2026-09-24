package data.scripts.orders.picker;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.OrderPicker; import org.lazywizard.lazylib.CollectionUtils; import java.util.*;
public class NearestPicker implements OrderPicker<CombatEntityAPI> { public AssignmentTargetAPI pick(ShipAPI s,List<CombatEntityAPI> t,CombatAssignmentType type,CombatEngineAPI e){t.sort(new CollectionUtils.SortEntitiesByDistance(s.getLocation()));CombatEntityAPI p=t.get(0);if(p instanceof ShipAPI)return e.getFleetManager(p.getOwner()).getDeployedFleetMember((ShipAPI)p);return (AssignmentTargetAPI)p;} }
