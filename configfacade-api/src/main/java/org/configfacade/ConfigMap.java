package org.configfacade;

public interface ConfigMap {

    public Object get(String key);

    public Iterable<String> getKeys();

    public boolean containsKey(String key);

    public interface ReplaceableConfigMap extends ConfigMap {

        public void replace(ConfigMap m);
    }
}