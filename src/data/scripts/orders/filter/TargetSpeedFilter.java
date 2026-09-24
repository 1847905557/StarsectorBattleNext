package data.scripts.orders.filter;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.*; import java.util.*;
public class TargetSpeedFilter implements OrderFilter<ShipAPI> { public void filterTargets(ShipAPI s,List<ShipAPI> t,String[] p,CombatEngineAPI e){float min=-1,max=p.length==1?BattleNext_OrderUtils.self(p[0],s.getMaxSpeed()):BattleNext_OrderUtils.self(p[1],s.getMaxSpeed());if(p.length>1)min=BattleNext_OrderUtils.self(p[0],s.getMaxSpeed());if(max<=0){t.clear();return;}final float lower=min,upper=max;t.removeIf(x->x.getMaxSpeed()>upper||x.getMaxSpeed()<lower);} }
