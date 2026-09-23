package checks.integrity;

import java.io.IOException;
import java.util.List;

public interface IntegrityRule {
	List<String> check(IntegrityCheckInput input) throws IOException;
}
