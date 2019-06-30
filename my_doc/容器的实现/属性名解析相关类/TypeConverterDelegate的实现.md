[TypeConverterDelegate]的作用是将指定值转换为需要的类型，辅助[BeanWrapperImpl]进行属性注入，而实现原理就是利用[PropertyEditorRegistrySupport]中的[PropertyEditor]，或[PropertyEditorRegistrySupport]中的[ConversionService]，进行类型转换，对于[PropertyEditor]可以看笔记[PropertyEditor的实现](PropertyEditor的实现.md)，[ConversionService]可以看笔记[ConversionService的实现](ConversionService的实现.md)

```java
class TypeConverterDelegate {

	private static final Log logger = LogFactory.getLog(TypeConverterDelegate.class);

	private final PropertyEditorRegistrySupport propertyEditorRegistry;

	@Nullable
	private final Object targetObject;


	public TypeConverterDelegate(PropertyEditorRegistrySupport propertyEditorRegistry) {
		this(propertyEditorRegistry, null);
	}

	public TypeConverterDelegate(PropertyEditorRegistrySupport propertyEditorRegistry, @Nullable Object targetObject) {
		this.propertyEditorRegistry = propertyEditorRegistry;
		this.targetObject = targetObject;
	}


	@Nullable
	public <T> T convertIfNecessary(@Nullable Object newValue, @Nullable Class<T> requiredType,
			@Nullable MethodParameter methodParam) throws IllegalArgumentException {

		return convertIfNecessary(null, null, newValue, requiredType,
				(methodParam != null ? new TypeDescriptor(methodParam) : TypeDescriptor.valueOf(requiredType)));
	}

	@Nullable
	public <T> T convertIfNecessary(@Nullable Object newValue, @Nullable Class<T> requiredType, @Nullable Field field)
			throws IllegalArgumentException {

		return convertIfNecessary(null, null, newValue, requiredType,
				(field != null ? new TypeDescriptor(field) : TypeDescriptor.valueOf(requiredType)));
	}

	@Nullable
	public <T> T convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue,
			Object newValue, @Nullable Class<T> requiredType) throws IllegalArgumentException {

		return convertIfNecessary(propertyName, oldValue, newValue, requiredType, TypeDescriptor.valueOf(requiredType));
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T convertIfNecessary(@Nullable String propertyName, @Nullable Object oldValue, @Nullable Object newValue,
			@Nullable Class<T> requiredType, @Nullable TypeDescriptor typeDescriptor) throws IllegalArgumentException {

		// Custom editor for this type?
		// 指定类的指定属性的PropertyEditor
		PropertyEditor editor = this.propertyEditorRegistry.findCustomEditor(requiredType, propertyName);

		ConversionFailedException conversionAttemptEx = null;

		// No custom editor but custom ConversionService specified?
		// 如果没有找到自定义的PropertyEditor并且conversionService不为空时使用conversionService进行转换，一般情况下这里的propertyEditorRegistry
		// 就是BeanWrapper，而BeanWrapper在初始化的时候会设置ConversionService为BeanFactory的conversionService，BeanFactory的conversionService默认为空
		ConversionService conversionService = this.propertyEditorRegistry.getConversionService();
		if (editor == null && conversionService != null && newValue != null && typeDescriptor != null) {
			TypeDescriptor sourceTypeDesc = TypeDescriptor.forObject(newValue);
			if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
				try {
					return (T) conversionService.convert(newValue, sourceTypeDesc, typeDescriptor);
				}
				catch (ConversionFailedException ex) {
					// fallback to default conversion logic below
					conversionAttemptEx = ex;
				}
			}
		}

		Object convertedValue = newValue;

		// Value not of required type?
		// 如果找到了自定义的PropertyEditor或者将被转换的值类型不是需要的类型，则表示需要进行转换
		if (editor != null || (requiredType != null && !ClassUtils.isAssignableValue(requiredType, convertedValue))) {
			// 如果需要的是集合类型并且被转换值是字符串则将被转换值按逗号分隔转换成字符串数组
			if (typeDescriptor != null && requiredType != null && Collection.class.isAssignableFrom(requiredType) &&
					convertedValue instanceof String) {
				// 获取集合的元素类型的TypeDescriptor
				TypeDescriptor elementTypeDesc = typeDescriptor.getElementTypeDescriptor();
				if (elementTypeDesc != null) {
					Class<?> elementType = elementTypeDesc.getType();
					// 如果是枚举或者是Class类型的才进行转换
					if (Class.class == elementType || Enum.class.isAssignableFrom(elementType)) {
						// 按逗号分割字符串，返回字符串数组
						convertedValue = StringUtils.commaDelimitedListToStringArray((String) convertedValue);
					}
				}
			}
			// 如果没有自定义的PropertyEditor则使用defaultEditor
			if (editor == null) {
				editor = findDefaultEditor(requiredType);
			}
			// 用editor进行转换，如果editor为空则只是简单的处理需要的类型不是数组而被转换的值是数组时，将被转换的值转成字符串，元素按逗号分割
			convertedValue = doConvertValue(oldValue, convertedValue, requiredType, editor);
		}

		boolean standardConversion = false;

		// 以下处理被转换的值是集合、map或原始类型等情况
		if (requiredType != null) {
			// Try to apply some standard type conversion rules if appropriate.

			if (convertedValue != null) {
				// 如果需要的类型是Object则直接返回
				if (Object.class == requiredType) {
					return (T) convertedValue;
				}
				else if (requiredType.isArray()) {
					// Array required -> apply appropriate conversion of elements.
					// 如果被转换的值是数组并且需要的返回值是枚举数组，则用逗号分割字符串并进行转换
					if (convertedValue instanceof String && Enum.class.isAssignableFrom(requiredType.getComponentType())) {
						convertedValue = StringUtils.commaDelimitedListToStringArray((String) convertedValue);
					}
					return (T) convertToTypedArray(convertedValue, propertyName, requiredType.getComponentType());
				}
				else if (convertedValue instanceof Collection) {
					// Convert elements to target type, if determined.
					// 如果被转换的值为集合类型，需要的类型也是集合类型，则尝试将被转换的值中的元素逐个转换成需要的集合的元素类型
					convertedValue = convertToTypedCollection(
							(Collection<?>) convertedValue, propertyName, requiredType, typeDescriptor);
					standardConversion = true;
				}
				else if (convertedValue instanceof Map) {
					// Convert keys and values to respective target type, if determined.
					// 如果被转换的值为map，需要的类型也是map，则尝试将被转换的值中的key-value对逐个转换成需要的map的key-value的类型
					convertedValue = convertToTypedMap(
							(Map<?, ?>) convertedValue, propertyName, requiredType, typeDescriptor);
					standardConversion = true;
				}
				if (convertedValue.getClass().isArray() && Array.getLength(convertedValue) == 1) {
					convertedValue = Array.get(convertedValue, 0);
					standardConversion = true;
				}
				// 如果需要的类型是字符串并且被转换的值是boolean、char、int或这些原始类型的包装类，则直接调用toString方法返回
				if (String.class == requiredType && ClassUtils.isPrimitiveOrWrapper(convertedValue.getClass())) {
					// We can stringify any primitive value...
					return (T) convertedValue.toString();
				}
				else if (convertedValue instanceof String && !requiredType.isInstance(convertedValue)) {
					// 如果被转换的值为字符串，则尝试直接用需要的类型的参数为String的构造函数创建返回值
					if (conversionAttemptEx == null && !requiredType.isInterface() && !requiredType.isEnum()) {
						try {
							Constructor<T> strCtor = requiredType.getConstructor(String.class);
							return BeanUtils.instantiateClass(strCtor, convertedValue);
						}
						catch (NoSuchMethodException ex) {
							// proceed with field lookup
							if (logger.isTraceEnabled()) {
								logger.trace("No String constructor found on type [" + requiredType.getName() + "]", ex);
							}
						}
						catch (Exception ex) {
							if (logger.isDebugEnabled()) {
								logger.debug("Construction via String failed for type [" + requiredType.getName() + "]", ex);
							}
						}
					}
					String trimmedValue = ((String) convertedValue).trim();
					// 如果被转换的值为空字符串并且需要的类型是枚举则返回null
					if (requiredType.isEnum() && "".equals(trimmedValue)) {
						// It's an empty enum identifier: reset the enum value to null.
						return null;
					}
					// 如果requiredType是枚举，则尝试将字符串类型的被转换值转换为枚举
					convertedValue = attemptToConvertStringToEnum(requiredType, trimmedValue, convertedValue);
					standardConversion = true;
				}
				else if (convertedValue instanceof Number && Number.class.isAssignableFrom(requiredType)) {
					// 如果是Number类型的则调用NumberUtils进行Number类型之间的转换
					convertedValue = NumberUtils.convertNumberToTargetClass(
							(Number) convertedValue, (Class<Number>) requiredType);
					standardConversion = true;
				}
			}
			else {
				// convertedValue == null
				if (requiredType == Optional.class) {
					convertedValue = Optional.empty();
				}
			}

			// 如果被转换的值的类型和需要的类型不兼容，表示上面的转换都没成功
			if (!ClassUtils.isAssignableValue(requiredType, convertedValue)) {
				// conversionAttemptEx不为空表示上面已经用conversionService转换过了，但是发生异常了，直接返回异常
				if (conversionAttemptEx != null) {
					// Original exception from former ConversionService call above...
					throw conversionAttemptEx;
				}
				// 如果conversionService不为空则尝试用conversionService进行转换
				else if (conversionService != null && typeDescriptor != null) {
					// ConversionService not tried before, probably custom editor found
					// but editor couldn't produce the required type...
					TypeDescriptor sourceTypeDesc = TypeDescriptor.forObject(newValue);
					if (conversionService.canConvert(sourceTypeDesc, typeDescriptor)) {
						return (T) conversionService.convert(newValue, sourceTypeDesc, typeDescriptor);
					}
				}

				// 到这里只能抛出异常了
				// Definitely doesn't match: throw IllegalArgumentException/IllegalStateException
				StringBuilder msg = new StringBuilder();
				msg.append("Cannot convert value of type '").append(ClassUtils.getDescriptiveType(newValue));
				msg.append("' to required type '").append(ClassUtils.getQualifiedName(requiredType)).append("'");
				if (propertyName != null) {
					msg.append(" for property '").append(propertyName).append("'");
				}
				if (editor != null) {
					msg.append(": PropertyEditor [").append(editor.getClass().getName()).append(
							"] returned inappropriate value of type '").append(
							ClassUtils.getDescriptiveType(convertedValue)).append("'");
					throw new IllegalArgumentException(msg.toString());
				}
				else {
					msg.append(": no matching editors or conversion strategy found");
					throw new IllegalStateException(msg.toString());
				}
			}
		}

		// 如果存在conversionAttemptEx并且standardConversion为false表示上面的转换都失败了，则抛出conversionAttemptEx
		if (conversionAttemptEx != null) {
			if (editor == null && !standardConversion && requiredType != null && Object.class != requiredType) {
				throw conversionAttemptEx;
			}
			logger.debug("Original ConversionService attempt failed - ignored since " +
					"PropertyEditor based conversion eventually succeeded", conversionAttemptEx);
		}

		return (T) convertedValue;
	}

	private Object attemptToConvertStringToEnum(Class<?> requiredType, String trimmedValue, Object currentConvertedValue) {
		Object convertedValue = currentConvertedValue;

		if (Enum.class == requiredType && this.targetObject != null) {
			// target type is declared as raw enum, treat the trimmed value as <enum.fqn>.FIELD_NAME
			int index = trimmedValue.lastIndexOf('.');
			if (index > - 1) {
				String enumType = trimmedValue.substring(0, index);
				String fieldName = trimmedValue.substring(index + 1);
				ClassLoader cl = this.targetObject.getClass().getClassLoader();
				try {
					Class<?> enumValueType = ClassUtils.forName(enumType, cl);
					Field enumField = enumValueType.getField(fieldName);
					convertedValue = enumField.get(null);
				}
				catch (ClassNotFoundException ex) {
					if (logger.isTraceEnabled()) {
						logger.trace("Enum class [" + enumType + "] cannot be loaded", ex);
					}
				}
				catch (Throwable ex) {
					if (logger.isTraceEnabled()) {
						logger.trace("Field [" + fieldName + "] isn't an enum value for type [" + enumType + "]", ex);
					}
				}
			}
		}

		if (convertedValue == currentConvertedValue) {
			// Try field lookup as fallback: for JDK 1.5 enum or custom enum
			// with values defined as static fields. Resulting value still needs
			// to be checked, hence we don't return it right away.
			try {
				// 枚举类型还有这种操作，调用getField返回获取元素
                Field enumField = requiredType.getField(trimmedValue);
				ReflectionUtils.makeAccessible(enumField);
				convertedValue = enumField.get(null);
			}
			catch (Throwable ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Field [" + convertedValue + "] isn't an enum value", ex);
				}
			}
		}

		return convertedValue;
	}
	
	@Nullable
	private PropertyEditor findDefaultEditor(@Nullable Class<?> requiredType) {
		PropertyEditor editor = null;
		if (requiredType != null) {
			// No custom editor -> check BeanWrapperImpl's default editors.
			editor = this.propertyEditorRegistry.getDefaultEditor(requiredType);
			if (editor == null && String.class != requiredType) {
				// No BeanWrapper default editor -> check standard JavaBean editor.
				editor = BeanUtils.findEditorByConvention(requiredType);
			}
		}
		return editor;
	}

	@Nullable
	private Object doConvertValue(@Nullable Object oldValue, @Nullable Object newValue,
			@Nullable Class<?> requiredType, @Nullable PropertyEditor editor) {

		Object convertedValue = newValue;

		// 如果PropertyEditor不为空并且被转换属性不是字符串则调用PropertyEditor对值进行转换
		if (editor != null && !(convertedValue instanceof String)) {
			try {
				editor.setValue(convertedValue);
				Object newConvertedValue = editor.getValue();
				if (newConvertedValue != convertedValue) {
					convertedValue = newConvertedValue;
					// Reset PropertyEditor: It already did a proper conversion.
					// Don't use it again for a setAsText call.
					editor = null;
				}
			}
			catch (Exception ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("PropertyEditor [" + editor.getClass().getName() + "] does not support setValue call", ex);
				}
				// Swallow and proceed.
			}
		}

		Object returnValue = convertedValue;

		// 如果被转换值是字符串数组但是需要的类型不是数组则将字符串数组转换成字符串，逗号分隔
		if (requiredType != null && !requiredType.isArray() && convertedValue instanceof String[]) {
			// Convert String array to a comma-separated String.
			// Only applies if no PropertyEditor converted the String array before.
			// The CSV String will be passed into a PropertyEditor's setAsText method, if any.
			if (logger.isTraceEnabled()) {
				logger.trace("Converting String array to comma-delimited String [" + convertedValue + "]");
			}
			convertedValue = StringUtils.arrayToCommaDelimitedString((String[]) convertedValue);
		}

		if (convertedValue instanceof String) {
			if (editor != null) {
				// Use PropertyEditor's setAsText in case of a String value.
				if (logger.isTraceEnabled()) {
					logger.trace("Converting String to [" + requiredType + "] using property editor [" + editor + "]");
				}
				String newTextValue = (String) convertedValue;
				return doConvertTextValue(oldValue, newTextValue, editor);
			}
			else if (String.class == requiredType) {
				returnValue = convertedValue;
			}
		}

		return returnValue;
	}

	private Object doConvertTextValue(@Nullable Object oldValue, String newTextValue, PropertyEditor editor) {
		try {
			editor.setValue(oldValue);
		}
		catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("PropertyEditor [" + editor.getClass().getName() + "] does not support setValue call", ex);
			}
			// Swallow and proceed.
		}
		editor.setAsText(newTextValue);
		return editor.getValue();
	}

	private Object convertToTypedArray(Object input, @Nullable String propertyName, Class<?> componentType) {
		if (input instanceof Collection) {
			// Convert Collection elements to array elements.
			Collection<?> coll = (Collection<?>) input;
			Object result = Array.newInstance(componentType, coll.size());
			int i = 0;
			for (Iterator<?> it = coll.iterator(); it.hasNext(); i++) {
				Object value = convertIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, it.next(), componentType);
				Array.set(result, i, value);
			}
			return result;
		}
		else if (input.getClass().isArray()) {
			// Convert array elements, if necessary.
			if (componentType.equals(input.getClass().getComponentType()) &&
					!this.propertyEditorRegistry.hasCustomEditorForElement(componentType, propertyName)) {
				return input;
			}
			int arrayLength = Array.getLength(input);
			Object result = Array.newInstance(componentType, arrayLength);
			for (int i = 0; i < arrayLength; i++) {
				Object value = convertIfNecessary(
						buildIndexedPropertyName(propertyName, i), null, Array.get(input, i), componentType);
				Array.set(result, i, value);
			}
			return result;
		}
		else {
			// A plain value: convert it to an array with a single component.
			Object result = Array.newInstance(componentType, 1);
			Object value = convertIfNecessary(
					buildIndexedPropertyName(propertyName, 0), null, input, componentType);
			Array.set(result, 0, value);
			return result;
		}
	}

	@SuppressWarnings("unchecked")
	private Collection<?> convertToTypedCollection(Collection<?> original, @Nullable String propertyName,
			Class<?> requiredType, @Nullable TypeDescriptor typeDescriptor) {

		if (!Collection.class.isAssignableFrom(requiredType)) {
			return original;
		}

		// CollectionFactory中保存了常见集合类型，这里检查requiredType是否在这些类型中，如果则表示可以直接初始化requiredType类的集合
		boolean approximable = CollectionFactory.isApproximableCollectionType(requiredType);
		if (!approximable && !canCreateCopy(requiredType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Custom Collection type [" + original.getClass().getName() +
						"] does not allow for creating a copy - injecting original Collection as-is");
			}
			return original;
		}

		// 检查转换的值是否能够直接作为requiredType的类型使用
		boolean originalAllowed = requiredType.isInstance(original);
		TypeDescriptor elementType = (typeDescriptor != null ? typeDescriptor.getElementTypeDescriptor() : null);
		// 如果未获取到元素类型并且被转换的值可以直接作为requiredType的类型使用，并且没有找到propertyEditor，则直接返回被转换的值
		if (elementType == null && originalAllowed &&
				!this.propertyEditorRegistry.hasCustomEditorForElement(null, propertyName)) {
			return original;
		}

		Iterator<?> it;
		try {
			it = original.iterator();
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot access Collection of type [" + original.getClass().getName() +
						"] - injecting original Collection as-is: " + ex);
			}
			return original;
		}

		Collection<Object> convertedCopy;
		try {
			if (approximable) {
				// 根据被转换的值的类型创建空的集合
				convertedCopy = CollectionFactory.createApproximateCollection(original, original.size());
			}
			else {
				// 直接调用空构造函数创建聚合
				convertedCopy = (Collection<Object>)
						ReflectionUtils.accessibleConstructor(requiredType).newInstance();
			}
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot create copy of Collection type [" + original.getClass().getName() +
						"] - injecting original Collection as-is: " + ex);
			}
			return original;
		}

		int i = 0;
		// 逐个转换元素
		for (; it.hasNext(); i++) {
			Object element = it.next();
			String indexedPropertyName = buildIndexedPropertyName(propertyName, i);
			Object convertedElement = convertIfNecessary(indexedPropertyName, null, element,
					(elementType != null ? elementType.getType() : null) , elementType);
			try {
				convertedCopy.add(convertedElement);
			}
			catch (Throwable ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Collection type [" + original.getClass().getName() +
							"] seems to be read-only - injecting original Collection as-is: " + ex);
				}
				return original;
			}
			originalAllowed = originalAllowed && (element == convertedElement);
		}
		return (originalAllowed ? original : convertedCopy);
	}

	@SuppressWarnings("unchecked")
	private Map<?, ?> convertToTypedMap(Map<?, ?> original, @Nullable String propertyName,
			Class<?> requiredType, @Nullable TypeDescriptor typeDescriptor) {

		if (!Map.class.isAssignableFrom(requiredType)) {
			return original;
		}

		boolean approximable = CollectionFactory.isApproximableMapType(requiredType);
		if (!approximable && !canCreateCopy(requiredType)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Custom Map type [" + original.getClass().getName() +
						"] does not allow for creating a copy - injecting original Map as-is");
			}
			return original;
		}

		boolean originalAllowed = requiredType.isInstance(original);
		TypeDescriptor keyType = (typeDescriptor != null ? typeDescriptor.getMapKeyTypeDescriptor() : null);
		TypeDescriptor valueType = (typeDescriptor != null ? typeDescriptor.getMapValueTypeDescriptor() : null);
		if (keyType == null && valueType == null && originalAllowed &&
				!this.propertyEditorRegistry.hasCustomEditorForElement(null, propertyName)) {
			return original;
		}

		Iterator<?> it;
		try {
			it = original.entrySet().iterator();
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot access Map of type [" + original.getClass().getName() +
						"] - injecting original Map as-is: " + ex);
			}
			return original;
		}

		Map<Object, Object> convertedCopy;
		try {
			if (approximable) {
				convertedCopy = CollectionFactory.createApproximateMap(original, original.size());
			}
			else {
				convertedCopy = (Map<Object, Object>)
						ReflectionUtils.accessibleConstructor(requiredType).newInstance();
			}
		}
		catch (Throwable ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot create copy of Map type [" + original.getClass().getName() +
						"] - injecting original Map as-is: " + ex);
			}
			return original;
		}

		while (it.hasNext()) {
			Map.Entry<?, ?> entry = (Map.Entry<?, ?>) it.next();
			Object key = entry.getKey();
			Object value = entry.getValue();
			String keyedPropertyName = buildKeyedPropertyName(propertyName, key);
			Object convertedKey = convertIfNecessary(keyedPropertyName, null, key,
					(keyType != null ? keyType.getType() : null), keyType);
			Object convertedValue = convertIfNecessary(keyedPropertyName, null, value,
					(valueType!= null ? valueType.getType() : null), valueType);
			try {
				convertedCopy.put(convertedKey, convertedValue);
			}
			catch (Throwable ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Map type [" + original.getClass().getName() +
							"] seems to be read-only - injecting original Map as-is: " + ex);
				}
				return original;
			}
			originalAllowed = originalAllowed && (key == convertedKey) && (value == convertedValue);
		}
		return (originalAllowed ? original : convertedCopy);
	}

	@Nullable
	private String buildIndexedPropertyName(@Nullable String propertyName, int index) {
		return (propertyName != null ?
				propertyName + PropertyAccessor.PROPERTY_KEY_PREFIX + index + PropertyAccessor.PROPERTY_KEY_SUFFIX :
				null);
	}

	@Nullable
	private String buildKeyedPropertyName(@Nullable String propertyName, Object key) {
		return (propertyName != null ?
				propertyName + PropertyAccessor.PROPERTY_KEY_PREFIX + key + PropertyAccessor.PROPERTY_KEY_SUFFIX :
				null);
	}

	private boolean canCreateCopy(Class<?> requiredType) {
		return (!requiredType.isInterface() && !Modifier.isAbstract(requiredType.getModifiers()) &&
				Modifier.isPublic(requiredType.getModifiers()) && ClassUtils.hasConstructor(requiredType));
	}

}
```

[TypeConverterDelegate]: aaa
[BeanWrapperImpl]: aaa
[PropertyEditorRegistrySupport]: aaa
[PropertyEditor]: aaa
[ConversionService]: aaa