package org.configfacade;

import static com.google.common.base.Preconditions.checkState;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.configfacade.ConfigMap.ReplaceableConfigMap;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ObjectArrays;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;

public interface Config extends ReplaceableConfigMap {
	
	public boolean hasPath(String path);
	
	public Config atPath(String path);
	
	public Property<String> getString(String path);
	
	public Property<Long> getLong(String path);
	
	public Property<Integer> getInteger(String path);
	
	public Property<Boolean> getBoolean(String path);
	
	public Property<Double> getDouble(String path);
	
	/**
	 * Gets paths that only have a single element. Basically have no '.'.
	 * @return
	 */
	public Iterable<String> getKeys();
	
	public Iterable<String> getPaths();
	
	public String getCurrentPath();
	
	public Properties toProperties();
	
	public Map<String, ? extends Object> toMap();
	
	public void replace(ConfigMap configMap);
	
	public Config withFallback(ConfigMap config);

	
	public <T> Property<T> getProperty(
			final String path, Type pt);

	public <T> Property<T> getProperty(
			final String path, 
			final Class<T> c);
	

	public interface TypeMatcher<CONTEXT, PATH, RETURN> {
		public RETURN onString(CONTEXT context, PATH path);
		public RETURN onBoolean(CONTEXT context, PATH path);
		public RETURN onLong(CONTEXT context, PATH path);
		public RETURN onDouble(CONTEXT context, PATH path);
		public RETURN onInteger(CONTEXT context, PATH path);
	}
		
	public interface PropertyTypeMatcher extends TypeMatcher<ConfigMap, String, Property<?>> {
		
	}
	
	public interface TypeMatchable {
		public <C, P, R> R match(TypeMatcher<C, P, R> matcher, C c, P p);
	}
	
	public enum Type implements TypeMatchable {
		
		STRING(String.class) {
			@Override
			public <C, P, R> R match(TypeMatcher<C, P, R> matcher, C c, P p) {
				return matcher.onString(c,p);
			}
		},
		BOOLEAN(Boolean.TYPE) {
			@Override
			public <C, P, R> R match(TypeMatcher<C, P, R> matcher, C c, P p) {
				return matcher.onBoolean(c,p);
			}
		},
		INTEGER(Integer.TYPE) {
			@Override
			public <C, P, R> R match(TypeMatcher<C, P, R> matcher, C c, P p) {
				return matcher.onInteger(c,p);
			}
		},
		LONG(Long.TYPE) { 
			@Override
			public <C, P, R> R match(TypeMatcher<C, P, R> matcher, C c, P p) {
				return matcher.onLong(c,p);
			}
		},
		DOUBLE(Double.TYPE) {
			@Override
			public <C, P, R> R match(TypeMatcher<C, P, R> matcher, C c, P p) {
				return matcher.onDouble(c,p);
			}
		};

		private final Class<?> type;
		
		private Type(Class<?> type) {
			this.type = type;
		}
		public boolean isPropertyType(java.lang.reflect.Type c) {
			return type == c; // ||  type.isAssignableFrom(c);
		}
		public static Type findPropertyType(java.lang.reflect.Type c) {
			for (Type pt : Type.values()) {
				if (pt.isPropertyType(c))
					return pt;
			}
			return null;
		}
		
		public Class<?> getType() {
			return type;
		}
		public abstract <C, P, R> R match(TypeMatcher<C, P, R> matcher, C c, P p);

	}

	

	public abstract class Property<T> implements Supplier<T> {
		
		public boolean isPresent() {
			return optional().isPresent();
		}
		
		@Override
		public T get() {
			Optional<? extends T> o = optional();
			checkState(o.isPresent(), "Property is not present: '" + getKey() + "'");
			return o.get();
		}
		
		public abstract String getKey();
		protected abstract Optional<? extends T> optional() throws RuntimeException;
		@SuppressWarnings("unchecked") // Ok because optional is readonly
		public Optional<T> toOptional() {
			return (Optional<T>) optional();
		}
		public void addListener(FutureCallback<? super T> callback) {
			runListener(callback);
		}
		
		protected final void runListener(FutureCallback<? super T> callback) {
			T t;
			try {
				t = get();
			}
			catch(Exception e) {
				callback.onFailure(e);
				return;
			}
			callback.onSuccess(t);
		}
		
		public Property<T> backup() {
			Property<T> p = backup(this);
			p.get();
			return p;
		}
		
		public Property<T> cache() {
			return cache(this);
		}
		
		public T orValue(T t) {
			return or(t).get();
		}
		
		
		public Property<T> or(T t) {
			Property<? extends T> p = new StaticProperty<T>(getKey(), Optional.<T>of(t));
			return or(p);
		}
		
		//TODO varags?
		public Property<T> or(Supplier<Optional<? extends T>> supplier) {
			Property<? extends T> p = new SupplierProperty<T>(getKey(), supplier);
			return or(p);
		}
		
		
		@SafeVarargs //Because Property is read only this is ok.
		public final  Property<T> or(Property<? extends T> ... property) {
			Property<? extends T>[] props = ObjectArrays.concat(this, property);
			return new ChainedProperty<T>(getKey(), props);
		}
		
		public  Supplier<Optional<? extends T>> supplier() {
			return new Supplier<Optional<? extends T>>() {
				@Override
				public Optional<? extends T> get() {
					return optional();
				}
			};
		}
		
		public static <T> Property<T> of(String key, T value) {
			return new StaticProperty<T>(key, Optional.fromNullable(value));
		}
		
		public static <T> Property<T> of(String key, Supplier<Optional<? extends T>> supplier) {
			return new SupplierProperty<>(key, supplier);
		}
		
		public static <T> Property<T> backup(final Property<T> property) {
			return new BackupProperty<>(property);
		}
		
		private static class BackupProperty<T> extends ForwardingProperty<T> {

			private volatile Optional<? extends T> fallback = null;

			public BackupProperty(Property<T> delegate) {
				super(delegate);
			}
			
			@Override
			public Optional<? extends T> optional() {
				Optional<? extends T> o = delegate.optional();
				Optional<? extends T> f = fallback;
				if (o.isPresent()) {
					fallback = o;
				}
				else if (f != null) {
					return f;
				}
				return o;
			}
		}
		
		public static <T> Property<T> cache(final Property<T> property) {
			return new CachedProperty<>(property);
		}
		
		private static class CachedProperty<T> extends ForwardingProperty<T> {
			private volatile Optional<? extends T> cached = null;

			public CachedProperty(Property<T> delegate) {
				super(delegate);
				delegate.addListener(new FutureCallback<T>() {
					@Override
					public void onSuccess(T result) {
						invalidate();
					}
					@Override
					public void onFailure(Throwable t) {
						invalidate();
					}
				});
			}
			
			@Override
			public Optional<? extends T> optional() {
				Optional<? extends T> c = cached;
				if (c == null) {
					c = delegate.optional();
					cached = c;
				}
				return c;
			}
			
			public void invalidate() {
				cached = null;
			}
			
		}
		
		private static class ForwardingProperty<T> extends Property<T> {

			protected final Property<T> delegate;
			
			public ForwardingProperty(Property<T> delegate) {
				super();
				this.delegate = delegate;
			}

			@Override
			public String getKey() {
				return delegate.getKey();
			}

			@Override
			public Optional<? extends T> optional() {
				return delegate.optional();
			}
			
			@Override
			public void addListener(FutureCallback<? super T> callback) {
				delegate.addListener(callback);
			}
			
		}
		
		public static <T> Property<T> absent(String key) {
			return new StaticProperty<T>(key, Optional.<T>absent());
		}
		
		private static class StaticProperty<T> extends Property<T>  {
			private final String key;
			private final Optional<T> value;

			public StaticProperty(String key, Optional<T> value) {
				super();
				this.key = key;
				this.value = value;
			}
			@Override
			public Optional<T> optional() {
				return this.value;
			}
			@Override
			public String getKey() {
				return this.key;
			}
		}
		
		private static class ChainedProperty<T> extends Property<T> {
			private final String key;
			private final Property<? extends T>[] properties;
			private final CallbackExecutionList<T> callbackList = new CallbackExecutionList<T>();
			private final AtomicBoolean callbacks = new AtomicBoolean();

			public ChainedProperty(final String key, Property<? extends T>[] properties) {
				super();
				this.key = key;
				this.properties = properties;
			}
			
			@Override
			public Optional<? extends T> optional() {
				if (properties.length == 0) return Optional.absent();
				int i = 0;
				for (; i < properties.length - 1; i++) {
					try {
						Optional<? extends T> o = properties[i].optional();
						if (o.isPresent()) return o;
					}
					catch (RuntimeException e) {
						continue;
					}
				}
				return properties[i].optional();
			}
			@Override
			public String getKey() {
				return this.key;
			}
			@Override
			public void addListener(FutureCallback<? super T> callback) {
				super.addListener(callback);
				if(callbacks.compareAndSet(false, true)) {
					FutureCallback<T> dispatcher = new FutureCallback<T>() {
						@Override
						public void onSuccess(T result) {
							runListener(callbackList);
						}
						@Override
						public void onFailure(Throwable t) {
							runListener(callbackList);
						}
					};
					for(Property<? extends T> p : properties) {
						p.addListener(dispatcher);
					}
				}
				callbackList.add(callback, MoreExecutors.sameThreadExecutor());
			}
		}
		
		private static class SupplierProperty<T> extends Property<T> {
			
			private final String key;
			private final Supplier<Optional<? extends T>> supplier;
			
			public SupplierProperty(String key, Supplier<Optional<? extends T>> supplier) {
				super();
				this.key = key;
				this.supplier = supplier;
			}

			@Override
			public Optional<? extends T> optional() {
				return supplier.get();
			}
			
			@Override
			public String getKey() {
				return this.key;
			}
		}
	}
	

}
