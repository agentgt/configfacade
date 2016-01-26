package org.configfacade;

import com.google.common.util.concurrent.FutureCallback;

public interface ConfigMap {

    public Object get(String key);

    public Iterable<String> getRawKeys();

    public boolean containsKey(String key);

    public interface ReplaceableConfigMap extends ConfigMap {

        public void reload();

        public void replace(ConfigMap m);

        public void addListener(FutureCallback<ConfigMap> listener);
    }
}