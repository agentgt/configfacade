package org.configfacade;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;
import java.util.Map;

import org.configfacade.Config.Property;
import org.junit.Test;

import com.google.common.collect.Lists;

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
		assertEquals(321,c.getInteger("a.b.c").get().intValue());
		
		List<String> keys = Lists.newArrayList(c.getKeys());
		
		assertEquals(asList("a"), keys);
		
		assertEquals(asList("b"), Lists.newArrayList(c.atPath("a").getKeys()));
		
		assertFalse(none.isPresent());
		//ConfigMap m = ConfigFactory.toConfigMap(System.getProperties());
		
		
		//assertEquals("321", p.get());
		
		//out.println(ConfigFactory.prettyPrint(c.atPath("user")));
	}

}
