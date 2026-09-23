public class ProjectileSourceChainedViolation {
	public void apply(DamagingProjectileAPI projectile) {
		projectile.getSource().getOwner();
	}
}
