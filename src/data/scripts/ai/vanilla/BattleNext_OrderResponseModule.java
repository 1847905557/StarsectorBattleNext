package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.maneuvers.BattleNext_EscortTargetManeuverV3;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.maneuvers.BattleNext_RetreatManeuver;
import data.scripts.ai.vanilla.maneuvers.BattleNext_TaskMoveManeuver;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.OrderResponseModule.
 * Turns war-room assignments and AI flags into the maneuver the ship should run next.
 * The engine's direct-order channel is not exposed to the mod API, so orders are
 * reconstructed from the ship's assignment and retreat state.
 */
public class BattleNext_OrderResponseModule {
	// intentionally kept dead: vanilla constants never referenced by ported code; the
	// decompiled names carry no recoverable context
	public static final float unusedRange1750 = 1750.0F;
	public static final float unusedRange1000 = 1000.0F;
	private ShipAPI ship;
	private BattleNext_FlockingAI flockingAI;
	private BattleNext_ManeuverAPI maneuverAPI;
	private boolean unusedFlag = false;
	private boolean isCarrier = false;
	private DeployedFleetMemberAPI fleetMember;
	private CombatFleetManagerAPI fleetManager;
	private CombatTaskManagerAPI taskManager;
	private final BattleNext_AttackAIModule attackModule;
	private boolean engaged = false;
	private boolean unusedFlag2 = false;

	public BattleNext_OrderResponseModule(ShipAPI ship, BattleNext_FlockingAI flockingAI, BattleNext_AttackAIModule attackModule, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.flockingAI = flockingAI;
		this.attackModule = attackModule;
		this.maneuverAPI = maneuverAPI;
		this.fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner());
		this.taskManager = this.fleetManager.getTaskManager(ship.isAlly());
		this.updateFleetMember();
	}

	private DeployedFleetMemberAPI getFleetMember() {
		this.updateFleetMember();
		return this.fleetMember;
	}

	private void updateFleetMember() {
		if (this.fleetManager != null && this.fleetMember == null) {
			this.fleetMember = this.fleetManager.getDeployedFleetMember(this.ship);
			if (this.fleetMember != null) {
				this.isCarrier = this.fleetMember.getMember().isCarrier();
			}
		}
	}

	public boolean isRetreating() {
		BattleNext_DirectOrders orders = this.getOrders();
		return orders.getType() == BattleNext_DirectOrdersType.RETREAT;
	}

	// intentionally kept dead: vanilla helper never called
	private ShipAPI unusedFindClosestWingMember(ShipAPI fighter) {
		if (fighter.getWing() == null) {
			return fighter;
		}

		List<ShipAPI> wingMembers = fighter.getWing().getWingMembers();
		float closestDistance = Float.MAX_VALUE;
		ShipAPI closest = null;

		for (ShipAPI member : wingMembers) {
			if (!member.isHulk()) {
				float distance = BattleNext_ShipAIHelper.getDistance(fighter.getLocation(), member.getLocation());
				if (distance < closestDistance) {
					closest = member;
					closestDistance = distance;
				}
			}
		}

		return closest;
	}

	// approximation: the engine reads CombatTaskManager.getOrdersForFleetMember, which the mod
	// API does not expose; orders are reconstructed from the ship's state and assignment
	public BattleNext_DirectOrders getOrders() {
		if (this.ship.isRetreating()) {
			return new BattleNext_DirectOrders(BattleNext_DirectOrdersType.RETREAT, null, null);
		}

		AssignmentInfo assignment = this.taskManager.getAssignmentFor(this.ship);
		if (assignment != null) {
			if (assignment.getType() == CombatAssignmentType.RETREAT) {
				return new BattleNext_DirectOrders(BattleNext_DirectOrdersType.RETREAT, null, null);
			}

			if (assignment.getType() == CombatAssignmentType.REPAIR_AND_REFIT) {
				return new BattleNext_DirectOrders(BattleNext_DirectOrdersType.REPAIR_AND_REFIT, null, null);
			}

			return new BattleNext_DirectOrders(BattleNext_DirectOrdersType.CARRY_OUT_COMBAT_TASK, assignment.getType(), assignment.getTarget());
		}

		return new BattleNext_DirectOrders(BattleNext_DirectOrdersType.NO_ORDERS, null, null);
	}

	public boolean hasOrders() {
		return this.getOrders().getType() != BattleNext_DirectOrdersType.NO_ORDERS;
	}

	public BattleNext_OrderResponseData getOrderResponse() {
		if (this.ship.getAIFlags().hasFlag(AIFlags.IGNORES_ORDERS)) {
			return null;
		}

		if (this.ship.getAIFlags().hasFlag(AIFlags.ESCORT_OTHER_SHIP)
			&& !this.ship.isRetreating()
			&& this.ship.getAIFlags().getCustom(AIFlags.ESCORT_OTHER_SHIP) instanceof ShipAPI) {
			ShipAPI escortTarget = (ShipAPI) this.ship.getAIFlags().getCustom(AIFlags.ESCORT_OTHER_SHIP);
			float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), escortTarget.getLocation());
			BattleNext_OrderResponseData response = new BattleNext_OrderResponseData(this);
			if (distance < 2000.0F) {
				BattleNext_EscortTargetManeuverV3 escortManeuver = new BattleNext_EscortTargetManeuverV3(this.ship, escortTarget, this.attackModule, this.flockingAI, this.maneuverAPI);
				response.maneuver = escortManeuver;
				response.duration = 2.0F;
				return response;
			} else {
				BattleNext_TaskMoveManeuver moveManeuver = new BattleNext_TaskMoveManeuver(this.ship, escortTarget.getLocation(), this.maneuverAPI);
				response.maneuver = moveManeuver;
				response.duration = 2.0F;
				return response;
			}
		} else {
			if (this.ship.getAIFlags().hasFlag(AIFlags.CAMP_LOCATION)
				&& !this.ship.isRetreating()
				&& this.ship.getAIFlags().getCustom(AIFlags.CAMP_LOCATION) instanceof Vector2f) {
				Vector2f campLocation = (Vector2f) this.ship.getAIFlags().getCustom(AIFlags.CAMP_LOCATION);
				BattleNext_OrderResponseData campResponse = new BattleNext_OrderResponseData(this);
				BattleNext_TaskMoveManeuver campManeuver = new BattleNext_TaskMoveManeuver(this.ship, campLocation, this.maneuverAPI);
				campResponse.maneuver = campManeuver;
				campResponse.duration = 3.0F;
				return campResponse;
			}

			BattleNext_OrderResponseData response = new BattleNext_OrderResponseData(this);
			BattleNext_DirectOrders orders = this.getOrders();
			this.engaged = false;
			if (this.ship.getWing() == null
				|| this.ship.getWing().getSourceShip() == null
				// approximation: FighterWing.isCarrierAlive() checks the source bay's carrier for life
				|| !this.ship.getWing().getSourceShip().isAlive()
				|| !BattleNext_ShipAIHelper.isAlwaysEscortWing(this.ship.getWing().getSpec()) && !this.ship.getWing().getSourceShip().isPullBackFighters()) {
				if (this.ship.isFighter() && this.ship.getWing() != null) {
					return null;
				}

				switch (orders.getType()) {
					case CARRY_OUT_COMBAT_TASK:
						return this.getOrderResponseForOrders(orders);
					case RETREAT:
						BattleNext_RetreatManeuver retreatManeuver = new BattleNext_RetreatManeuver(this.ship, this.flockingAI, this.maneuverAPI);
						response.maneuver = retreatManeuver;
						response.duration = 3.0F;
						this.ship.setRetreating(true, this.ship.isDirectRetreat());
						// intentionally kept: vanilla case 2 falls through into the default return
					case REPAIR_AND_REFIT:
						return response;
					case SEARCH_AND_DESTROY:
					case NO_ORDERS:
					default:
						return this.getNoOrderResponse();
				}
			} else {
				BattleNext_EscortTargetManeuverV3 escortManeuver = new BattleNext_EscortTargetManeuverV3(
					this.ship, this.ship.getWing().getSourceShip(), this.attackModule, this.flockingAI, this.maneuverAPI
				);
				response.maneuver = escortManeuver;
				response.duration = 2.0F;
				return response;
			}
		}
	}

	protected BattleNext_OrderResponseData getNoOrderResponse() {
		return null;
	}

	public boolean isEngaged() {
		return this.engaged;
	}

	public void advance(float amount) {
	}

	private void setMovementDestination(Vector2f destination) {
		if (destination != null) {
			this.ship.getAIFlags().setFlag(AIFlags.MOVEMENT_DEST, 3.0F, destination);
		} else {
			this.ship.getAIFlags().unsetFlag(AIFlags.MOVEMENT_DEST);
		}
	}

	private void setMovementDestinationForTask(BattleNext_DirectOrders orders) {
		if (orders != null) {
			if (orders.getTaskType() != CombatAssignmentType.AVOID
				&& orders.getTaskType() != CombatAssignmentType.IGNORE
				&& orders.getTaskTarget() != null
				&& orders.getTaskTarget().getLocation() != null) {
				Vector2f location = orders.getTaskTarget().getLocation();
				float distance = Misc.getDistance(location, this.ship.getLocation()) - this.ship.getCollisionRadius();
				if (distance < 2000.0F) {
					this.setMovementDestination(null);
				} else {
					this.setMovementDestination(location);
				}
			} else {
				this.setMovementDestination(null);
			}
		}
	}

	private BattleNext_OrderResponseData getOrderResponseForOrders(BattleNext_DirectOrders orders) {
		if (orders != null) {
			this.setMovementDestinationForTask(orders);
		}

		BattleNext_OrderResponseData response = new BattleNext_OrderResponseData(this);
		CombatAssignmentType taskType = orders.getTaskType();
		AssignmentTargetAPI taskTarget = orders.getTaskTarget();
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), taskTarget.getLocation());
		if (taskType != CombatAssignmentType.LIGHT_ESCORT
			&& taskType != CombatAssignmentType.MEDIUM_ESCORT
			&& taskType != CombatAssignmentType.HEAVY_ESCORT) {
			if (taskType == CombatAssignmentType.INTERCEPT && distance > 2700.0F) {
				BattleNext_TaskMoveManeuver interceptApproach = new BattleNext_TaskMoveManeuver(this.ship, taskTarget.getLocation(), this.maneuverAPI);
				response.maneuver = interceptApproach;
				response.duration = 2.0F;
				return response;
			}

			if (distance > 1500.0F) {
				// intentionally kept dead: vanilla writes the flag but never reads it
				this.unusedFlag2 = false;
			}

			// intentionally kept dead: vanilla assigns true and then false to a local never read
			boolean civilianRally = taskType == CombatAssignmentType.RALLY_CIVILIAN;
			HullSize effectiveHullSize = this.ship.getHullSize();
			if (!this.ship.isFrigate()) {
				effectiveHullSize = HullSize.DESTROYER;
			}

			float arcRange = 210.0F - Math.min(this.ship.getMaxSpeed() * 0.5F, 100.0F);
			int enemiesInArc = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(this.ship, taskTarget.getLocation(), arcRange, 1500.0F, effectiveHullSize, null);
			Vector2f targetLocation = taskTarget.getLocation();
			ShipAPI nearestFriendly = BattleNext_ShipAIHelper.getNearestShip(this.ship.getOwner(), targetLocation, false);
			DeployedFleetMemberAPI closestDeployed = this.findClosestToLocation(this.fleetManager, targetLocation, false);
			float closestDeployedDistance = 1.0E10F;
			if (closestDeployed != null && closestDeployed.getShip() != this.ship) {
				closestDeployedDistance = BattleNext_ShipAIHelper.getDistance(closestDeployed.getShip().getLocation(), targetLocation);
			}

			float nearestFriendlyDistance = Float.MAX_VALUE;
			if (nearestFriendly != null) {
				nearestFriendlyDistance = BattleNext_ShipAIHelper.getDistance(nearestFriendly.getLocation(), taskTarget.getLocation());
			}

			if (taskType == CombatAssignmentType.DEFEND || taskType == CombatAssignmentType.CONTROL || taskType == CombatAssignmentType.HARASS || taskType == CombatAssignmentType.ENGAGE) {
				ShipAPI nearbyEnemy = BattleNext_ShipAIHelper.getNearestEnemyInRangeIncludingFighters(this.ship, 3000.0F);
				if (nearestFriendlyDistance > distance + 300.0F && distance < 3000.0F && nearbyEnemy != null) {
					this.engaged = true;
					return null;
				}
			}

			boolean carrierWithoutCombatHints = BattleNext_ShipAIHelper.isCarrier(this.ship);
			carrierWithoutCombatHints &= !this.ship.getHullSpec().getHints().contains(ShipTypeHints.NO_AUTO_ESCORT);
			carrierWithoutCombatHints &= !this.ship.getHullSpec().getHints().contains(ShipTypeHints.COMBAT);
			if ((taskType == CombatAssignmentType.INTERCEPT || taskType == CombatAssignmentType.AVOID || taskType == CombatAssignmentType.IGNORE || taskType == CombatAssignmentType.STRIKE && !carrierWithoutCombatHints)
				&& distance < 2700.0F
				// approximation: the engine reads task.getDeployedFleetMember(), which unwraps to the
				// task target itself when it is a deployed fleet member
				&& taskTarget instanceof DeployedFleetMemberAPI) {
				// the engine calls getShip(), which fabricates a placeholder for fighter-wing targets;
				// the API accessor returns null instead
				response.forcedTarget = ((DeployedFleetMemberAPI) taskTarget).getShip();
				response.maneuver = null;
				response.duration = 0.0F;
				return response;
			}

			boolean isBomberWing = false;
			boolean isSupportWing = false;
			if (this.ship.getWing() != null) {
				isBomberWing = this.ship.getWing().getSpec().isBomber();
				isSupportWing = this.ship.getWing().getSpec().isSupport();
			}

			if ((isBomberWing || isSupportWing) && distance < 3000.0F && taskTarget instanceof DeployedFleetMemberAPI) {
				response.forcedTarget = ((DeployedFleetMemberAPI) taskTarget).getShip();
				response.maneuver = null;
				response.duration = 0.0F;
				return response;
			}

			if (!civilianRally
				&& (taskType != CombatAssignmentType.RALLY_CARRIER || !(distance > 1000.0F))
				&& (taskType != CombatAssignmentType.STRIKE || !isBomberWing && !isSupportWing)
				&& (taskType != CombatAssignmentType.ENGAGE || !isBomberWing && !isSupportWing)
				&& (taskType != CombatAssignmentType.AVOID || !isBomberWing && !isSupportWing)
				&& (
					taskType != CombatAssignmentType.CAPTURE && taskType != CombatAssignmentType.ASSAULT
						|| !(closestDeployedDistance >= distance)
						|| taskTarget.getOwner() == this.ship.getOwner()
						|| this.maneuverAPI.getThreatEvaluator().isThreatLow()
						|| !(nearestFriendlyDistance > distance + 500.0F)
				)
				&& (
					taskType != CombatAssignmentType.RALLY_STRIKE_FORCE && taskType != CombatAssignmentType.RALLY_CIVILIAN
						|| !(this.maneuverAPI.getThreatEvaluator().getTotalThreat() <= 0.0F)
							&& (!this.maneuverAPI.getThreatEvaluator().isAllThreatsActive() || this.maneuverAPI.getThreatEvaluator().isThreatHigh())
				)) {
				if (this.maneuverAPI.getThreatEvaluator().isThreatHigh()
					|| !this.maneuverAPI.getThreatEvaluator().isAllThreatsValid() && this.maneuverAPI.getThreatEvaluator().getMaxThreatData() != null
					|| this.maneuverAPI.getThreatEvaluator().isThreatLow() && enemiesInArc > 0) {
					float defenseArc = 90.0F + Math.max(0.0F, 3000.0F - distance) * 0.01F;
					int enemiesNearArc = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(this.ship, targetLocation, defenseArc, distance + 1000.0F, effectiveHullSize, null);
					if (taskType != CombatAssignmentType.DEFEND && taskType != CombatAssignmentType.ASSAULT && taskType != CombatAssignmentType.CAPTURE && taskType != CombatAssignmentType.CONTROL
						|| distance > 4000.0F) {
						enemiesNearArc = 0;
					}

					if (!(distance < 1500.0F) && enemiesNearArc <= 0) {
						this.ship.getAIFlags().unsetFlag(AIFlags.MOVEMENT_DEST_WHILE_SIDETRACKED);
						BattleNext_TaskMoveManeuver taskApproach = new BattleNext_TaskMoveManeuver(this.ship, taskTarget.getLocation(), this.maneuverAPI);
						response.maneuver = taskApproach;
						response.duration = 3.0F;
						return response;
					} else {
						this.engaged = true;
						this.ship.getAIFlags().setFlag(AIFlags.MOVEMENT_DEST_WHILE_SIDETRACKED, 5.0F, taskTarget.getLocation());
						return null;
					}
				}

				if (distance < 2000.0F) {
					ShipAPI nearbyEnemy = BattleNext_ShipAIHelper.getNearestEnemyInRangeIncludingFighters(this.ship, 3000.0F);
					this.maneuverAPI.setMaxTargetRange(3000.0F);
					if (nearbyEnemy != null) {
						this.engaged = true;
						this.ship.getAIFlags().unsetFlag(AIFlags.MOVEMENT_DEST_WHILE_SIDETRACKED);
						return null;
					}
				}
			} else {
				this.ship.getAIFlags().unsetFlag(AIFlags.MOVEMENT_DEST_WHILE_SIDETRACKED);
			}

			this.ship.getAIFlags().unsetFlag(AIFlags.MOVEMENT_DEST_WHILE_SIDETRACKED);
			Vector2f defendPosition = null;
			if (taskType == CombatAssignmentType.DEFEND && taskTarget instanceof DeployedFleetMemberAPI && distance < 3000.0F) {
				DeployedFleetMemberAPI defendTarget = (DeployedFleetMemberAPI) taskTarget;
				if (defendTarget.getShip() != null) {
					ArrayList<DeployedFleetMemberAPI> assignedMembers = new ArrayList<>();

					for (DeployedFleetMemberAPI member : this.fleetManager.getDeployedCopyDFM()) {
						if (member.getShip() != null) {
							AssignmentInfo memberAssignment = this.taskManager.getAssignmentFor(member.getShip());
							if (memberAssignment != null && memberAssignment.getTarget() == taskTarget) {
								assignedMembers.add(member);
							}
						}
					}

					Vector2f defendCenter = new Vector2f(taskTarget.getLocation());
					float totalWeight = 0.1F;
					defendCenter.scale(totalWeight);

					for (DeployedFleetMemberAPI member : assignedMembers) {
						Vector2f weightedPosition = new Vector2f(member.getShip().getLocation());
						float weight = BattleNext_ShipAIHelper.getThreat(member.getShip(), false);
						weightedPosition.scale(weight);
						Vector2f.add(defendCenter, weightedPosition, defendCenter);
						totalWeight += weight;
					}

					defendCenter.scale(1.0F / totalWeight);
					float awayAngle = Misc.getAngleInDegrees(defendCenter, this.ship.getLocation());
					float standOff = 1000.0F + defendTarget.getShip().getCollisionRadius() + this.ship.getCollisionRadius();
					Vector2f defendOffset = Misc.getUnitVectorAtDegreeAngle(awayAngle);
					defendOffset.scale(standOff);
					// approximation: DeployedFleetMemberAPI has no getLocation; the engine's accessor
					// returns the ship's (or wing leader's) position, guarded non-null above
					Vector2f.add(defendOffset, defendTarget.getShip().getLocation(), defendOffset);
					defendPosition = defendOffset;
				}
			}

			switch (taskType) {
				// the engine switch also handles PATROL here; that task type converts to no API
				// assignment and cannot appear in reconstructed orders
				case RECON:
				case RALLY_STRIKE_FORCE:
				case RALLY_FIGHTERS:
				case RALLY_CARRIER:
				case CAPTURE:
				case CONTROL:
				case ASSAULT:
				case DEFEND:
				case RALLY_TASK_FORCE:
				case RALLY_CIVILIAN:
				case HARASS:
					Vector2f moveTarget = taskTarget.getLocation();
					if (defendPosition != null) {
						moveTarget = defendPosition;
					}

					BattleNext_TaskMoveManeuver taskMove = new BattleNext_TaskMoveManeuver(this.ship, moveTarget, this.maneuverAPI);
					response.maneuver = taskMove;
					response.duration = 1.0F;
					break;
				case STRIKE:
					if (carrierWithoutCombatHints) {
						float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, null);
						float standOff = 200.0F;
						boolean maintainingStrikeRange = this.maneuverAPI.getAIFlags().hasFlag(AIFlags.MAINTAINING_STRIKE_RANGE);
						if (maintainingStrikeRange) {
							standOff = 600.0F;
						}

						if (distance > wingRange - standOff) {
							if (!maintainingStrikeRange) {
								this.maneuverAPI.getAIFlags().setFlag(AIFlags.MAINTAINING_STRIKE_RANGE, 5.0F + (float) Math.random() * 5.0F);
							}

							Vector2f holdPosition = BattleNext_ShipAIHelper.getUnitVectorAtAngle(BattleNext_ShipAIHelper.getAngleFast(targetLocation, this.ship.getLocation()));
							holdPosition.scale(wingRange - standOff);
							Vector2f.add(holdPosition, targetLocation, holdPosition);
							BattleNext_TaskMoveManeuver holdManeuver = new BattleNext_TaskMoveManeuver(this.ship, holdPosition, this.maneuverAPI);
							response.maneuver = holdManeuver;
							response.duration = 1.0F;
						}
					}
					break;
				case ENGAGE:
				case AVOID:
				case INTERCEPT:
					if ((isBomberWing || isSupportWing) && taskTarget instanceof DeployedFleetMemberAPI) {
						DeployedFleetMemberAPI targetMember = (DeployedFleetMemberAPI) taskTarget;
						if (!targetMember.isFighterWing()) {
							response.forcedTarget = targetMember.getShip();
						}
					} else {
						BattleNext_TaskMoveManeuver engageMove = new BattleNext_TaskMoveManeuver(this.ship, taskTarget.getLocation(), this.maneuverAPI);
						response.maneuver = engageMove;
						response.duration = 1.0F;
					}
					// intentionally kept: vanilla falls through into the empty labels below
				case IGNORE:
				case LIGHT_ESCORT:
				case MEDIUM_ESCORT:
				case HEAVY_ESCORT:
					break;
				default:
					break;
			}

			return response.forcedTarget == null && response.maneuver == null ? null : response;
		} else if (distance < 2000.0F) {
			BattleNext_EscortTargetManeuverV3 escortManeuver = new BattleNext_EscortTargetManeuverV3(
				this.ship, ((DeployedFleetMemberAPI) taskTarget).getShip(), this.attackModule, this.flockingAI, this.maneuverAPI
			);
			response.maneuver = escortManeuver;
			response.duration = 2.0F;
			return response;
		} else {
			BattleNext_TaskMoveManeuver escortApproach = new BattleNext_TaskMoveManeuver(this.ship, taskTarget.getLocation(), this.maneuverAPI);
			response.maneuver = escortApproach;
			response.duration = 2.0F;
			return response;
		}
	}

	// approximation: CombatFleetManagerAPI does not expose findClosestToLocation; the loop
	// mirrors the engine, including its quirk of never updating the running minimum, so the
	// first deployed non-wing member is returned
	private DeployedFleetMemberAPI findClosestToLocation(CombatFleetManagerAPI fleetManager, Vector2f location, boolean includeWings) {
		DeployedFleetMemberAPI closest = null;
		float closestDistance = Float.MAX_VALUE;

		for (DeployedFleetMemberAPI member : fleetManager.getDeployedCopyDFM()) {
			if (includeWings || !member.isFighterWing()) {
				ShipAPI memberShip = member.getShip();
				// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
				if (memberShip == null) {
					continue;
				}
				float distance = BattleNext_ShipAIHelper.getDistance(memberShip.getLocation(), location);
				if (distance < closestDistance) {
					closest = member;
				}
			}
		}

		return closest;
	}

	// intentionally kept dead: vanilla helper never called
	private boolean unusedIsShipActive(ShipAPI other) {
		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		return other != null && !other.isHulk() && ships.contains(other);
	}
}
