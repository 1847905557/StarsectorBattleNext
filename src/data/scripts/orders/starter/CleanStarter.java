package data.scripts.orders.starter;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.*; import java.util.*;
public class CleanStarter implements OrderStarter<ShipAPI> { public List<ShipAPI> getInitialTargets(ShipAPI s, CombatEngineAPI e){return new ArrayList<>(List.of(s));} public CombatAssignmentType getCombatAssignmentType(){return null;} }
