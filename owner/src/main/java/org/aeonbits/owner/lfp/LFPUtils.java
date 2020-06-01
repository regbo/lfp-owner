package org.aeonbits.owner.lfp;

import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.aeonbits.owner.util.Reflection.isDefault;

public class LFPUtils {


    public static Class<?>[] getInterfaces(Class<?> clazz) {
        if (clazz == null)
            return null;
        List<Class<?>> ifaces = ClassUtils.getAllInterfaces(clazz);
        return ifaces.toArray(new Class[ifaces.size()]);
    }


}
