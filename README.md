# configfacade - the slf4j for configuration
 configfacade serves as a simple facade or abstraction for various Java configuration frameworks such as
 [typesafe config](https://github.com/typesafehub/config),
 [commons-configuration](https://commons.apache.org/proper/commons-configuration/), and
 [Netflix Archaius](https://github.com/Netflix/archaius).

The aim is to make configfacade a shared and decoupled API for configuration
in a similar manner to [slf4j](http://www.slf4j.org/) for logging.


# Goals

## Agnostic of source

A simple `ConfigMap` interface is all that backing implementations need to implement.
A default implementation is provided that can ready `Map<String,?>` and `java.util.Properties`.

## Mainly Read Only

In an effort to keep the API simple to implement backing implementations the API does not have any
modification or loading methods.

## Static and Dynamic properties

The API provides a `Property` object that will always pull the latest similar to Archaius `DynamicProperty`.

## Minimal dependencies and no static initialization

The only dependency is Guava at the moment and unlike other frameworks configfacade
does not rely on a logging framework and thus will not perform an static initialization.
