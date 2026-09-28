package com.acme.demo.replay;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/** Build time: the event types a set of nodes handle, read from their {@code @OnEventHandler} methods. */
public final class EventTypes {
    private EventTypes() { }

    public static Set<Class<?>> handledBy(Object... nodes) {
        Set<Class<?>> out = new LinkedHashSet<>();
        for (Object node : nodes) {
            for (Class<?> c = node.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(OnEventHandler.class) && m.getParameterCount() == 1) {
                        out.add(m.getParameterTypes()[0]);
                    }
                }
            }
        }
        return out;
    }
}
