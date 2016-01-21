# configfacade - the slf4j for configuration
 configfacade serves as a simple facade or abstraction for various Java configuration frameworks such as
 [typesafe config](https://github.com/typesafehub/config),
 [commons-configuration](https://commons.apache.org/proper/commons-configuration/), and
 [Netflix Archaius](https://github.com/Netflix/archaius).

The aim is to make configfacade a shared and decoupled API for configuration
in a similar manner to [slf4j](http://www.slf4j.org/) for logging.

## Status

 * In discovery and feedback mode. Nothing released to maven central yet. TODO Javadoc 
 * [Archaius 2 API](https://github.com/Netflix/archaius/tree/2.x) maybe a better feature rich facade. Unfortunately it is not
   released yet. For now this library is an easier copy'n paste.

## Goals

### Agnostic of source and Read only

In an effort to keep the API simple to implement backing implementations the API does not have any
modification or resource loading methods.

A simple `ConfigMap` interface is all that backing implementations need to implement.
A default implementation is provided that can use `Map<String,?>` and `java.util.Properties`.

Property conversion should be done by the backing implementation.

### Static and Dynamic properties

The API provides a `Property` object that will always pull the latest similar to Archaius `DynamicProperty`.

### Minimal dependencies and no static initialization

The only dependency is Guava at the moment and unlike other frameworks configfacade
does not rely on a logging framework and thus will not perform an static initialization.

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

See the unit tests for more examples.
