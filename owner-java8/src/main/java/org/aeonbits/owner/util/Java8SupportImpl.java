/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.aeonbits.owner.lfp.LFPUtils;

/**
 * @author Luigi R. Viggiano
 */
class Java8SupportImpl implements Reflection.Java8Support {
    private LoadingCache<Map.Entry<Class<?>, Optional<Method>>, Method> DEFAULT_METHOD_LOOKUP_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10)).expireAfterAccess(Duration.ofSeconds(1)).build(ent -> {
                Method method = LFPUtils.lookupDefaultMethod(ent.getKey(), ent.getValue());
                return Optional.ofNullable(method);
            });
    private boolean isJava8;

    Java8SupportImpl() {
        String version = ManagementFactory.getRuntimeMXBean().getSpecVersion();
        isJava8 = version.startsWith("1.8");
    }

    @Override
    public boolean isDefault(Method method) {
        return method.isDefault();
    }


    @Override
    public Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        final Class<?> declaringClass = method.getDeclaringClass();

        if (isJava8) {
            return Lookup.in(declaringClass)
                    .unreflectSpecial(method, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        } else {
            MethodType rt = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            return MethodHandles.lookup()
                    .findSpecial(declaringClass, method.getName(), rt, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        }
    }

    @Override
    public Callable<Object> getDefaultMethodInvoker(Object proxy, Method method, Object[] args) {
        if (!method.isDefault()) {
            Optional<Method> methodOp = DEFAULT_METHOD_LOOKUP_CACHE.get(new AbstractMap.SimpleEntry<>(proxy.getClass(), method));
            if (!methodOp.isPresent())
                return null;
            method = methodOp.get();
        }
        final Method methodF = method;
        return () -> {
            try {
                return invokeDefaultMethod(proxy, methodF, args);
            } catch (Throwable throwable) {
                if (throwable instanceof Exception)
                    throw (Exception) throwable;
                throw new Exception(throwable);
            }
        };
    }

    private static class Lookup {
        private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR = lookupConstructor();

        private static Constructor<MethodHandles.Lookup> lookupConstructor() {
            try {
                Constructor<MethodHandles.Lookup> ctor =
                        MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
                ctor.setAccessible(true);
                return ctor;
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        private static MethodHandles.Lookup in(Class<?> requestedLookupClass)
                throws IllegalAccessException, InvocationTargetException, InstantiationException {
            return LOOKUP_CONSTRUCTOR.newInstance(requestedLookupClass, MethodHandles.Lookup.PRIVATE);
        }
    }
}
