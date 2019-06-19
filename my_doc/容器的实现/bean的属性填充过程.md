## 如何设置XML中的字符串值到Bean的属性上

属性填充分两步，一步是获取属性值，一步是转换属性值为期望值，对于XML配置文件，属性值一般直接定义在XML中，如：
```java
public class TestPropertyPopulateBean {
	private String name;
	private Integer num;
	private Date time;
	private MyBeanA myBeanA;
	private MyBeanB myBeanB;
	private List<String> stringList;
	private String[] stringArray;
	private Map<String, String> stringMap;

	public TestPropertyPopulateBean(String name, MyBeanA myBeanA) {
		this.name = name;
		this.myBeanA = myBeanA;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getNum() {
		return num;
	}

	public void setNum(Integer num) {
		this.num = num;
	}

	public Date getTime() {
		return time;
	}

	public void setTime(Date time) {
		this.time = time;
	}

	public MyBeanA getMyBeanA() {
		return myBeanA;
	}

	public void setMyBeanA(MyBeanA myBeanA) {
		this.myBeanA = myBeanA;
	}

	public MyBeanB getMyBeanB() {
		return myBeanB;
	}

	public void setMyBeanB(MyBeanB myBeanB) {
		this.myBeanB = myBeanB;
	}

	public List<String> getStringList() {
		return stringList;
	}

	public void setStringList(List<String> stringList) {
		this.stringList = stringList;
	}

	public String[] getStringArray() {
		return stringArray;
	}

	public void setStringArray(String[] stringArray) {
		this.stringArray = stringArray;
	}

	public Map<String, String> getStringMap() {
		return stringMap;
	}

	public void setStringMap(Map<String, String> stringMap) {
		this.stringMap = stringMap;
	}
}

public class MyBeanA {
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

public class MyBeanB {
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

import org.springframework.beans.propertyeditors.CustomDateEditor;
public class CustomDateEditorRegistrar implements PropertyEditorRegistrar {
	@Override
	public void registerCustomEditors(PropertyEditorRegistry registry) {
		registry.registerCustomEditor(Date.class, new CustomDateEditor(new SimpleDateFormat("yyyy-MM-dd"), true));
	}
}
```

XML配置：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
	   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	   xsi:schemaLocation="http://www.springframework.org/schema/beans
	   http://www.springframework.org/schema/beans/spring-beans.xsd">
	<bean id="testPropertyPopulateBean"
			class="org.springframework.tests.sample.beans.property.TestPropertyPopulateBean" autowire="byType">
		<constructor-arg name="name" value="test"/>
		<constructor-arg name="myBeanA" ref="myBeanA"/>
		<property name="num" value="20"/>
		<property name="time" value="2019-06-16"/>
		<property name="stringList">
			<list>
				<value>A</value>
				<value>B</value>
				<value>C</value>
			</list>
		</property>
		<property name="stringArray">
			<array>
				<value>A</value>
				<value>B</value>
				<value>C</value>
			</array>
		</property>
		<property name="stringMap">
			<map>
				<entry key="keyA" value="valueA"/>
				<entry key="keyB" value="valueB"/>
				<entry key="keyC" value="valueC"/>
			</map>
		</property>
		<property name="myBeanA.name" value="tttt"/>
	</bean>

	<bean id="myBeanA" class="org.springframework.tests.sample.beans.property.MyBeanA">
		<property name="name" value="myBeanA"/>
	</bean>

	<bean id="myBeanB" class="org.springframework.tests.sample.beans.property.MyBeanB">
		<property name="name" value="myBeanB"/>
	</bean>

	<bean class="org.springframework.beans.factory.config.CustomEditorConfigurer">
		<property name="propertyEditorRegistrars">
			<bean class="org.springframework.tests.sample.beans.property.CustomDateEditorRegistrar"/>
		</property>
	</bean>
</beans>
```

测试代码：
```java
@Test
public void testPropertyPopulate() {
	ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
	TestPropertyPopulateBean testPropertyPopulateBean = applicationContext.getBean("testPropertyPopulateBean", TestPropertyPopulateBean.class);
	System.out.println(testPropertyPopulateBean.getName());
	System.out.println(testPropertyPopulateBean.getNum());
	System.out.println(testPropertyPopulateBean.getTime());
	System.out.println(testPropertyPopulateBean.getMyBeanA().getName());
	System.out.println(testPropertyPopulateBean.getMyBeanB().getName());
	System.out.println(testPropertyPopulateBean.getStringList());
	System.out.println(Arrays.toString(testPropertyPopulateBean.getStringArray()));
	System.out.println(testPropertyPopulateBean.getStringMap());
}

/*
输出：
test
20
Sun Jun 16 00:00:00 CST 2019
tttt
myBeanB
[A, B, C]
[A, B, C]
{keyA=valueA, keyB=valueB, keyC=valueC}
*/
```
上述的例子有构造函数的属性注入和普通成员变量的属性注入，注入的属性有普通的字符串、数字、集合、无法直接转换的Date类型属性和引用其他bean的属性。下面分析spring是如何设置上这些属性的，以下内容假设已经看过笔记[容器的初始化过程](../容器的实现/容器的初始化过程.md)和[从容器获取Bean](../容器的实现/从容器获取Bean.md)

bean的属性注入发生在初始化bean时执行的`populateBean()`方法中，代码：
```java
protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
	if (bw == null) {
		if (mbd.hasPropertyValues()) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
		}
		else {
			// Skip property population phase for null instance.
			return;
		}
	}

	// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
	// state of the bean before properties are set. This can be used, for example,
	// to support styles of field injection.
	boolean continueWithPropertyPopulation = true;

	// 给InstantiationAwareBeanPostProcessors机会在设置属性之前改变bean
	if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
				if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
					continueWithPropertyPopulation = false;
					break;
				}
			}
		}
	}

	// 如果postProcessAfterInstantiation返回false则停止自动注入属性的过程
	if (!continueWithPropertyPopulation) {
		return;
	}

	// 获取所有从XML的property元素解析到的属性
	// 可能是RuntimeBeanReference或者是TypedStringValue
	PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

	// 判断属性的注入方式，通过在XML配置bean时设置autowire属性指定，如果设置了autowire则尝试自动注入bean的属性(即使没有在属性上声明注解)
	if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
			mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
		MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

		// Add property values based on autowire by name if applicable.
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {
			// 根据属性名称获取bean，获取到的bean保存在newPvs中
			autowireByName(beanName, mbd, bw, newPvs);
		}

		// Add property values based on autowire by type if applicable.
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
			// 根据属性类型获取bean，获取到的bean保存在newPvs中
			autowireByType(beanName, mbd, bw, newPvs);
		}

		pvs = newPvs;
	}

	boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
	boolean needsDepCheck = (mbd.getDependencyCheck() != RootBeanDefinition.DEPENDENCY_CHECK_NONE);

	// 如果存在InstantiationAwareBeanPostProcessors并且当前bean没有禁用依赖检查，则执行postProcessPropertyValues方法
	if (hasInstAwareBpps || needsDepCheck) {
		if (pvs == null) {
			pvs = mbd.getPropertyValues();
		}
		PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
		if (hasInstAwareBpps) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					pvs = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
					if (pvs == null) {
						return;
					}
				}
			}
		}
		if (needsDepCheck) {
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}
	}

	// 设置属性到bean中
	if (pvs != null) {
		applyPropertyValues(beanName, mbd, bw, pvs);
	}
}
```

`populateBean()`方法将属性注入分为两步，一步是获取所有的属性，也就是`PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null)`和该语句下面的`autowireByName()`与`autowireByType`，一步是`applyPropertyValues()`方法，将属性设置到bean中，所以首先需要分析的是获取到的[PropertyValues]有什么用，和[PropertyValues]如何获取的。下面是[PropertyValues]的定义：
```java
public interface PropertyValues {
	PropertyValue[] getPropertyValues();

	@Nullable
	PropertyValue getPropertyValue(String propertyName);

	PropertyValues changesSince(PropertyValues old);

	boolean contains(String propertyName);

	boolean isEmpty();

}
```

[PropertyValues]的一个重要实现是[MutablePropertyValues]，作用是维护[PropertyValue]列表，即保存某个bean的所有属性，代码：
```java
@SuppressWarnings("serial")
public class MutablePropertyValues implements PropertyValues, Serializable {

	// PropertyValue被作为当个属性使用
	private final List<PropertyValue> propertyValueList;

	@Nullable
	private Set<String> processedProperties;

	private volatile boolean converted = false;


	public MutablePropertyValues() {
		this.propertyValueList = new ArrayList<>(0);
	}

	public MutablePropertyValues(@Nullable PropertyValues original) {
		// We can optimize this because it's all new:
		// There is no replacement of existing property values.
		if (original != null) {
			PropertyValue[] pvs = original.getPropertyValues();
			this.propertyValueList = new ArrayList<>(pvs.length);
			for (PropertyValue pv : pvs) {
				this.propertyValueList.add(new PropertyValue(pv));
			}
		}
		else {
			this.propertyValueList = new ArrayList<>(0);
		}
	}

	public MutablePropertyValues(@Nullable Map<?, ?> original) {
		// We can optimize this because it's all new:
		// There is no replacement of existing property values.
		if (original != null) {
			this.propertyValueList = new ArrayList<>(original.size());
			original.forEach((attrName, attrValue) -> this.propertyValueList.add(
					new PropertyValue(attrName.toString(), attrValue)));
		}
		else {
			this.propertyValueList = new ArrayList<>(0);
		}
	}

	public MutablePropertyValues(@Nullable List<PropertyValue> propertyValueList) {
		this.propertyValueList =
				(propertyValueList != null ? propertyValueList : new ArrayList<>());
	}


	public List<PropertyValue> getPropertyValueList() {
		return this.propertyValueList;
	}

	public int size() {
		return this.propertyValueList.size();
	}

	// 从其他PropertyValues添加PropertyValue
	public MutablePropertyValues addPropertyValues(@Nullable PropertyValues other) {
		if (other != null) {
			PropertyValue[] pvs = other.getPropertyValues();
			for (PropertyValue pv : pvs) {
				addPropertyValue(new PropertyValue(pv));
			}
		}
		return this;
	}

	public MutablePropertyValues addPropertyValues(@Nullable Map<?, ?> other) {
		if (other != null) {
			other.forEach((attrName, attrValue) -> addPropertyValue(
					new PropertyValue(attrName.toString(), attrValue)));
		}
		return this;
	}

	// 添加单个PropertyValue
	public MutablePropertyValues addPropertyValue(PropertyValue pv) {
		for (int i = 0; i < this.propertyValueList.size(); i++) {
			PropertyValue currentPv = this.propertyValueList.get(i);
			if (currentPv.getName().equals(pv.getName())) {
				// 如果pv是可merge的并且开启了merge则合并两个PropertyValue的值
				pv = mergeIfRequired(pv, currentPv);
				setPropertyValueAt(pv, i);
				return this;
			}
		}
		this.propertyValueList.add(pv);
		return this;
	}

	public void addPropertyValue(String propertyName, Object propertyValue) {
		addPropertyValue(new PropertyValue(propertyName, propertyValue));
	}

	public MutablePropertyValues add(String propertyName, @Nullable Object propertyValue) {
		addPropertyValue(new PropertyValue(propertyName, propertyValue));
		return this;
	}

	public void setPropertyValueAt(PropertyValue pv, int i) {
		this.propertyValueList.set(i, pv);
	}

	private PropertyValue mergeIfRequired(PropertyValue newPv, PropertyValue currentPv) {
		Object value = newPv.getValue();
		// 如果newPv是可merge的则合并两个PropertyValue的值
		if (value instanceof Mergeable) {
			Mergeable mergeable = (Mergeable) value;
			if (mergeable.isMergeEnabled()) {
				Object merged = mergeable.merge(currentPv.getValue());
				return new PropertyValue(newPv.getName(), merged);
			}
		}
		return newPv;
	}

	public void removePropertyValue(PropertyValue pv) {
		this.propertyValueList.remove(pv);
	}

	public void removePropertyValue(String propertyName) {
		this.propertyValueList.remove(getPropertyValue(propertyName));
	}


	@Override
	public PropertyValue[] getPropertyValues() {
		return this.propertyValueList.toArray(new PropertyValue[0]);
	}

	@Override
	@Nullable
	public PropertyValue getPropertyValue(String propertyName) {
		for (PropertyValue pv : this.propertyValueList) {
			if (pv.getName().equals(propertyName)) {
				return pv;
			}
		}
		return null;
	}

	@Nullable
	public Object get(String propertyName) {
		PropertyValue pv = getPropertyValue(propertyName);
		return (pv != null ? pv.getValue() : null);
	}
	
	@Override
	// 将当前PropertyValues的PropertyValue中不存在于传入的PropertyValues的添加到返回值返回
	public PropertyValues changesSince(PropertyValues old) {
		MutablePropertyValues changes = new MutablePropertyValues();
		if (old == this) {
			return changes;
		}

		// for each property value in the new set
		for (PropertyValue newPv : this.propertyValueList) {
			// if there wasn't an old one, add it
			PropertyValue pvOld = old.getPropertyValue(newPv.getName());
			if (pvOld == null || !pvOld.equals(newPv)) {
				changes.addPropertyValue(newPv);
			}
		}
		return changes;
	}

	@Override
	public boolean contains(String propertyName) {
		return (getPropertyValue(propertyName) != null ||
				(this.processedProperties != null && this.processedProperties.contains(propertyName)));
	}

	@Override
	public boolean isEmpty() {
		return this.propertyValueList.isEmpty();
	}


	/**
	 * Register the specified property as "processed" in the sense
	 * of some processor calling the corresponding setter method
	 * outside of the PropertyValue(s) mechanism.
	 * <p>This will lead to {@code true} being returned from
	 * a {@link #contains} call for the specified property.
	 * @param propertyName the name of the property.
	 */
	public void registerProcessedProperty(String propertyName) {
		if (this.processedProperties == null) {
			this.processedProperties = new HashSet<>(4);
		}
		this.processedProperties.add(propertyName);
	}

	/**
	 * Clear the "processed" registration of the given property, if any.
	 * @since 3.2.13
	 */
	public void clearProcessedProperty(String propertyName) {
		if (this.processedProperties != null) {
			this.processedProperties.remove(propertyName);
		}
	}

	/**
	 * Mark this holder as containing converted values only
	 * (i.e. no runtime resolution needed anymore).
	 */
	public void setConverted() {
		this.converted = true;
	}

	/**
	 * Return whether this holder contains converted values only ({@code true}),
	 * or whether the values still need to be converted ({@code false}).
	 */
	public boolean isConverted() {
		return this.converted;
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MutablePropertyValues)) {
			return false;
		}
		MutablePropertyValues that = (MutablePropertyValues) other;
		return this.propertyValueList.equals(that.propertyValueList);
	}

	@Override
	public int hashCode() {
		return this.propertyValueList.hashCode();
	}

	@Override
	public String toString() {
		PropertyValue[] pvs = getPropertyValues();
		StringBuilder sb = new StringBuilder("PropertyValues: length=").append(pvs.length);
		if (pvs.length > 0) {
			sb.append("; ").append(StringUtils.arrayToDelimitedString(pvs, "; "));
		}
		return sb.toString();
	}

}
```

[PropertyValues]作用只是维护一组[PropertyValue]，[PropertyValue]是bean的某个属性的具体实例，将bean的属性和bean解耦，使得属性可以单独存在，[PropertyValue]继承结构为：
![PropertyValue继承结构](../img/PropertyValue.png)

[AttributeAccessor]接口定义了属性的访问方法
```java
public interface AttributeAccessor {
	void setAttribute(String name, @Nullable Object value);

	@Nullable
	Object getAttribute(String name);

	@Nullable
	Object removeAttribute(String name);

	boolean hasAttribute(String name);

	String[] attributeNames();
}
```

[AttributeAccessorSupport]抽象类实现了[AttributeAccessor]接口的方法，用map保存属性名称和属性值
```java
@SuppressWarnings("serial")
public abstract class AttributeAccessorSupport implements AttributeAccessor, Serializable {
	private final Map<String, Object> attributes = new LinkedHashMap<>(0);

	@Override
	public void setAttribute(String name, @Nullable Object value) {
		Assert.notNull(name, "Name must not be null");
		if (value != null) {
			this.attributes.put(name, value);
		}
		else {
			removeAttribute(name);
		}
	}

	@Override
	@Nullable
	public Object getAttribute(String name) {
		Assert.notNull(name, "Name must not be null");
		return this.attributes.get(name);
	}

	@Override
	@Nullable
	public Object removeAttribute(String name) {
		Assert.notNull(name, "Name must not be null");
		return this.attributes.remove(name);
	}

	@Override
	public boolean hasAttribute(String name) {
		Assert.notNull(name, "Name must not be null");
		return this.attributes.containsKey(name);
	}

	@Override
	public String[] attributeNames() {
		return StringUtils.toStringArray(this.attributes.keySet());
	}

	// 从另一个AttributeAccessor复制属性
	protected void copyAttributesFrom(AttributeAccessor source) {
		Assert.notNull(source, "Source must not be null");
		String[] attributeNames = source.attributeNames();
		for (String attributeName : attributeNames) {
			setAttribute(attributeName, source.getAttribute(attributeName));
		}
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AttributeAccessorSupport)) {
			return false;
		}
		AttributeAccessorSupport that = (AttributeAccessorSupport) other;
		return this.attributes.equals(that.attributes);
	}

	@Override
	public int hashCode() {
		return this.attributes.hashCode();
	}
}
```

[BeanMetadataElement]接口定义了获取配置源的方法，如对于XML配置，元素的配置源就是其在XML中的配置元素对应的[Element]对象，可以返回null
```java
public interface BeanMetadataElement {
	@Nullable
	Object getSource();
}
```

[BeanMetadataAttributeAccessor]类主要是利用[AttributeAccessorSupport]保存属性，并将属性值定义为[BeanMetadataAttribute]保存在[AttributeAccessorSupport]的map中，[BeanMetadataAttribute]类的作用是维护属性名和属性值还有属性的配置源
```java
public class BeanMetadataAttribute implements BeanMetadataElement {

	private final String name;

	@Nullable
	private final Object value;

	@Nullable
	private Object source;

	public BeanMetadataAttribute(String name, @Nullable Object value) {
		Assert.notNull(name, "Name must not be null");
		this.name = name;
		this.value = value;
	}


	public String getName() {
		return this.name;
	}

	@Nullable
	public Object getValue() {
		return this.value;
	}

	public void setSource(@Nullable Object source) {
		this.source = source;
	}

	@Override
	@Nullable
	public Object getSource() {
		return this.source;
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BeanMetadataAttribute)) {
			return false;
		}
		BeanMetadataAttribute otherMa = (BeanMetadataAttribute) other;
		return (this.name.equals(otherMa.name) &&
				ObjectUtils.nullSafeEquals(this.value, otherMa.value) &&
				ObjectUtils.nullSafeEquals(this.source, otherMa.source));
	}

	@Override
	public int hashCode() {
		return this.name.hashCode() * 29 + ObjectUtils.nullSafeHashCode(this.value);
	}

	@Override
	public String toString() {
		return "metadata attribute '" + this.name + "'";
	}
}

@SuppressWarnings("serial")
public class BeanMetadataAttributeAccessor extends AttributeAccessorSupport implements BeanMetadataElement {

	@Nullable
	private Object source;

	public void setSource(@Nullable Object source) {
		this.source = source;
	}

	@Override
	@Nullable
	public Object getSource() {
		return this.source;
	}


	public void addMetadataAttribute(BeanMetadataAttribute attribute) {
		super.setAttribute(attribute.getName(), attribute);
	}

	@Nullable
	public BeanMetadataAttribute getMetadataAttribute(String name) {
		return (BeanMetadataAttribute) super.getAttribute(name);
	}

	@Override
	public void setAttribute(String name, @Nullable Object value) {
		super.setAttribute(name, new BeanMetadataAttribute(name, value));
	}

	@Override
	@Nullable
	public Object getAttribute(String name) {
		BeanMetadataAttribute attribute = (BeanMetadataAttribute) super.getAttribute(name);
		return (attribute != null ? attribute.getValue() : null);
	}

	@Override
	@Nullable
	public Object removeAttribute(String name) {
		BeanMetadataAttribute attribute = (BeanMetadataAttribute) super.removeAttribute(name);
		return (attribute != null ? attribute.getValue() : null);
	}
}
```

[PropertyValue]主要是用于维护单个bean的属性值，[BeanMetadataAttributeAccessor]是有保存多个属性的能力的，之所以没有维护所有的属性，是因为单个属性的实现使用起来更灵活，以及能够以优化的方式处理索引属性。
```java
@SuppressWarnings("serial")
public class PropertyValue extends BeanMetadataAttributeAccessor implements Serializable {

	private final String name;

	@Nullable
	// 保存未转换的值
	private final Object value;

	// 属性是否是Optional类型的
	private boolean optional = false;

	// 是否已经转换过
	private boolean converted = false;

	@Nullable
	// 保存转换后值
	private Object convertedValue;

	/** Package-visible field that indicates whether conversion is necessary */
	@Nullable
	// 是否需要转换
	volatile Boolean conversionNecessary;

	/** Package-visible field for caching the resolved property path tokens */
	@Nullable
	transient volatile Object resolvedTokens;


	public PropertyValue(String name, @Nullable Object value) {
		Assert.notNull(name, "Name must not be null");
		this.name = name;
		this.value = value;
	}

	public PropertyValue(PropertyValue original) {
		Assert.notNull(original, "Original must not be null");
		this.name = original.getName();
		this.value = original.getValue();
		this.optional = original.isOptional();
		this.converted = original.converted;
		this.convertedValue = original.convertedValue;
		this.conversionNecessary = original.conversionNecessary;
		this.resolvedTokens = original.resolvedTokens;
		setSource(original.getSource());
		copyAttributesFrom(original);
	}

	public PropertyValue(PropertyValue original, @Nullable Object newValue) {
		Assert.notNull(original, "Original must not be null");
		this.name = original.getName();
		this.value = newValue;
		this.optional = original.isOptional();
		this.conversionNecessary = original.conversionNecessary;
		this.resolvedTokens = original.resolvedTokens;
		setSource(original);
		copyAttributesFrom(original);
	}

	public String getName() {
		return this.name;
	}

	@Nullable
	public Object getValue() {
		return this.value;
	}

	public PropertyValue getOriginalPropertyValue() {
		PropertyValue original = this;
		Object source = getSource();
		while (source instanceof PropertyValue && source != original) {
			original = (PropertyValue) source;
			source = original.getSource();
		}
		return original;
	}

	public void setOptional(boolean optional) {
		this.optional = optional;
	}

	public boolean isOptional() {
		return this.optional;
	}

	public synchronized boolean isConverted() {
		return this.converted;
	}

	public synchronized void setConvertedValue(@Nullable Object value) {
		this.converted = true;
		this.convertedValue = value;
	}

	@Nullable
	public synchronized Object getConvertedValue() {
		return this.convertedValue;
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PropertyValue)) {
			return false;
		}
		PropertyValue otherPv = (PropertyValue) other;
		return (this.name.equals(otherPv.name) &&
				ObjectUtils.nullSafeEquals(this.value, otherPv.value) &&
				ObjectUtils.nullSafeEquals(getSource(), otherPv.getSource()));
	}

	@Override
	public int hashCode() {
		return this.name.hashCode() * 29 + ObjectUtils.nullSafeHashCode(this.value);
	}

	@Override
	public String toString() {
		return "bean property '" + this.name + "'";
	}
}
```

bean的属性填充过程先获取了bean的[BeanDefinition]的[PropertyValues]，[BeanDefinition]的[PropertyValues]是在容器初始化过程中创建[BeanFactory]后解析资源文件时创建的，对于XML的资源文件，解析并创建[BeanDefinition]的过程在[BeanDefinitionParserDelegate]的`parseBeanDefinitionElement()`方法，代码：
```java
@Nullable
public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {
	// 获取id属性
	String id = ele.getAttribute(ID_ATTRIBUTE);
	// 获取name属性
	String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

	List<String> aliases = new ArrayList<>();
	// 以,或者;为分隔符，nameAttr属性即指定了beanName也指定了alias，nameAttr也可为空
	if (StringUtils.hasLength(nameAttr)) {
		String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
		aliases.addAll(Arrays.asList(nameArr));
	}

	String beanName = id;
	// 如果id为空则使用name为beanName，否则以id为beanName
	if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
		// 以别名的第一个名称为beanName，并将该名称从别名中移除
		beanName = aliases.remove(0);
		if (logger.isDebugEnabled()) {
			logger.debug("No XML 'id' specified - using '" + beanName +
					"' as bean name and " + aliases + " as aliases");
		}
	}

	// 判断beanName和所有的别名是否已经被使用过
	if (containingBean == null) {
		checkNameUniqueness(beanName, aliases, ele);
	}

	// AbstractBeanDefinition类表示XML中的<bean>元素，包含了bean的所有相关信息
	AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
	if (beanDefinition != null) {
		// 如果即没有指定id也没有指定name，则beanName为空，这里创建一个别名
		if (!StringUtils.hasText(beanName)) {
			try {
				// 根据Spring的命名规则创建beanName，默认是className + 一些其他字符串，具体可以看generateBeanName方法注释
				if (containingBean != null) {
					beanName = BeanDefinitionReaderUtils.generateBeanName(
							beanDefinition, this.readerContext.getRegistry(), true);
				}
				else {
					// 如果不是内嵌bean则委托给readerContext创建，而readerContext是调用的DefaultBeanNameGenerator创建的
					// DefaultBeanNameGenerator又是调用BeanDefinitionReaderUtils.generateBeanName(beanDefinition, this.readerContext.getRegistry(), false)
					beanName = this.readerContext.generateBeanName(beanDefinition);
					// Register an alias for the plain bean class name, if still possible,
					// if the generator returned the class name plus a suffix.
					// This is expected for Spring 1.2/2.0 backwards compatibility.
					String beanClassName = beanDefinition.getBeanClassName();
					// 如果当前beanName不等于className则新增一个className的别名
					if (beanClassName != null &&
							beanName.startsWith(beanClassName) && beanName.length() > beanClassName.length() &&
							!this.readerContext.getRegistry().isBeanNameInUse(beanClassName)) {
						aliases.add(beanClassName);
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Neither XML 'id' nor 'name' specified - " +
							"using generated bean name [" + beanName + "]");
				}
			}
			catch (Exception ex) {
				error(ex.getMessage(), ele);
				return null;
			}
		}
		String[] aliasesArray = StringUtils.toStringArray(aliases);
		// BeanDefinitionHolder维护了传入构造函数的三个属性，供之后的注册bean信息使用
		return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
	}

	return null;
}

@Nullable
public AbstractBeanDefinition parseBeanDefinitionElement(
		Element ele, String beanName, @Nullable BeanDefinition containingBean) {

	// 保存当前解析状态，用于发生异常时显示解析状态
	this.parseState.push(new BeanEntry(beanName));

	String className = null;
	// 获取className
	if (ele.hasAttribute(CLASS_ATTRIBUTE)) {
		className = ele.getAttribute(CLASS_ATTRIBUTE).trim();
	}
	// 获取parent
	String parent = null;
	if (ele.hasAttribute(PARENT_ATTRIBUTE)) {
		parent = ele.getAttribute(PARENT_ATTRIBUTE);
	}

	try {
		// 初始化AbstractBeanDefinition，包含了parentName和class，创建出来的是GenericBeanDefinition类型，spring中
		// 有3中BeanDefinition，分别是ChildBeanDefinition: 表示存在父bean的BeanDefinition、GenericBeanDefinition: 一站式的BeanDefinition，保存了parentName属性(如果有的话)
		// RootBeanDefinition: 表示普通的bean，这3中BeanDefinition都继承自AbstractBeanDefinition，AbstractBeanDefinition已经实现了大部分BeanDefinition需要的功能，
		// ChildBeanDefinition只是多了校验parentName不为空的逻辑(重写validate方法)，GenericBeanDefinition定义了parentName属性、RootBeanDefinition由一个或多个BeanDefinition
		// 合并而来(子bean合并父bean的属性之后才能作为一个单独的bean)，是容器中一个具体bean的BeanDefinition视图。
		AbstractBeanDefinition bd = createBeanDefinition(className, parent);

		// 根据element获取beanDefinition属性，如autowire、destroyMethod、scope、abstract等所有bean标签上的属性
		parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);
		bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DESCRIPTION_ELEMENT));

		//保存meta数据，meta属性不会影响bean的创建，当需要获取meta时通过beanDefinition.getAttribute(key)获取
		parseMetaElements(ele, bd);
		/*
		解析lookup方法，将该方法的相关属性添加到对象并保存到bd.getMethodOverrides()中，lookup方法代理获取对象的方法，返回一个指定的bean，如
		<lookup-method name="getFruit" bean="apple"/>这样的声明存在于某个bean声明中的话表示该bean的getFruit方法将会返回bean apple，
		常用的使用场景是:
		假设一个单例模式的bean A需要引用另外一个非单例模式的bean B，为了在每次引用的时候都能拿到最新的bean B，可以让bean A通过实现ApplicationContextWare来
		获取applicationContext(即可以获得容器上下文)，从而能在运行时通过ApplicationContext.getBean(String beanName)的方法来获取最新的bean B，
		但是如果用ApplicationContextAware接口，就与Spring代码耦合了，违背了反转控制原则(IoC，即bean完全由Spring容器管理，我们代码只需要用bean就可以了)，
		所以Spring为我们提供了方法注入的方式来实现以上的场景。方法注入方式主要是通过<lookup-method/>标签，Spring通过CGLIB代理了包含lookup-method的bean，
		被代理的方法如上面是getFruit可以是抽象方法，被代理的bean也可以是抽象bean，一般被返回的bean如上面的apple的scope是prototype的，如果是singleton的话每次返回的都是
		同一个bean，那样lookup-method的意义就不大了，当然apple是singleton也不会报错。spring实现lookup-method的地方是在创建bean时AbstractAutowireCapableBeanFactory
		在调用instantiateBean实例化bean的时候判断当前创建的bean是否存在MethodOverrides，如果存在MethodOverrides则使用CGLIB创建代理bean
		*/
		parseLookupOverrideSubElements(ele, bd.getMethodOverrides());
		/*
		解析replaced方法，将该方法的相关属性也是保存到bd.getMethodOverrides()中，replaced方法替代某个方法的执行，如
		<bean id="person" class="test.replaced.Person">
							<replaced-method name="show" replacer="replace"></replaced-method>
				</bean>

				<bean id="replace" class="test.replaced.ReplacedClass"></bean>

				ReplacedClass实现了MethodReplacer接口，该接口只有一个方法，表示将取代其他方法的方法:
				Object reimplement(Object obj, Method method, Object[] args) throws Throwable
		*/
		parseReplacedMethodSubElements(ele, bd.getMethodOverrides());

		// 解析构造函数
		parseConstructorArgElements(ele, bd);
		// 解析property
		parsePropertyElements(ele, bd);
		// 解析qualifier元素，qualifier用于指定注入其他bean时设置需要的bean的名字，这样就能在由多个bean满足注入条件的情况下
		// 选择一个特定的bean
		parseQualifierElements(ele, bd);

		bd.setResource(this.readerContext.getResource());
		bd.setSource(extractSource(ele));

		return bd;
	}
	catch (ClassNotFoundException ex) {
		error("Bean class [" + className + "] not found", ele, ex);
	}
	catch (NoClassDefFoundError err) {
		error("Class that bean class [" + className + "] depends on not found", ele, err);
	}
	catch (Throwable ex) {
		error("Unexpected failure during bean definition parsing", ele, ex);
	}
	finally {
		this.parseState.pop();
	}

	return null;
}
```

解析bean属性的方法在`parsePropertyElements()`方法，代码：
```java
public void parsePropertyElements(Element beanEle, BeanDefinition bd) {
	NodeList nl = beanEle.getChildNodes();
	for (int i = 0; i < nl.getLength(); i++) {
		Node node = nl.item(i);
		if (isCandidateElement(node) && nodeNameEquals(node, PROPERTY_ELEMENT)) {
			parsePropertyElement((Element) node, bd);
		}
	}
}

public void parsePropertyElement(Element ele, BeanDefinition bd) {
	String propertyName = ele.getAttribute(NAME_ATTRIBUTE);
	if (!StringUtils.hasLength(propertyName)) {
		error("Tag 'property' must have a 'name' attribute", ele);
		return;
	}
	// 保存当前解析状态，用于发生异常时显示解析状态
	this.parseState.push(new PropertyEntry(propertyName));
	try {
		if (bd.getPropertyValues().contains(propertyName)) {
			error("Multiple 'property' definitions for property '" + propertyName + "'", ele);
			return;
		}
		// 返回property对应的值，如果是ref的属性则返回RuntimeBeanReference，其他返回TypedStringValue
		Object val = parsePropertyValue(ele, bd, propertyName);
		PropertyValue pv = new PropertyValue(propertyName, val);
		parseMetaElements(ele, pv);
		// 提取source，默认直接返回null(NullSourceExtractor)，另一个实现类PassThroughSourceExtractor返回
		// 传入的Element即这里的ele
		pv.setSource(extractSource(ele));
		// getPropertyValues由AbstractBeanDefinition实现，判断当前BeanDefinition的propertyValues是否为空，
		// 为空则创建一个MutablePropertyValues并返回
		bd.getPropertyValues().addPropertyValue(pv);
	}
	finally {
		this.parseState.pop();
	}
}
```

`parsePropertyElement`方法遍历`<property>`元素，创建对应的[PropertyValue]并添加到[BeanDefinition]的[PropertyValues]中，而[BeanDefinition]的[PropertyValues]默认实现是[MutablePropertyValues]，[BeanDefinition]的`getPropertyValues()`方法代码：
```java
public MutablePropertyValues getPropertyValues() {
	if (this.propertyValues == null) {
		this.propertyValues = new MutablePropertyValues();
	}
	return this.propertyValues;
}
```

上面解析属性值的方法为`parsePropertyValue()`，代码：
```java
@Nullable
public Object parsePropertyValue(Element ele, BeanDefinition bd, @Nullable String propertyName) {
	String elementName = (propertyName != null) ?
					"<property> element for property '" + propertyName + "'" :
					"<constructor-arg> element";

	// Should only have one child element: ref, value, list, etc.
	/*
	对于
	<property name="stringList">
		<list>
			<value>A</value>
			<value>B</value>
			<value>C</value>
		</list>
	</property>
	这种配置，getChildNodes方法返回的就是<property>元素，假设解析的就是上面的配置，这里首先获取<property>元素，之后
	在for循环中获取<property>元素的所有子元素，正常情况下，只会有一个元素，如ref, value, list等

	对于<property name="num" value="20"/>这种配置，getChildNodes方法直接返回<property>元素，并且nl.getLength()等于0
	*/
	NodeList nl = ele.getChildNodes();
	Element subElement = null;
	for (int i = 0; i < nl.getLength(); i++) {
		Node node = nl.item(i);
		if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT) &&
				!nodeNameEquals(node, META_ELEMENT)) {
			// Child element is what we're looking for.
			// 如果存在多个子元素则报错
			if (subElement != null) {
				error(elementName + " must not contain more than one sub-element", ele);
			}
			else {
				subElement = (Element) node;
			}
		}
	}

	// ref和value至少存在一个，或者存在子元素，如果ref、value或子元素存在其中多余一个，则报错
	boolean hasRefAttribute = ele.hasAttribute(REF_ATTRIBUTE);
	boolean hasValueAttribute = ele.hasAttribute(VALUE_ATTRIBUTE);
	if ((hasRefAttribute && hasValueAttribute) ||
			((hasRefAttribute || hasValueAttribute) && subElement != null)) {
		error(elementName +
				" is only allowed to contain either 'ref' attribute OR 'value' attribute OR sub-element", ele);
	}

	// 如果是rel则使用RuntimeBeanReference代表value，在创建bean的时候通过传入的refName查找bean
	if (hasRefAttribute) {
		String refName = ele.getAttribute(REF_ATTRIBUTE);
		if (!StringUtils.hasText(refName)) {
			error(elementName + " contains empty 'ref' attribute", ele);
		}
		RuntimeBeanReference ref = new RuntimeBeanReference(refName);
		ref.setSource(extractSource(ele));
		return ref;
	}
	// 如果是字符串则使用TypedStringValue封装
	else if (hasValueAttribute) {
		TypedStringValue valueHolder = new TypedStringValue(ele.getAttribute(VALUE_ATTRIBUTE));
		valueHolder.setSource(extractSource(ele));
		return valueHolder;
	}
	// 如果存在子元素则解析子元素
	else if (subElement != null) {
		return parsePropertySubElement(subElement, bd);
	}
	else {
		// Neither child element nor "ref" or "value" attribute found.
		error(elementName + " must specify a ref or value", ele);
		return null;
	}
}
```

对于普通的ref或value属性，直接用[RuntimeBeanReference]和[TypedStringValue]表示，如果存在子元素则需要用`parsePropertySubElement()`方法进一步解析，代码：
```java
@Nullable
public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd) {
	return parsePropertySubElement(ele, bd, null);
}

@Nullable
// 解析<property>元素的子元素
public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd, @Nullable String defaultValueType) {
	if (!isDefaultNamespace(ele)) {
		return parseNestedCustomElement(ele, bd);
	}
	// 如果是元素名为bean则按照BeanDefinition
	else if (nodeNameEquals(ele, BEAN_ELEMENT)) {
		BeanDefinitionHolder nestedBd = parseBeanDefinitionElement(ele, bd);
		if (nestedBd != null) {
			nestedBd = decorateBeanDefinitionIfRequired(ele, nestedBd, bd);
		}
		return nestedBd;
	}
	// 如果是ref则用RuntimeBeanReference表示
	else if (nodeNameEquals(ele, REF_ELEMENT)) {
		// A generic reference to any name of any bean.
		String refName = ele.getAttribute(BEAN_REF_ATTRIBUTE);
		boolean toParent = false;
		if (!StringUtils.hasLength(refName)) {
			// A reference to the id of another bean in a parent context.
			refName = ele.getAttribute(PARENT_REF_ATTRIBUTE);
			toParent = true;
			if (!StringUtils.hasLength(refName)) {
				error("'bean' or 'parent' is required for <ref> element", ele);
				return null;
			}
		}
		if (!StringUtils.hasText(refName)) {
			error("<ref> element contains empty target attribute", ele);
			return null;
		}
		RuntimeBeanReference ref = new RuntimeBeanReference(refName, toParent);
		ref.setSource(extractSource(ele));
		return ref;
	}
	// idref和ref一样，也用RuntimeBeanNameReference表示
	else if (nodeNameEquals(ele, IDREF_ELEMENT)) {
		return parseIdRefElement(ele);
	}
	// value元素用TypedStringValue表示
	else if (nodeNameEquals(ele, VALUE_ELEMENT)) {
		return parseValueElement(ele, defaultValueType);
	}
	else if (nodeNameEquals(ele, NULL_ELEMENT)) {
		// It's a distinguished null value. Let's wrap it in a TypedStringValue
		// object in order to preserve the source location.
		TypedStringValue nullHolder = new TypedStringValue(null);
		nullHolder.setSource(extractSource(ele));
		return nullHolder;
	}
	// array元素用ManagedArray表示
	else if (nodeNameEquals(ele, ARRAY_ELEMENT)) {
		return parseArrayElement(ele, bd);
	}
	// list元素用ManagedList表示
	else if (nodeNameEquals(ele, LIST_ELEMENT)) {
		return parseListElement(ele, bd);
	}
	// set元素用ManagedSet表示
	else if (nodeNameEquals(ele, SET_ELEMENT)) {
		return parseSetElement(ele, bd);
	}
	// map元素用ManagedMap表示
	else if (nodeNameEquals(ele, MAP_ELEMENT)) {
		return parseMapElement(ele, bd);
	}
	// props元素用ManagedProperties表示
	else if (nodeNameEquals(ele, PROPS_ELEMENT)) {
		return parsePropsElement(ele);
	}
	else {
		error("Unknown property sub-element: [" + ele.getNodeName() + "]", ele);
		return null;
	}
}
```

解析array、list等元素的过程是一样的，以array元素的解析过程为例，代码：
```java
public Object parseArrayElement(Element arrayEle, @Nullable BeanDefinition bd) {
	// 从value-type属性获取元素值的类型，可以为空
	String elementType = arrayEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
	NodeList nl = arrayEle.getChildNodes();
	// 以ManagedArray保存数组类型的属性
	ManagedArray target = new ManagedArray(elementType, nl.getLength());
	target.setSource(extractSource(arrayEle));
	target.setElementTypeName(elementType);
	// merge属性表示如果存在parent beanDefinition，则parent beanDefinition的同名的array元素是否需要合并到当前array元素中
	target.setMergeEnabled(parseMergeAttribute(arrayEle));
	// 解析集合子元素
	parseCollectionElements(nl, target, bd, elementType);
	return target;
}

protected void parseCollectionElements(
		NodeList elementNodes, Collection<Object> target, @Nullable BeanDefinition bd, String defaultElementType) {

	// 遍历子元素并递归调用parsePropertySubElement方法解析
	for (int i = 0; i < elementNodes.getLength(); i++) {
		Node node = elementNodes.item(i);
		if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT)) {
			target.add(parsePropertySubElement((Element) node, bd, defaultElementType));
		}
	}
}
```

以上是[BeanDefinition]的[PropertyValues]的由来，现在回到`populateBean()`方法继续bean的属性填充过程，获取到[PropertyValues]后，执行的是autowire逻辑，代码：
```java
protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
	//...

	// 获取所有从XML的property元素解析到的属性
	// 可能是RuntimeBeanReference或者是TypedStringValue
	PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

	// 判断属性的注入方式，通过在XML配置bean时设置autowire属性指定，如果设置了autowire则尝试自动注入bean的属性(即使没有在属性上声明注解)
	if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
			mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
		MutablePropertyValues newPvs = new MutablePropertyValues(pvs);

		// Add property values based on autowire by name if applicable.
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {
			// 根据属性名称获取bean，获取到的bean保存在newPvs中
			autowireByName(beanName, mbd, bw, newPvs);
		}

		// Add property values based on autowire by type if applicable.
		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
			// 根据属性类型获取bean，获取到的bean保存在newPvs中
			autowireByType(beanName, mbd, bw, newPvs);
		}

		pvs = newPvs;
	}

	//...
}
```

如果在bean上声明了autowire属性，则根据配置执行不同的注入逻辑，如根据autowire="byName"，则根据beanName注入，代码：
```java
protected void autowireByName(
		String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

	// 获取满足自动注入条件的属性名称
	String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
	for (String propertyName : propertyNames) {
		// 如果存在该属性名称的bean则自动注入
		if (containsBean(propertyName)) {
			Object bean = getBean(propertyName);
			// 将自动注入的bean暂时保存在pvs，后面再set到当前创建的bean中
			pvs.add(propertyName, bean);
			// 注册当前bean依赖自动注入的bean的依赖关系
			registerDependentBean(propertyName, beanName);
			if (logger.isDebugEnabled()) {
				logger.debug("Added autowiring by name from bean name '" + beanName +
						"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
						"' by name: no matching bean found");
			}
		}
	}
}
```

`unsatisfiedNonSimpleProperties()`方法用于获取需要自动注入的属性，代码：
```java
// 获取满足自动注入条件的属性名称
protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
	Set<String> result = new TreeSet<>();
	PropertyValues pvs = mbd.getPropertyValues();
	PropertyDescriptor[] pds = bw.getPropertyDescriptors();
	for (PropertyDescriptor pd : pds) {
		// 满足自动注入的属性需要的条件：存在setter方法、不包含在ignoredDependencyInterfaces和ignoredDependencyTypes中、没有配置在XML的property元素中、不是基础属性如String、Array等
		// ignoredDependencyInterfaces和ignoredDependencyTypes配置在BeanFactory中，AbstractApplicationContext在启动时就为其BeanFactory
		// 添加了多个ignoredDependencyInterface
		if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
				!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
			result.add(pd.getName());
		}
	}
	return StringUtils.toStringArray(result);
}
```

`autowireByName()`方法的逻辑很简单，获取需要注入的属性列表，遍历并根据属性名获取bean，再添加到[MutablePropertyValues]中待后面设置到bean上，`autowireByType()`方法则更复杂点，代码：
```java

```













Spring中的属性转换是通过[PropertyEditor]实现的，[PropertyEditor]是`java.beans`包下的，该接口主要方法如下:
1.`Object getValue()`: 返回属性的当前值。基本类型被封装成对应的封装类实例。
1.`void setValue(Object newValue)`: 设置属性的值，基本类型以封装类传入。
1.`String getAsText()`: 将属性对象用一个字符串表示，以便外部的属性编辑器能以可视化的方式显示。缺省返回null，表示该属性不能以字符串表示。
1.`void setAsText(String text)`: 用一个字符串去更新属性的内部值，这个字符串一般从外部属性编辑器传入。
1.`String[] getTags()`: 返回表示有效属性值的字符串数组(如boolean属性对应的有效Tag为true和false)，以便属性编辑器能以下拉框的方式显示出来。缺省返回null，表示属性没有匹配的字符值有限集合。
1.`String getJavaInitializationString()`: 为属性提供一个表示初始值的字符串，属性编辑器以此值作为属性的默认值。
JDK还提供了[PropertyEditorSupport]类实现了[PropertyEditor]的默认功能，Spring大部分默认属性编辑器都直接扩展于[PropertyEditorSupport]类。
想要实现一个自己的[PropertyEditor]只需要继承[PropertyEditorSupport]并重写`setAsText()`和`getAsText()`方法即可，如:
```
public class UUIDEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        if (StringUtils.hasText(text)) {
            setValue(UUID.fromString(text));
        }
        else {
            setValue(null);
        }
    }

    @Override
    public String getAsText() {
        UUID value = (UUID) getValue();
        return (value != null ? value.toString() : "");
    }
}
```
之后在XML中将自定义的[PropertyEditor]添加到[CustomEditorConfigurer]中，如下:
```
<bean id="customEditorConfigurer" class="org.springframework.beans.factory.config.CustomEditorConfigurer">
	<property name="customEditors">
	    <map>
	    <entry key="java.util.UUID">
		<bean class="com.example.UUIDEditor"/>
		</entry>
		</map>
	</property>
</bean>
```
[CustomEditorConfigurer]实现了[BeanFactoryPostProcessor]接口在容器初始化完成后遍历`customEditors`属性并全部添加到[BeanFactory]中，或者同样用[CustomEditorConfigurer]的`propertyEditorRegistrars`属性:
```
<bean id="customEditorConfigurer" class="org.springframework.beans.factory.config.CustomEditorConfigurer">
	<property name="propertyEditorRegistrars">
		<bean class="org.springframework.jmx.export.CustomDateEditorRegistrar"/>
	</property>
</bean>
```
添加[PropertyEditorRegistrar]接口的实现类到`propertyEditorRegistrars`，[CustomEditorConfigurer]会通过`postProcessBeanFactory`方法添加[PropertyEditorRegistrar]到[BeanFactory]中，
容器在启动时执行`prepareBeanFactory`方法时会默认添加[ResourceEditorRegistrar]到[BeanFactory]，使用`customEditors`注册[PropertyEditor]和使用[propertyEditorRegistrars]注册[PropertyEditorRegistrar]区别不大，[PropertyEditor]是直接添加到[BeanFactory]的`customEditors`属性中，
而[PropertyEditorRegistrar]是添加到[BeanFactory]的`propertyEditorRegistrars`属性中，在初始化一个bean时，Spring会创建一个[BeanWrapper]，默认实现是[BeanWrapperImpl]，类图如下:
![BeanWrapperImpl继承结构图](../img/BeanWrapperImpl.png)
[PropertyEditorRegistry]接口定义了关联Class和[PropertyEditor]的方法，[PropertyEditorRegistrySupport]实现了[PropertyEditorRegistry]接口并添加了`getDefaultEditor`方法，默认添加了多个[PropertyEditor]到其`defaultEditors`属性中(`defaultEditors`不同于`customEditors`，获取`customEditors`时不会获取`defaultEditors`)。
[TypeConverter]接口定义了将Object转化为指定的类的方法，[TypeConverterSupport]使用[TypeConverterDelegate]类作为代理实现了[TypeConverter]接口，[PropertyAccessor]接口定义了访问bean的属性的若干方法，如判断属性是否可读/写，获取属性值、属性类型和属性的[TypeDescriptor]，设置属性值等。
[ConfigurablePropertyAccessor]接口添加了设置[ConversionService]、`extractOldValueForEditor`和`autoGrowNestedPaths`的方法，[AbstractPropertyAccessor]抽象类实现了其实现的接口的getter/setter方法，并处理了set属性时属性值为[PropertyValues]的情况(获取[PropertyValue]列表并依次执行set)，具体的设置属性的方法为抽象方法。
[AbstractNestablePropertyAccessor]抽象类实现了上述接口的方法，[BeanWrapper]接口定义了获取[BeanWrapper]包装的bean及bean的[PropertyDescriptor]的方法。[BeanWrapperImpl]类实现了[BeanWrapper]接口和[AbstractNestablePropertyAccessor]抽象类。
初始化`bean`时创建[BeanWrapper]的地方是在[AbstractAutowireCapableBeanFactory]的`instantiateBean`方法中，代码如下:
```
protected BeanWrapper instantiateBean(final String beanName, final RootBeanDefinition mbd) {
    ...
    BeanWrapper bw = new BeanWrapperImpl(beanInstance);
    initBeanWrapper(bw);
    ...
}

protected void initBeanWrapper(BeanWrapper bw) {
	bw.setConversionService(getConversionService());
	registerCustomEditors(bw);
}
```
`initBeanWrapper`方法将[BeanWrapper]作为[PropertyEditorRegistry]传入`registerCustomEditors`方法中，`registerCustomEditors`方法代码如下:
```
protected void registerCustomEditors(PropertyEditorRegistry registry) {
	PropertyEditorRegistrySupport registrySupport =
			(registry instanceof PropertyEditorRegistrySupport ? (PropertyEditorRegistrySupport) registry : null);
	if (registrySupport != null) {
		registrySupport.useConfigValueEditors();
	}
	//遍历propertyEditorRegistrars将BeanWrapper传入并执行registerCustomEditors
	if (!this.propertyEditorRegistrars.isEmpty()) {
		for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
			try {
				registrar.registerCustomEditors(registry);
			}
			catch (BeanCreationException ex) {
				Throwable rootCause = ex.getMostSpecificCause();
				if (rootCause instanceof BeanCurrentlyInCreationException) {
					BeanCreationException bce = (BeanCreationException) rootCause;
					String bceBeanName = bce.getBeanName();
					if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
						if (logger.isDebugEnabled()) {
							logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
									"] failed because it tried to obtain currently created bean '" +
									ex.getBeanName() + "': " + ex.getMessage());
						}
						onSuppressedException(ex);
						continue;
					}
				}
				throw ex;
			}
		}
	}
	//遍历customEditors将BeanWrapper传入并执行registerCustomEditor
    if (!this.customEditors.isEmpty()) {
		this.customEditors.forEach((requiredType, editorClass) ->
				registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
	}
}
```
上面说到[BeanWrapperImpl]的父接口[TypeConverterSupport]使用[TypeConverterDelegate]类作为代理实现了[TypeConverter]接口，[BeanWrapperImpl]的构造函数:
```
public BeanWrapperImpl(Object object) {
	super(object);
}

protected AbstractNestablePropertyAccessor(Object object) {
	registerDefaultEditors();
	setWrappedInstance(object);
}

protected void registerDefaultEditors() {
	this.defaultEditorsActive = true;
}
```
默认激活`defaultEditorsActive`属性，`setWrappedInstance`方法初始化了[TypeConverterDelegate]:
```
this.typeConverterDelegate = new TypeConverterDelegate(this, this.wrappedObject);
```
将[BeanWrapperImpl]作为[PropertyEditorRegistrySupport]传入构造函数，Spring在初始化`bean`时如果[BeanFactory]没有设置`typeConverter`属性则将当前`bean`的[BeanWrapperImpl]作为[TypeConverter]，
之后在[AbstractAutowireCapableBeanFactory]的`applyPropertyValues`方法中获取保存在当前`bean`的[BeanDefinition]中的[PropertyValue]并进行属性注入，比如XML中的日期字符串转Date，Spring解析该属性时将其以[TypedStringValue]类进行封装保存到[BeanDefinition]中，
`applyPropertyValues`方法调用`convertForProperty`进行属性转换，最终属性转换会交由[AbstractNestablePropertyAccessor]的`convertIfNecessary`方法完成，该方法代码如下:
```
private Object convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue,
		@Nullable Object newValue, @Nullable Class<?> requiredType, @Nullable TypeDescriptor td)
		throws TypeMismatchException {

	Assert.state(this.typeConverterDelegate != null, "No TypeConverterDelegate");
	try {
		return this.typeConverterDelegate.convertIfNecessary(propertyName, oldValue, newValue, requiredType, td);
	}
	catch (ConverterNotFoundException | IllegalStateException ex) {
		PropertyChangeEvent pce =
				new PropertyChangeEvent(getRootInstance(), this.nestedPath + propertyName, oldValue, newValue);
		throw new ConversionNotSupportedException(pce, requiredType, ex);
	}
	catch (ConversionException | IllegalArgumentException ex) {
		PropertyChangeEvent pce =
				new PropertyChangeEvent(getRootInstance(), this.nestedPath + propertyName, oldValue, newValue);
		throw new TypeMismatchException(pce, requiredType, ex);
	}
}
```
实际的转换由[TypeConverterDelegate]实现，[TypeConverterDelegate]会先获取[BeanWrapperImpl]的`customEditors`，如果不存在指定类型的[PropertyEditor]则获取`defaultEditors`，如果最终的转换结果为String并且和期望的返回值类型不符则保存。

[PropertyEditorRegistrar]: aaa
[BeanMetadataAttribute]: aaa
[Element]: aaa
[AbstractRefreshableApplicationContext]: aaa
[BeanDefinitionParserDelegate]: aaa