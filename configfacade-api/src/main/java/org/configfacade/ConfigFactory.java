package org.configfacade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.configfacade.ConfigMap.ReplaceableConfigMap;

import com.google.common.base.Converter;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

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
        return new VolatileMapLike(m);
    }

    private static class VolatileMapLike implements ReplaceableConfigMap {

        private volatile ConfigMap map;

        public VolatileMapLike(ConfigMap map) {
            super();
            this.map = map;
        }

        public void replace(ConfigMap map) {
            this.map = map;
        }

        @Override
        public Object get(String key) {
            return map.get(key);
        }

        @Override
        public Iterable<String> getKeys() {
            return map.getKeys();
        }

        @Override
        public boolean containsKey(String key) {
            return map.containsKey(key);
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
        public Iterable<String> getKeys() {
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
            return getProperty(path, String.class, Converter.<String> identity());
        }

        @Override
        public Property<Long> getLong(String path) {
            return getProperty(path, Long.class, Longs.stringConverter());
        }

        @Override
        public Property<Boolean> getBoolean(String path) {
            return getProperty(path, Boolean.class, booleanConverter);
        }

        @Override
        public Property<Integer> getInteger(String path) {
            return getProperty(path, Integer.class, Ints.stringConverter());
        }

        @Override
        public Iterable<String> getKeys() {
            return Iterables.filter(getPaths(), new Predicate<String>() {

                @Override
                public boolean apply(String input) {
                    return !input.contains(".");
                }
            });
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
            Iterable<String> f = Iterables.filter(map.getKeys(), new Predicate<String>() {

                @Override
                public boolean apply(String input) {
                    return isKeyMatch(input);
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

        private boolean isKeyMatch(String s) {
            final String p = getCurrentPath();
            return !p.equals(s) && startsWith(s, p);
        }

        public <T> Property<T> getProperty(final String path, final Class<T> c,
                final Converter<String, ? extends T> converter) {
            return new Property<T>() {

                @Override
                public Optional<T> optional() {
                    return Optional.fromNullable(getValue(path, c, converter));
                }
            };
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
        public String toString() {
            return MoreObjects.toStringHelper(Config.class).add("basePath", basePath).addValue(map).toString();
        }

        @Override
        public void replace(ConfigMap configMap) {
            this.map.replace(configMap);
        }

        @Override
        public Object get(String key) {
            return this.map.get(getCurrentPath() + key);
        }

        @Override
        public boolean containsKey(String key) {
            return this.map.get(getCurrentPath() + key) != null;
        }

    }

    private abstract static class PrefixMap implements Map<String, Object> {

        protected abstract ConfigMap delegate();

        protected abstract String prefix();

        @Override
        public int size() {
            final String p = prefix();
            int i = 0;
            for (String k : delegate().getKeys()) {
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
            for (String k : delegate().getKeys()) {
                if (isKeyMatch(k)) {
                    s.add(removeStart(k, prefix()));
                }
            }
            return s;
        }

        @Override
        public Collection<Object> values() {
            Iterable<String> i = Iterables.filter(delegate().getKeys(), new Predicate<String>() {

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

    private static String validatePath(String path) {
        checkNotNull(path, "Path should not be null");
        checkArgument(!endsWith(path, "."), "Path should not end with a '.' ");
        checkArgument(!startsWith(path, "."), "Path should not start with a '.' ");
        return path;
    }

}
