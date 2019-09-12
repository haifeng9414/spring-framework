[HandlerMethodArgumentResolverd]用于解析方法参数，在笔记[ServletInvocableHandlerMethod的实现](ServletInvocableHandlerMethod的实现.md)中有提到其使用场景，常见的SpringMVC参数注解如[PathVariable]都有对应的[HandlerMethodArgumentResolverd]实现，这里对[HandlerMethodArgumentResolverd]接口及其常见的实现类进行分析

首先是[HandlerMethodArgumentResolver]接口的定义：
```java
public interface HandlerMethodArgumentResolver {
	boolean supportsParameter(MethodParameter parameter);

	@Nullable
	Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception;
}
```

下面是几个主要的抽象类，大部分常见的实现类都继承自这些抽象类，首先是[AbstractNamedValueMethodArgumentResolver]，该类实现了解析参数的整体逻辑，具体获取参数名称的过程由子类实现，[AbstractNamedValueMethodArgumentResolver]还对参数名称中的表达式提供了支持，并利用[WebDataBinderFactory]对参数转换提供了支持，代码：
```java
public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Nullable
	private final ConfigurableBeanFactory configurableBeanFactory;

	@Nullable
	// BeanExpressionContext接收beanName，并从beanFactory获取bean，如果获取不到，则尝试从Scope对象中获取
	private final BeanExpressionContext expressionContext;

	// NamedValueInfo类保存参数的名称、require属性和默认值
	private final Map<MethodParameter, NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);

	public AbstractNamedValueMethodArgumentResolver() {
		this.configurableBeanFactory = null;
		this.expressionContext = null;
	}
  
	public AbstractNamedValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
		this.configurableBeanFactory = beanFactory;
		// 使用RequestScope作为expressionContext的scope，这样当从beanFactory获取不到bean时，会尝试从RequestScope获取，
		// 而RequestScope只支持参数为request和session的对象获取操作，分别返回当前请求request和session
		this.expressionContext =
				(beanFactory != null ? new BeanExpressionContext(beanFactory, new RequestScope()) : null);
	}

	@Override
	@Nullable
	public final Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		// 为当前参数创建NamedValueInfo，具体创建过程由子类实现
		NamedValueInfo namedValueInfo = getNamedValueInfo(parameter);
		MethodParameter nestedParameter = parameter.nestedIfOptional();

		// 使用beanFactory中的BeanExpressionResolver对参数名称进行解析，以支持参数名称中的表达式
		Object resolvedName = resolveStringValue(namedValueInfo.name);
		if (resolvedName == null) {
			throw new IllegalArgumentException(
					"Specified name must not resolve to null: [" + namedValueInfo.name + "]");
		}

		// 由子类实现，解析参数值
		Object arg = resolveName(resolvedName.toString(), nestedParameter, webRequest);
		if (arg == null) {
			// 如果解析结果为空，则尝试使用默认值
			if (namedValueInfo.defaultValue != null) {
				arg = resolveStringValue(namedValueInfo.defaultValue);
			}
			// 如果参数没有默认值，并且是require的，并且非optional，则默认抛出ServletRequestBindingException
			else if (namedValueInfo.required && !nestedParameter.isOptional()) {
				handleMissingValue(namedValueInfo.name, nestedParameter, webRequest);
			}
			// 如果参数类型是Boolean类型，则返回false，否则如果是基础类型，则抛出异常，因为基础类型不能被Optional封装
			arg = handleNullValue(namedValueInfo.name, arg, nestedParameter.getNestedParameterType());
		}
		// 空字符串的解析结果也尝试用默认值替代
		else if ("".equals(arg) && namedValueInfo.defaultValue != null) {
			arg = resolveStringValue(namedValueInfo.defaultValue);
		}

		if (binderFactory != null) {
			WebDataBinder binder = binderFactory.createBinder(webRequest, null, namedValueInfo.name);
			try {
				// 尝试对参数进行转换，这里用WebDataBinderFactory创建WebDataBinder来进行类型转换，是因为WebDataBinderFactory的实现类
				// 可以对binder进行定制，这可能会影响转换结果，如InitBinderDataBinderFactory对initBinder注解提供了支持，可以通过在方法上
				// 添加initBinder注解并声明一个WebDataBinder类型的参数接收WebDataBinder并对其进行定制
				arg = binder.convertIfNecessary(arg, parameter.getParameterType(), parameter);
			}
			catch (ConversionNotSupportedException ex) {
				throw new MethodArgumentConversionNotSupportedException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());
			}
			catch (TypeMismatchException ex) {
				throw new MethodArgumentTypeMismatchException(arg, ex.getRequiredType(),
						namedValueInfo.name, parameter, ex.getCause());

			}
		}

		// 空方法，供子类实现
		handleResolvedValue(arg, namedValueInfo.name, parameter, mavContainer, webRequest);

		return arg;
	}
  
	private NamedValueInfo getNamedValueInfo(MethodParameter parameter) {
		NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);
		if (namedValueInfo == null) {
			namedValueInfo = createNamedValueInfo(parameter);
			// updateNamedValueInfo方法检查创建的namedValueInfo是否有名字，如果没有则使用参数名，同时还检查了namedValueInfo是否存在
			// 默认值
			namedValueInfo = updateNamedValueInfo(parameter, namedValueInfo);
			this.namedValueInfoCache.put(parameter, namedValueInfo);
		}
		return namedValueInfo;
	}
  
	protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);

	private NamedValueInfo updateNamedValueInfo(MethodParameter parameter, NamedValueInfo info) {
		String name = info.name;
		if (info.name.isEmpty()) {
			name = parameter.getParameterName();
			if (name == null) {
				throw new IllegalArgumentException(
						"Name for argument type [" + parameter.getNestedParameterType().getName() +
						"] not available, and parameter name information not found in class file either.");
			}
		}
    // ValueConstants.DEFAULT_NONE表示没有默认值
		String defaultValue = (ValueConstants.DEFAULT_NONE.equals(info.defaultValue) ? null : info.defaultValue);
		return new NamedValueInfo(name, info.required, defaultValue);
	}
  
	@Nullable
	private Object resolveStringValue(String value) {
		if (this.configurableBeanFactory == null) {
			return value;
		}
		String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
		BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
		if (exprResolver == null || this.expressionContext == null) {
			return value;
		}
		return exprResolver.evaluate(placeholdersResolved, this.expressionContext);
	}
  
	@Nullable
	protected abstract Object resolveName(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception;

	protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request)
			throws Exception {

		handleMissingValue(name, parameter);
	}
  
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
		throw new ServletRequestBindingException("Missing argument '" + name +
				"' for method parameter of type " + parameter.getNestedParameterType().getSimpleName());
	}

	@Nullable
	private Object handleNullValue(String name, @Nullable Object value, Class<?> paramType) {
		if (value == null) {
			if (Boolean.TYPE.equals(paramType)) {
				return Boolean.FALSE;
			}
			else if (paramType.isPrimitive()) {
				throw new IllegalStateException("Optional " + paramType.getSimpleName() + " parameter '" + name +
						"' is present but cannot be translated into a null value due to being declared as a " +
						"primitive type. Consider declaring it as object wrapper for the corresponding primitive type.");
			}
		}
		return value;
	}
  
	protected void handleResolvedValue(@Nullable Object arg, String name, MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest) {
	}
  
	protected static class NamedValueInfo {

		private final String name;

		private final boolean required;

		@Nullable
		private final String defaultValue;

		public NamedValueInfo(String name, boolean required, @Nullable String defaultValue) {
			this.name = name;
			this.required = required;
			this.defaultValue = defaultValue;
		}
	}

}
```

[AbstractNamedValueMethodArgumentResolver]的子类需要实现的方法有:
```java
// 判断是否支持解析该参数
public boolean supportsParameter(MethodParameter parameter);
// 获取参数对应的NamedValueInfo对象，该对象包含参数名称、参数默认值及require
protected abstract NamedValueInfo createNamedValueInfo(MethodParameter parameter);
// 解析参数值
protected abstract Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception;
```

下面是[AbstractNamedValueMethodArgumentResolver]的实现类[PathVariableMethodArgumentResolver]的实现，[PathVariableMethodArgumentResolver]解析参数值的是通过保存在request中的HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE属性实现的，该变量的初始化可以看笔记[RequestMappingHandlerMapping的实现.md](RequestMappingHandlerMapping的实现.md)，代码：
```java
public class PathVariableMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver
		implements UriComponentsContributor {

	private static final TypeDescriptor STRING_TYPE_DESCRIPTOR = TypeDescriptor.valueOf(String.class);


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		if (!parameter.hasParameterAnnotation(PathVariable.class)) {
			return false;
		}
		// 判断参数是否是Map类型的，如果是，则需要PathVariable注解配置了参数名称
		if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
			PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
			return (pathVariable != null && StringUtils.hasText(pathVariable.value()));
		}
		return true;
	}

	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		PathVariable ann = parameter.getParameterAnnotation(PathVariable.class);
		Assert.state(ann != null, "No PathVariable annotation");
		// PathVariableNamedValueInfo类继承自NamedValueInfo类，用PathVariable注解中的信息表示NamedValueInfo中信息
		return new PathVariableNamedValueInfo(ann);
	}

	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		// PathVariable变量的值就保存在request的URI_TEMPLATE_VARIABLES_ATTRIBUTE属性中，这块可以看RequestMappingInfoHandlerMapping
		// 的handleMatch方法的实现，这里只需要从uriTemplateVars中获取值就可以了
		Map<String, String> uriTemplateVars = (Map<String, String>) request.getAttribute(
				HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		return (uriTemplateVars != null ? uriTemplateVars.get(name) : null);
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletRequestBindingException {
		// 重新定义找不到值的异常
		throw new MissingPathVariableException(name, parameter);
	}

	@Override
	@SuppressWarnings("unchecked")
	// 参数解析完成后执行，这里将解析到request属性中View.PATH_VARIABLES对应的HashMap中
	protected void handleResolvedValue(@Nullable Object arg, String name, MethodParameter parameter,
			@Nullable ModelAndViewContainer mavContainer, NativeWebRequest request) {

		String key = View.PATH_VARIABLES;
		int scope = RequestAttributes.SCOPE_REQUEST;
		// 判断request中会否已存在View.PATH_VARIABLES对应的map，不存在则创建
		Map<String, Object> pathVars = (Map<String, Object>) request.getAttribute(key, scope);
		if (pathVars == null) {
			pathVars = new HashMap<>();
			request.setAttribute(key, pathVars, scope);
		}
		// 保存值
		pathVars.put(name, arg);
	}

	@Override
	// 该方法和MvcUriComponentsBuilder与UriComponents有关，对请求的执行没有影响
	public void contributeMethodArgument(MethodParameter parameter, Object value,
			UriComponentsBuilder builder, Map<String, Object> uriVariables, ConversionService conversionService) {

		if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
			return;
		}

		PathVariable ann = parameter.getParameterAnnotation(PathVariable.class);
		String name = (ann != null && !StringUtils.isEmpty(ann.value()) ? ann.value() : parameter.getParameterName());
		String formatted = formatUriValue(conversionService, new TypeDescriptor(parameter.nestedIfOptional()), value);
		uriVariables.put(name, formatted);
	}

	@Nullable
	protected String formatUriValue(@Nullable ConversionService cs, @Nullable TypeDescriptor sourceType, Object value) {
		if (value instanceof String) {
			return (String) value;
		}
		else if (cs != null) {
			return (String) cs.convert(value, sourceType, STRING_TYPE_DESCRIPTOR);
		}
		else {
			return value.toString();
		}
	}


	private static class PathVariableNamedValueInfo extends NamedValueInfo {

		public PathVariableNamedValueInfo(PathVariable annotation) {
			super(annotation.name(), annotation.required(), ValueConstants.DEFAULT_NONE);
		}
	}

}
```

其他[AbstractNamedValueMethodArgumentResolver]的实现也大同小异，这里不再赘述

[HandlerMethodArgumentResolverd]: aaa
[PathVariable]: aaa
[HandlerMethodArgumentResolver]: aaa
[AbstractNamedValueMethodArgumentResolver]: aaa