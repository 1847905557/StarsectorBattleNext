package data.scripts.orders.filter;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.*; import java.util.*;
public class HullTypeFilter implements OrderFilter<ShipAPI> { public void filterTargets(ShipAPI s,List<ShipAPI> t,String[] p,CombatEngineAPI e){if(Arrays.asList(p).contains("any"))return;t.removeIf(x->!(Arrays.asList(p).contains("phase")&&x.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.PHASE))&&!(Arrays.asList(p).contains("carrier")&&x.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CARRIER)));} }
