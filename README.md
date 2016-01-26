# configfacade - the slf4j for configuration
 configfacade serves as a simple facade or abstraction for various Java configuration frameworks such as
 [typesafe config](https://github.com/typesafehub/config),
 [commons-configuration](https://commons.apache.org/proper/commons-configuration/),
 [Netflix Archaius](https://github.com/Netflix/archaius),
 [cfg4j](https://github.com/cfg4j/cfg4j) and
 [OWNER](https://github.com/lviggiano/owner).

The aim is to make configfacade a shared and decoupled API for configuration
in a similar manner to [slf4j](http://www.slf4j.org/) for logging. It does not aim to replace
the above feature rich libraries.

## Status

 * In discovery and feedback mode. Nothing released to maven central yet. TODO Javadoc
 * [Archaius 2 API](https://github.com/Netflix/archaius/tree/2.x) maybe a better feature rich facade. Unfortunately it is not
   released yet. For now this library is an easier copy'n paste.

## Goals

### Agnostic of source and Read only

A simple `ConfigMap` interface is all that backing implementations need to implement.
A default implementation is provided that can use `Map<String,?>` and `java.util.Properties`.

In an effort to keep the API simple to implement backing implementations the API does not offer any modification or
resource loading methods. Property conversion and String interpolation should be done by the backing implementation.
Many backing implementations already have a great deal of complexity devoted towards this but this is not important to a
library developer that is a consumer of the configuration.

### Static and Dynamic properties

The API provides a `Property` object that will always pull the latest similar to Archaius `DynamicProperty`.

### Minimal dependencies and no static initialization

The only dependency is Guava at the moment and unlike other frameworks configfacade
does **not rely on a logging framework/fascade and thus will not perform any logging static initialization**.
Thus you can use library to preconfigure logging or any bootstrap configuration.

## Features

The library is modeled after many of the existing configuration libraries.

 * Dynamic Properties with listeners - (Archaius)
 * Minimal Interface binding - (OVERT and cfg4j)
 * Narrow in by config path - (typesafe config and Archaius)
 * Prefers to avoid `null` - (typesafe config)
 * Support for Guava `Suppliers` and `Optional` as properties
 * Read only and agnostic of source

## Example Usage

```java
Map<String, Object> o =
        newLinkedHashMap(); // could also be java.util.Properties or an immutablemap
o.put("zone1.db.user", "admin");
o.put("zone1.db.host", "127.0.0.1");
o.put("zone1.db.port", "1111");

Config config = ConfigFactory.fromMap(o);

Config db = config.atPath("zone1.db");
String user = db.getString("user").get();
assertEquals("admin", user);

int port = db.getInteger("port").get().intValue();
assertEquals(1111, port);

Property<String> host = db.getString("host"); // a lazy dynamic property

assertEquals(host.get(), "127.0.0.1");

o.put("zone1.db.host", "changed");

assertEquals(host.get(), "changed");

//Concerned with performance but still want dynamic properties.. You can cache the Property using Guava

Supplier<String> h = Suppliers.memoizeWithExpiration(host, 5, TimeUnit.SECONDS);
```

See the unit tests for more examples including interface binding.
