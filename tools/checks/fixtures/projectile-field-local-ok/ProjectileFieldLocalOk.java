public class ProjectileFieldLocalOk {
	private DamagingProjectileAPI projectile;

	public void apply() {
		ShipAPI source = projectile.getSource();
		if (source != null) {
			source.getOwner();
		}
	}
}
