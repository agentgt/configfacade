package org.configfacade;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static java.lang.System.out;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.configfacade.Config.Property;
import org.configfacade.ConfigFactory.BindConfig;
import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;

public class ConfigFactoryTest {

    @Test
    public void test() {
        Map<String, Object> o = newLinkedHashMap();
        o.put("a.b.c", "123");
        o.put("a.b", "12");
        o.put("a", "Hello");

        Config c = ConfigFactory.fromMap(o);
        Property<String> p = c.getString("a.b.c");
        Property<String> pa = c.atPath("a").getString("b.c");
        Property<String> pab = c.atPath("a.b").getString("c");
        Property<String> none = c.atPath("none").getString("c");

        assertEquals("123", p.get());
        assertEquals("123", pa.get());
        assertEquals("123", pab.get());

        o.put("a.b.c", "321");
        assertEquals("321", p.get());
        assertEquals("321", pa.get());
        assertEquals("321", pab.get());
        assertEquals(321, c.getInteger("a.b.c").get().intValue());

        List<String> keys = Lists.newArrayList(c.getKeys());

        assertEquals(asList("a"), keys);

        assertEquals(asList("b"), Lists.newArrayList(c.atPath("a").getKeys()));

        assertFalse(none.isPresent());
        // ConfigMap m = ConfigFactory.toConfigMap(System.getProperties());

        // c.replace(m);

        // assertEquals("321", p.get());

        // out.println(ConfigFactory.prettyPrint(c.atPath("user")));
    }

    @Test
    public void testBind() throws Exception {
        Map<String, Object> o = newLinkedHashMap();
        o.put("host", "localhost");
        o.put("port", 2);
        o.put("auto", true);
        Config c = ConfigFactory.fromMap(o);

        Example e = ConfigFactory.bind(c, Example.class);

        assertEquals(2, e.getPort());
        String host = e.getHost().get();
        assertEquals("localhost", host);
        assertEquals(true, e.isAuto());
        try {
            e.getUser();
            fail("user");
        } catch (IllegalStateException se) {
        }

        BindConfig bc = new BindConfig();
        bc.setAllowMissing(true);
        e = ConfigFactory.bind(c, Example.class, bc);
        assertNull(e.getUser());
        assertTrue(!e.alias().isPresent());
        o.put("alias", "demo");
        assertEquals("demo", e.alias().get());

    }

    @Test
    public void testFromBean() throws Exception {
        Example e = new Example() {

            @Override
            public Supplier<String> getHost() {
                return Suppliers.ofInstance("hello");
            }

            @Override
            public int getPort() {
                return 90;
            }

            @Override
            public String getUser() {
                return "admin";
            }

            @Override
            public boolean isAuto() {
                return true;
            }

            @Override
            public Optional<String> alias() {
                return Optional.of("demo");
            }
        };
        Config c = ConfigFactory.fromBean(Example.class, e);
        assertEquals("hello", c.getString("host").get());
        assertEquals(90, c.getInteger("port").get().intValue());
        assertEquals(true, c.getBoolean("auto").get().booleanValue());
        assertEquals("demo", c.getString("alias").get());

    }

    @Test
    public void testListener() throws Exception {
        Map<String, Object> o = newLinkedHashMap();
        o.put("host", "localhost");
        o.put("port", 2);
        Config c = ConfigFactory.fromMap(o);
        final AtomicInteger count = new AtomicInteger();
        c.getString("host").addListener(new FutureCallback<String>() {

            @Override
            public void onSuccess(String result) {
                out.println("success: " + result);
                count.incrementAndGet();
            }

            @Override
            public void onFailure(Throwable t) {
                out.println("Error: " + t);
            }
        });
        Property<String> backup = c.getString("host").backup();
        Property<String> cached = c.getString("host").cache();
        Property<String> both = c.getString("host").cache().backup();

        assertEquals("localhost", cached.get());
        assertEquals("localhost", backup.get());
        assertEquals("localhost", both.get());

        o.put("host", "backup");
        assertEquals("localhost", cached.get());
        assertEquals("localhost", both.get());
        assertEquals("backup", backup.get());

        c.replace(ConfigFactory.toConfigMap(newLinkedHashMap()));
        assertEquals("backup", backup.get());
        assertTrue(!cached.isPresent());
        assertEquals("localhost", both.get());

        o.put("host", "changed");
        c.replace(ConfigFactory.toConfigMap(o));
        assertEquals("changed", backup.get());
        assertEquals("changed", both.get());

    }

    public interface Example {

        public Supplier<String> getHost();

        public int getPort();

        public String getUser();

        public boolean isAuto();

        public Optional<String> alias();
    }

}
