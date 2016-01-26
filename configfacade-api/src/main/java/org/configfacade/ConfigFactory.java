package org.configfacade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Arrays.asList;

import java.beans.Introspector;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.configfacade.Config.Property;
import org.configfacade.Config.Type;
import org.configfacade.ConfigMap.ReplaceableConfigMap;

import com.google.common.base.Converter;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.FutureCallback;

public final class ConfigFactory {

    private ConfigFactory() {
    }

    public static Config fromMap(Map<String, ? extends Object> m) {
        return from(toConfigMap(m));
    }

    public static Config fromProperties(Properties m) {
        return from(toConfigMap(m));
    }

    public static Config from(ConfigMap m) {
        return new DefaultConfig(replaceable(m), "");
    }

    public static ReplaceableConfigMap replaceable(ConfigMap m) {
        if (m instanceof ReplaceableConfigMap)
            return (ReplaceableConfigMap) m;
        return new VolatileConfigMap(m);
    }

    public static ConfigMap chain(ConfigMap... m) {
        return chain(asList(m));
    }

    public static ConfigMap chain(Iterable<ConfigMap> m) {
        return new ChainedConfigMap(m);
    }

    @SuppressWarnings("unchecked")
    public static <T> T bind(Config config, Class<? extends T> clazz) {
        Class<?>[] interfaces = { clazz };
        ConfigInvocationHandler h = new ConfigInvocationHandler(config, false);
        return (T) Proxy.newProxyInstance(ConfigFactory.class.getClassLoader(), interfaces, h);
    }

    @SuppressWarnings("unchecked")
    public static <T> T bind(Config config, Class<? extends T> clazz, BindConfig bc) {
        Class<?>[] interfaces = { clazz };
        ConfigInvocationHandler h = new ConfigInvocationHandler(config, bc.isAllowMissing());
        return (T) Proxy.newProxyInstance(ConfigFactory.class.getClassLoader(), interfaces, h);
    }

    public static class BindConfig {

        private boolean allowMissing;

        public boolean isAllowMissing() {
            return allowMissing;
        }

        public void setAllowMissing(boolean allowMissing) {
            this.allowMissing = allowMissing;
        }
    }

    public static Config fromBean(Class<?> c, Object o) {
        return from(toConfigMap(c, o));
    }

    private static class ChainedConfigMap implements ConfigMap {

        private final Iterable<ConfigMap> maps;

        public ChainedConfigMap(Iterable<ConfigMap> maps) {
            super();
            this.maps = maps;
        }

        @Override
        public Object get(String key) {
            for (ConfigMap m : getMaps()) {
                Object o = m.get(key);
                if (o != null)
                    return o;
            }
            return null;
        }

        @Override
        public Iterable<String> getRawKeys() {
            return FluentIterable.from(getMaps()).transformAndConcat(new Function<ConfigMap, Iterable<String>>() {

                @Override
                public Iterable<String> apply(ConfigMap input) {
                    return input.getRawKeys();
                }

            }).toList();
        }

        @Override
        public boolean containsKey(String key) {
            for (ConfigMap m : getMaps()) {
                if (m.containsKey(key))
                    return true;
            }
            return false;
        }

        protected Iterable<ConfigMap> getMaps() {
            return this.maps;
        }
    }

    private static class ConfigInvocationHandler implements InvocationHandler {

        private final Config config;
        private final boolean allowMissing;

        public ConfigInvocationHandler(Config config, boolean allowMissing) {
            super();
            this.config = config;
            this.allowMissing = allowMissing;

        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            checkArgument(args == null);
            PropertyMethod pm = PropertyMethod.resolve(method);
            checkState(pm != null, "Is not a valid property: %s", method);
            Property<?> p = config.getProperty(pm.name, pm.type);
            if (pm.wrapping.isSupplier()) {
                return p;
            }
            if (pm.wrapping.isOptional()) {
                return p.optional();
            }
            if (allowMissing && !p.isPresent() && !pm.method.getReturnType().isPrimitive()) {
                return null;
            }
            return p.get();
        }

    }

    private static class PropertyMethod {

        private final String name;
        private final Method method;
        private final Type type;
        private final WrappingType wrapping;

        public PropertyMethod(String name, Method method, Type type, WrappingType wrapping) {
            super();
            this.name = name;
            this.method = method;
            this.type = type;
            this.wrapping = wrapping;
        }

        public static PropertyMethod resolve(Method method) {
            checkArgument(method != null);
            Class<?> rtype = method.getReturnType();
            if (rtype == null)
                return null;
            java.lang.reflect.Type rt = method.getGenericReturnType();
            final String n;
            String name = method.getName();
            if (name.startsWith("get")) {
                n = Introspector.decapitalize(removeStart(name, "get"));
            } else if (name.startsWith("is") && Boolean.TYPE == rtype) {
                n = Introspector.decapitalize(removeStart(name, "is"));
            } else {
                n = name;
            }
            final Type pt;
            final WrappingType wrapping = WrappingType.resolve(rtype);
            if (wrapping.isWrapping()) {
                java.lang.reflect.Type[] rts = ((ParameterizedType) rt).getActualTypeArguments();
                if (rts.length == 1 && rts[0] instanceof Class) {
                    pt = Type.findPropertyType((Class<?>) rts[0]);
                } else {
                    return null;
                }
            } else {
                pt = Type.findPropertyType(rtype);
            }

            if (pt == null)
                return null;

            return new PropertyMethod(n, method, pt, wrapping);
        }

        public enum WrappingType {
            NONE() {

                @Override
                public boolean isType(Class<?> c) {
                    return false;
                }
            },
            SUPPLIER() {

                @Override
                public boolean isType(Class<?> c) {
                    return Supplier.class.isAssignableFrom(c);
                }
            },
            OPTIONAL() {

                @Override
                public boolean isType(Class<?> c) {
                    return Optional.class.isAssignableFrom(c);
                }
            };

            public abstract boolean isType(Class<?> c);

            public boolean isWrapping() {
                return this != NONE;
            }

            public boolean isSupplier() {
                return this == SUPPLIER;
            }

            public boolean isOptional() {
                return this == OPTIONAL;
            }

            public static WrappingType resolve(Class<?> c) {
                for (WrappingType t : WrappingType.values()) {
                    if (t.isType(c))
                        return t;
                }
                return NONE;
            }
        }

    }

    private static class VolatileConfigMap implements ReplaceableConfigMap {

        private volatile ConfigMap map;
        private final EventBus eventBus;

        public VolatileConfigMap(ConfigMap map) {
            super();
            this.map = map;
            this.eventBus = new EventBus(new SubscriberExceptionHandler() {

                @Override
                public void handleException(Throwable exception, SubscriberExceptionContext context) {
                    // throw new RuntimeException(exception);
                }
            });
        }

        public void replace(ConfigMap map) {
            this.map = map;
            eventBus.post(map);
        }

        @Override
        public Object get(String key) {
            return map.get(key);
        }

        @Override
        public Iterable<String> getRawKeys() {
            return map.getRawKeys();
        }

        @Override
        public boolean containsKey(String key) {
            return map.containsKey(key);
        }

        @Override
        public void addListener(FutureCallback<ConfigMap> listener) {
            eventBus.register(new EventBusConfigListener(listener));
        }

        @Override
        public void reload() {
            eventBus.post(map);
        }
    }

    private static class EventBusConfigListener {

        private final FutureCallback<ConfigMap> callback;

        public EventBusConfigListener(FutureCallback<ConfigMap> callback) {
            super();
            this.callback = callback;
        }

        @Subscribe
        public void onChange(ConfigMap map) {
            callback.onSuccess(map);
        }
    }

    public static String prettyPrint(Config c) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        for (String k : c.getPaths()) {
            sb.append(k).append("=").append(c.getString(k).optional().or(""));
            sb.append("\n");
        }
        return sb.toString();
    }

    private final static Function<Object, String> stringFunction = new Function<Object, String>() {

        public String apply(Object input) {
            return input.toString();
        }
    };

    private final static Converter<String, Boolean> booleanConverter = converter(new Function<String, Boolean>() {

        public Boolean apply(String input) {
            return Boolean.parseBoolean(input);
        }
    });

    private static <T> Converter<String, T> converter(Function<String, T> f) {
        return Converter.from(f, stringFunction);
    }

    public static ConfigMap toConfigMap(Map<?, ? extends Object> m) {
        return new MapMapLike(m);
    }

    public static ConfigMap toConfigMap(Class<?> clazz, Object target) {
        return new BeanConfigMap(clazz, target);
    }

    private static class BeanConfigMap implements ConfigMap {

        private final Class<?> clazz;
        private final Object target;
        private final Map<String, PropertyMethod> methodMap;

        public BeanConfigMap(Class<?> clazz, Object target) {
            super();
            this.clazz = clazz;
            this.target = target;
            Method[] ms = this.clazz.getMethods();
            Map<String, PropertyMethod> map = Maps.newLinkedHashMap(); // for
                                                                       // predicatable
                                                                       // order
            for (Method m : ms) {
                PropertyMethod pm = PropertyMethod.resolve(m);
                if (pm != null) {
                    map.put(pm.name, pm);
                }
            }
            this.methodMap = ImmutableMap.copyOf(map);
        }

        @Override
        public Object get(String key) {
            PropertyMethod p = methodMap.get(key);
            if (p == null)
                return null;
            try {
                Object o = p.method.invoke(this.target);
                if (p.wrapping.isSupplier()) {
                    return ((Supplier<?>) o).get();
                } else if (p.wrapping.isOptional()) {
                    Optional<?> opt = ((Optional<?>) o);
                    if (opt.isPresent())
                        return opt.get();
                    return null;
                }
                return o;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Iterable<String> getRawKeys() {
            return methodMap.keySet();
        }

        @Override
        public boolean containsKey(String key) {
            return methodMap.containsKey(key);
        }

    }

    private static class MapMapLike implements ConfigMap {

        private final Map<?, ? extends Object> map;

        public MapMapLike(Map<?, ? extends Object> map) {
            super();
            this.map = map;
        }

        @Override
        public Object get(String key) {
            return map.get(key);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Iterable<String> getRawKeys() {
            return (Iterable<String>) map.keySet();
        }

        @Override
        public boolean containsKey(String key) {
            return map.containsKey(key);
        }

        @Override
        public String toString() {
            return map.toString();
        }

    }

    private static class DefaultConfig implements Config {

        private final ReplaceableConfigMap map;
        private final String basePath;

        public DefaultConfig(ReplaceableConfigMap map, String basePath) {
            super();
            this.map = map;
            this.basePath = basePath;
        }

        @Override
        public Config atPath(String path) {
            validatePath(path);
            final String resolvedPath = path + ".";
            return new DefaultConfig(this.map, resolvedPath);
        }

        @Override
        public Property<String> getString(String path) {
            return getProperty(path, Type.STRING);
        }

        @Override
        public Property<Long> getLong(String path) {
            return getProperty(path, Type.LONG);
        }

        @Override
        public Property<Boolean> getBoolean(String path) {
            return getProperty(path, Type.BOOLEAN);
        }

        @Override
        public Property<Integer> getInteger(String path) {
            return getProperty(path, Type.INTEGER);
        }

        @Override
        public Property<Double> getDouble(String path) {
            return getProperty(path, Type.DOUBLE);
        }

        @Override
        public Iterable<String> getKeys() {
            return Iterables.filter(getPaths(), new Predicate<String>() {

                @Override
                public boolean apply(String input) {
                    return isKey(input);
                }
            });
        }

        @Override
        public Iterable<String> getRawKeys() {
            return getPaths();
        }

        @Override
        public String getCurrentPath() {
            return this.basePath;
        }

        @Override
        public boolean hasPath(String path) {
            return this.containsKey(path);
        }

        @Override
        public Iterable<String> getPaths() {
            Iterable<String> f = Iterables.filter(map.getRawKeys(), new Predicate<String>() {

                @Override
                public boolean apply(String input) {
                    return isRawKeySubPathMatch(input);
                }
            });
            f = Iterables.transform(f, new Function<String, String>() {

                @Override
                public String apply(String input) {
                    return removeStart(input, getCurrentPath());
                }
            });
            return f;
        }

        private boolean isRawKeySubPathMatch(String s) {
            final String p = getCurrentPath();
            return !p.equals(s) && startsWith(s, p);
        }

        @Override
        public <T> Property<T> getProperty(String path, Class<T> c) {
            return getProperty(path, Type.findPropertyType(c));
        }

        protected <T> Property<T> getProperty(final String path, final Class<T> c,
                final Converter<String, ? extends T> converter) {
            return new Property<T>() {

                @Override
                public Optional<T> optional() {
                    return Optional.fromNullable(getValue(path, c, converter));
                }

                @Override
                public String getKey() {
                    return path;
                }

                @Override
                public void addListener(final FutureCallback<T> callback) {
                    super.addListener(callback);
                    final Property<T> prop = this;
                    DefaultConfig.this.addListener(new FutureCallback<ConfigMap>() {

                        @Override
                        public void onSuccess(ConfigMap result) {
                            prop.runListener(callback);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            callback.onFailure(t);
                        }
                    });
                }
            };
        }

        @SuppressWarnings("unchecked")
        public <T> Property<T> getProperty(final String path, Type propertyType) {
            checkNotNull(propertyType);
            final Property<?> prop;

            switch (propertyType) {
            case BOOLEAN:
                prop = getProperty(path, Boolean.class, booleanConverter);
                break;
            case DOUBLE:
                prop = getProperty(path, Double.class, Doubles.stringConverter());
                break;
            case INTEGER:
                prop = getProperty(path, Integer.class, Ints.stringConverter());
                break;
            case LONG:
                prop = getProperty(path, Long.class, Longs.stringConverter());
                break;
            case STRING:
                prop = getProperty(path, String.class, Converter.<String> identity());
                break;
            default:
                throw new IllegalStateException();
            }
            return (Property<T>) prop;

        }

        private <T> T getValue(String path, Class<T> c, Converter<String, ? extends T> converter) {
            Object o = get(path);
            if (o == null)
                return null;
            if (c.isAssignableFrom(o.getClass())) {
                return c.cast(o);
            }
            return converter.convert(o.toString());
        }

        @Override
        public Map<String, ? extends Object> toMap() {
            return new PrefixMap() {

                @Override
                protected ConfigMap delegate() {
                    return map;
                }

                @Override
                protected String prefix() {
                    return basePath;
                }

            };
        }

        public Properties toProperties() {
            Properties properties = new Properties();
            properties.putAll(toMap());
            return properties;
        }

        @Override
        public void replace(ConfigMap configMap) {
            checkArgument(configMap != this);
            this.map.replace(configMap);
        }

        @Override
        public void reload() {
            this.map.reload();
        }

        @Override
        public String toString() {
            return "Config [map=" + map + ", basePath=" + basePath + "]";
        }

        @Override
        public Object get(String key) {
            return this.map.get(getCurrentPath() + key);
        }

        @Override
        public boolean containsKey(String key) {
            return this.map.get(getCurrentPath() + key) != null;
        }

        @Override
        public Config withFallback(ConfigMap config) {
            checkArgument(config != this);
            return new DefaultConfig(replaceable(chain(map, config)), basePath);
        }

        @Override
        public void addListener(FutureCallback<ConfigMap> listener) {
            this.map.addListener(listener);
        }

    }

    private abstract static class PrefixMap implements Map<String, Object> {

        protected abstract ConfigMap delegate();

        protected abstract String prefix();

        @Override
        public int size() {
            final String p = prefix();
            int i = 0;
            for (String k : delegate().getRawKeys()) {
                startsWith(k, p);
                i++;
            }
            return i;

        }

        @Override
        public boolean isEmpty() {
            return size() > 0;
        }

        @Override
        public boolean containsKey(Object key) {
            return delegate().containsKey(prefix() + key);
        }

        @Override
        public boolean containsValue(Object value) {
            throw new UnsupportedOperationException("put is not yet supported");
        }

        @Override
        public Object get(Object key) {
            return delegate().get(prefix() + key);
        }

        @Override
        public Object put(String key, Object value) {
            throw new UnsupportedOperationException("put is not yet supported");
        }

        @Override
        public Object remove(Object key) {
            throw new UnsupportedOperationException("remove is not yet supported");
        }

        @Override
        public void putAll(Map<? extends String, ? extends Object> m) {
            throw new UnsupportedOperationException("putAll is not yet supported");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("clear is not yet supported");
        }

        @Override
        public Set<String> keySet() {
            Set<String> s = Sets.newLinkedHashSet();
            for (String k : delegate().getRawKeys()) {
                if (isKeyMatch(k)) {
                    s.add(removeStart(k, prefix()));
                }
            }
            return s;
        }

        @Override
        public Collection<Object> values() {
            Iterable<String> i = Iterables.filter(delegate().getRawKeys(), new Predicate<String>() {

                @Override
                public boolean apply(String input) {
                    return isKeyMatch(input);
                }

            });

            Iterable<Object> values = Iterables.transform(i, new Function<String, Object>() {

                @Override
                public Object apply(String input) {
                    return get(input);
                }

            });

            return ImmutableList.copyOf(values);
        }

        @Override
        public Set<java.util.Map.Entry<String, Object>> entrySet() {
            throw new UnsupportedOperationException("clear is not yet supported");

        }

        private boolean isKeyMatch(String s) {
            if (s == null)
                return false;
            final String p = prefix();
            return !p.equals(s) && startsWith(s, p);
        }

    }

    private static String substringBeforeLast(String s, String p) {
        int i;
        return (i = s.lastIndexOf(".")) < 0 ? s : s.substring(i);
    }

    private static String removeStart(String s, String p) {
        if (startsWith(s, p)) {
            return s.substring(p.length());
        }
        return s;
    }

    private static boolean startsWith(String s, String p) {
        return s.startsWith(p);
    }

    private static boolean endsWith(String s, String e) {
        return s.endsWith(e);
    }

    public static String validatePath(String path) {
        checkNotNull(path, "Path should not be null");
        checkArgument(!endsWith(path, "."), "Path should not end with a '.' ");
        checkArgument(!startsWith(path, "."), "Path should not start with a '.' ");
        return path;
    }

    public static boolean isKey(String s) {
        return !s.contains(".");
    }

    public static boolean isSubPath(String parentPath, String childPath) {
        return !parentPath.equals(childPath) && startsWith(childPath, parentPath);
    }

    public static String joinPath(List<String> elements) {
        return Joiner.on(".").join(elements);
    }

    public static List<String> splitPath(String path) {
        return Splitter.on(".").splitToList(path);
    }

    public static String parentPath(String path) {
        validatePath(path);
        checkArgument(!isKey(path));
        return substringBeforeLast(path, ".");
    }

    public static Set<String> filterParentPaths(Iterator<String> paths) {
        Set<String> result = Sets.newLinkedHashSet();
        while (paths.hasNext()) {
            String p = paths.next();
            if (!isKey(p)) {
                result.add(parentPath(p));
            }
        }
        return result;
    }

}