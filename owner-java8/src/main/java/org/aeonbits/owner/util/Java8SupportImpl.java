/*
 * Copyright (c) 2012-2015, Luigi R. Viggiano
 * All rights reserved.
 *
 * This software is distributable under the BSD license.
 * See the terms of the BSD license in the documentation provided with this software.
 */

package org.aeonbits.owner.util;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.aeonbits.owner.lfp.LFPUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author Luigi R. Viggiano
 */
class Java8SupportImpl implements Reflection.Java8Support {
    private boolean isJava8;
    private LoadingCache<Map.Entry<Class<?>, Method>, Optional<Invoker>> defaultMethodLookupCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10)).expireAfterAccess(Duration.ofSeconds(1)).build(ent -> {
                Class<?> proxyClassType = ent.getKey();
                Method proxyMethod = ent.getValue();
                Optional<Method> defaultMethodOp = lookupDefaultMethod(proxyClassType, proxyMethod);
                if (!defaultMethodOp.isPresent())
                    return Optional.empty();
                final Class<?> declaringClass = defaultMethodOp.get().getDeclaringClass();
                MethodHandle methodHandle;
                if (isJava8) {
                    methodHandle = Lookup.in(declaringClass)
                            .unreflectSpecial(defaultMethodOp.get(), declaringClass);
                } else {
                    MethodType rt = MethodType.methodType(defaultMethodOp.get().getReturnType(), defaultMethodOp.get().getParameterTypes());
                    methodHandle = MethodHandles.lookup()
                            .findSpecial(declaringClass, defaultMethodOp.get().getName(), rt, declaringClass);
                }
                Invoker invoker = (proxy, args) -> {
                    return methodHandle.bindTo(proxy).invokeWithArguments(args);
                };
                return Optional.of(invoker);

            });


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
        return getDefaultMethodInvoker(proxy, method, args).call();
    }

    @Override
    public Callable<Object> getDefaultMethodInvoker(Object proxy, Method method, Object[] args) {
        if (proxy == null || method == null)
            return null;
        if (!method.isDefault() && !Modifier.isAbstract(method.getModifiers()))
            return null;
        Optional<Invoker> invokerOp = defaultMethodLookupCache.get(new AbstractMap.SimpleEntry<>(proxy.getClass(), method));
        if (!invokerOp.isPresent())
            return null;
        return () -> {
            try {
                return invokerOp.get().invoke(proxy, args);
            } catch (Throwable t) {
                if (t instanceof Exception)
                    throw (Exception) t;
                throw new Exception(t);
            }
        };
    }


    private static Optional<Method> lookupDefaultMethod(Class<?> proxyClassType, Method invokedMethod) {
        if (proxyClassType == null || invokedMethod == null)
            return Optional.empty();
        if (invokedMethod.isDefault())
            return Optional.of(invokedMethod);
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        classes.addAll(Arrays.asList(LFPUtils.getInterfaces(proxyClassType)));
        if (classes.isEmpty())
            return Optional.empty();
        Class<?>[] invokedMethodPTs = invokedMethod.getParameterTypes();
        for (Class<?> classType : classes) {
            Method[] methods = classType.getMethods();
            for (Method method : methods) {
                if (!Reflection.isDefault(method))
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
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }


    private static interface Invoker {

        public Object invoke(Object proxy, Object[] args) throws Throwable;

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
