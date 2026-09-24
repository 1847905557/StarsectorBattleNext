package data.scripts.orders.filter;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.OrderFilter; import org.lazywizard.lazylib.MathUtils; import java.util.*;
public class DistanceFilter implements OrderFilter<CombatEntityAPI> { public void filterTargets(ShipAPI s,List<CombatEntityAPI> t,String[] p,CombatEngineAPI e){float min=-1,max=p.length==1?Float.parseFloat(p[0]):Float.parseFloat(p[1]);if(p.length>1)min=Float.parseFloat(p[0]);if(max<=0){t.clear();return;}final float lower=min,upper=max;t.removeIf(x->{float d=MathUtils.getDistance(s,x);return d>upper||d<lower;});} }
