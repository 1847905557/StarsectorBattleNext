package data.scripts.orders.starter;
import com.fs.starfarer.api.combat.*; import data.scripts.orders.*; import java.util.List;
public class CaptureStarter implements OrderStarter<BattleObjectiveAPI> { public List<BattleObjectiveAPI> getInitialTargets(ShipAPI s, CombatEngineAPI e){return BattleNext_OrderUtils.enemyObjectives(s);} public CombatAssignmentType getCombatAssignmentType(){return CombatAssignmentType.CAPTURE;} }
