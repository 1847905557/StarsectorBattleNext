public class ProjectileSourceGuardedOk {
	public void apply(DamagingProjectileAPI projectile) {
		ShipAPI source = projectile.getSource();
		if (source != null) {
			source.getOwner();
		}
	}
}
