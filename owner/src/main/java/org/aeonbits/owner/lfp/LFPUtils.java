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


    public static Method lookupDefaultMethod(Class<?> proxyClassType, Method invokedMethod) {
        if(proxyClassType==null||invokedMethod==null)
            return null;
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        classes.add(proxyClassType);
        classes.addAll(Arrays.asList(LFPUtils.getInterfaces(proxyClassType)));
        Class<?>[] invokedMethodPTs = invokedMethod.getParameterTypes();
        for (Class<?> classType : classes) {
            Method[] methods = classType.getMethods();
            for (Method method : methods) {
                if (!isDefault(method))
                    continue;
                if (!invokedMethod.getName().equals(method.getName()))
                    continue;
                if (!invokedMethod.getReturnType().isAssignableFrom(method.getReturnType()))
                    continue;
                Class<?>[] methodPTs = method.getParameterTypes();
                if (invokedMethodPTs.length != methodPTs.length)
                    continue;
                boolean ptMatch = true;
                for (int i = 0; ptMatch && i < invokedMethodPTs.length; i++) {
                    Class<?> invokedMethodPT = invokedMethodPTs[i];
                    Class<?> methodPT = methodPTs[i];
                    if (!invokedMethodPT.isAssignableFrom(methodPT))
                        ptMatch = false;
                }
                if (!ptMatch)
                    continue;
                return method;
            }
        }
        return null;
    }

}
