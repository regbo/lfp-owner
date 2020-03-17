package org.aeonbits.owner.lfp;

import java.util.List;

import org.aeonbits.owner.Config;
import org.apache.commons.lang3.ClassUtils;

public class LFPUtils {

	public static Class<?>[] getInterfaces(Class<? extends Config> clazz) {
		if (clazz == null)
			return null;
		List<Class<?>> ifaces = ClassUtils.getAllInterfaces(clazz);
		return ifaces.toArray(new Class[ifaces.size()]);
	}

}
