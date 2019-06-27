[PropertyValues]的定义：
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