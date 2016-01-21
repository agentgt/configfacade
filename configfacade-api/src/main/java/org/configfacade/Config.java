package org.configfacade;

import java.util.Map;
import java.util.Properties;

import org.configfacade.ConfigMap.ReplaceableConfigMap;

import com.google.common.base.Converter;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;

public interface Config extends ReplaceableConfigMap {
	
	public boolean hasPath(String path);
	
	public Config atPath(String path);
	
	public Property<String> getString(String path);
	
	public Property<Long> getLong(String path);
	
	public Property<Integer> getInteger(String path);
	
	public Property<Boolean> getBoolean(String path);
	
	public Iterable<String> getKeys();
	
	public Iterable<String> getPaths();
	
	public String getCurrentPath();
	
	public Properties toProperties();
	
	public Map<String, ? extends Object> toMap();
	
	public void replace(ConfigMap configMap);

	
	public <T> Property<T> getProperty(
			final String path, 
			final Class<T> c, 
			final Converter<String, ? extends T> converter);
	


	public abstract class Property<T> implements Supplier<T> {
		
		public boolean isPresent() {
			return optional().isPresent();
		}
		
		@Override
		public T get() {
			return optional().get();
		}
		
		public abstract Optional<T> optional();
		
		@SuppressWarnings("unchecked") //Because Property is read only this is ok.
		public Property<T> or(Property<? extends T> ... property) {
			return new ChainedProperty<T>(property);
		}
		
		public  Supplier<Optional<T>> supplier() {
			return new Supplier<Optional<T>>() {
				@Override
				public Optional<T> get() {
					return optional();
				}
			};
		}
		
		public static <T> Property<T> of(T value) {
			return new InstanceDynamicProperty<T>(value);
		}
		
		public static <T> Property<T> of(Supplier<Optional<T>> supplier) {
			return new SupplierProperty<>(supplier);
		}
		
		
		@SuppressWarnings("unchecked")
		public static <T> Property<T> absent() {
			return (Property<T>) _missingProperty;
		}
		
		private static class InstanceDynamicProperty<T> extends Property<T>  {
			private final Optional<T> value;

			public InstanceDynamicProperty(T value) {
				super();
				this.value = Optional.of(value);
			}
			@Override
			public Optional<T> optional() {
				return this.value;
			}
		}
		
		private static final  MissingProperty<Object> _missingProperty = new MissingProperty<Object>();
		private static class MissingProperty<T> extends Property<T> {
			@Override
			public Optional<T> optional() {
				return Optional.absent();
			}
			
		}
		
		private static class ChainedProperty<T> extends Property<T> {
			private final Property<? extends T>[] properties;

			public ChainedProperty(Property<? extends T>[] properties) {
				super();
				this.properties = properties;
			}
			
			@Override
			public Optional<T> optional() {
				for(Property<? extends T> p : properties) {
					@SuppressWarnings("unchecked")
					Optional<T> o = (Optional<T>) p.optional();
					if (o.isPresent()) return o;
				}
				return Optional.absent();
			}
		}
		
		private static class SupplierProperty<T> extends Property<T> {
			
			private final Supplier<Optional<T>> supplier;
			
			public SupplierProperty(Supplier<Optional<T>> supplier) {
				super();
				this.supplier = supplier;
			}

			@Override
			public Optional<T> optional() {
				return supplier.get();
			}
		}
	}
	

}
