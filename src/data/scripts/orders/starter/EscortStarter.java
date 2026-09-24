package data.scripts.orders.starter;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.*; import java.util.List;
public class EscortStarter implements OrderStarter<ShipAPI> { public List<ShipAPI> getInitialTargets(ShipAPI s, CombatEngineAPI e){return BattleNext_OrderUtils.allies(s);} public CombatAssignmentType getCombatAssignmentType(){return CombatAssignmentType.MEDIUM_ESCORT;} }
