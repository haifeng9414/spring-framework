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

[HandlerMethodArgumentResolverd]还有一个重要的抽象类实现[AbstractMessageConverterMethodArgumentResolver]，该类支持利用[HttpMessageConverter]，将request的inputStream转化为request的ContentType对应的MediaType类型的数据，同时还提供了参数校验相关支持，代码：
```java
public abstract class AbstractMessageConverterMethodArgumentResolver implements HandlerMethodArgumentResolver {

	private static final Set<HttpMethod> SUPPORTED_METHODS =
			EnumSet.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

	private static final Object NO_VALUE = new Object();


	protected final Log logger = LogFactory.getLog(getClass());

	protected final List<HttpMessageConverter<?>> messageConverters;

	protected final List<MediaType> allSupportedMediaTypes;

	private final RequestResponseBodyAdviceChain advice;

	public AbstractMessageConverterMethodArgumentResolver(List<HttpMessageConverter<?>> converters) {
		this(converters, null);
	}
	
	public AbstractMessageConverterMethodArgumentResolver(List<HttpMessageConverter<?>> converters,
			@Nullable List<Object> requestResponseBodyAdvice) {

		Assert.notEmpty(converters, "'messageConverters' must not be empty");
		this.messageConverters = converters;
		this.allSupportedMediaTypes = getAllSupportedMediaTypes(converters);
		this.advice = new RequestResponseBodyAdviceChain(requestResponseBodyAdvice);
	}
	
	private static List<MediaType> getAllSupportedMediaTypes(List<HttpMessageConverter<?>> messageConverters) {
		Set<MediaType> allSupportedMediaTypes = new LinkedHashSet<>();
		// 遍历HttpMessageConverter获取支持的MediaType
		for (HttpMessageConverter<?> messageConverter : messageConverters) {
			allSupportedMediaTypes.addAll(messageConverter.getSupportedMediaTypes());
		}
		List<MediaType> result = new ArrayList<>(allSupportedMediaTypes);
		MediaType.sortBySpecificity(result);
		return Collections.unmodifiableList(result);
	}
	
	RequestResponseBodyAdviceChain getAdvice() {
		return this.advice;
	}
	
	@Nullable
	protected <T> Object readWithMessageConverters(NativeWebRequest webRequest, MethodParameter parameter,
			Type paramType) throws IOException, HttpMediaTypeNotSupportedException, HttpMessageNotReadableException {

		// createInputMessage方法返回ServletServerHttpRequest对象，ServletServerHttpRequest对象持有HttpServletRequest
		HttpInputMessage inputMessage = createInputMessage(webRequest);
		return readWithMessageConverters(inputMessage, parameter, paramType);
	}
	
	@SuppressWarnings("unchecked")
	@Nullable
	protected <T> Object readWithMessageConverters(HttpInputMessage inputMessage, MethodParameter parameter,
			Type targetType) throws IOException, HttpMediaTypeNotSupportedException, HttpMessageNotReadableException {

		MediaType contentType;
		boolean noContentType = false;
		try {
			// 先尝试获取请求头的ContentType，作为MediaType
			contentType = inputMessage.getHeaders().getContentType();
		}
		catch (InvalidMediaTypeException ex) {
			throw new HttpMediaTypeNotSupportedException(ex.getMessage());
		}
		if (contentType == null) {
			noContentType = true;
			// 没获取到则使用默认值application/octet-stream
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}

		// 获取MethodParameter对象对应的方法所在类
		Class<?> contextClass = parameter.getContainingClass();
		Class<T> targetClass = (targetType instanceof Class ? (Class<T>) targetType : null);
		if (targetClass == null) {
			ResolvableType resolvableType = ResolvableType.forMethodParameter(parameter);
			targetClass = (Class<T>) resolvableType.resolve();
		}

		// 获取请求对应的method，如POST
		HttpMethod httpMethod = (inputMessage instanceof HttpRequest ? ((HttpRequest) inputMessage).getMethod() : null);
		Object body = NO_VALUE;

		EmptyBodyCheckingHttpInputMessage message;
		try {
			// EmptyBodyCheckingHttpInputMessage在body为空的时候返回一个空的inputStream
			message = new EmptyBodyCheckingHttpInputMessage(inputMessage);

			for (HttpMessageConverter<?> converter : this.messageConverters) {
				Class<HttpMessageConverter<?>> converterType = (Class<HttpMessageConverter<?>>) converter.getClass();
				GenericHttpMessageConverter<?> genericConverter =
						(converter instanceof GenericHttpMessageConverter ? (GenericHttpMessageConverter<?>) converter : null);
				// 判断converter是否支持当前的类型转换
				if (genericConverter != null ? genericConverter.canRead(targetType, contextClass, contentType) :
						(targetClass != null && converter.canRead(targetClass, contentType))) {
					if (logger.isDebugEnabled()) {
						logger.debug("Read [" + targetType + "] as \"" + contentType + "\" with [" + converter + "]");
					}
					if (message.hasBody()) {
						// 转换前的回调函数
						HttpInputMessage msgToUse =
								getAdvice().beforeBodyRead(message, parameter, targetType, converterType);
						// 将请求的body也就是inputStream转换为对象
						body = (genericConverter != null ? genericConverter.read(targetType, contextClass, msgToUse) :
								((HttpMessageConverter<T>) converter).read(targetClass, msgToUse));
						// 转换后的回调函数
						body = getAdvice().afterBodyRead(body, msgToUse, parameter, targetType, converterType);
					}
					else {
						// 空body用advice处理
						body = getAdvice().handleEmptyBody(null, message, parameter, targetType, converterType);
					}
					break;
				}
			}
		}
		catch (IOException ex) {
			throw new HttpMessageNotReadableException("I/O error while reading input message", ex);
		}

		if (body == NO_VALUE) {
			if (httpMethod == null || !SUPPORTED_METHODS.contains(httpMethod) ||
					(noContentType && !message.hasBody())) {
				return null;
			}
			throw new HttpMediaTypeNotSupportedException(contentType, this.allSupportedMediaTypes);
		}

		return body;
	}
	
	protected ServletServerHttpRequest createInputMessage(NativeWebRequest webRequest) {
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		Assert.state(servletRequest != null, "No HttpServletRequest");
		return new ServletServerHttpRequest(servletRequest);
	}

	// 用于参数校验，如果参数有Validated注解，则调用binder.validate进行校验
	protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) {
		Annotation[] annotations = parameter.getParameterAnnotations();
		for (Annotation ann : annotations) {
			Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
			if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
				Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
				Object[] validationHints = (hints instanceof Object[] ? (Object[]) hints : new Object[] {hints});
				binder.validate(validationHints);
				break;
			}
		}
	}

	/*
	 当使用参数校验功能时，被校验的参数后面可以跟Errors或其实现类接收校验结果（也只能跟在被校验参数后面），如：
	 public String addUser(HttpServletRequest request,
                          @Validated @ModelAttribute("user") User user,
                          BindingResult userResult, // BindingResult实现了接口
                          final RedirectAttributes redirectAttributes, @PathVariable String test) {
    	//...
	}
	
	这里检查指定的被校验参数是否存在接收校验结果的参数
	 */
	protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
		int i = parameter.getParameterIndex();
		Class<?>[] paramTypes = parameter.getExecutable().getParameterTypes();
		// 检查当前参数后面一个参数是不是Errors类型的
		boolean hasBindingResult = (paramTypes.length > (i + 1) && Errors.class.isAssignableFrom(paramTypes[i + 1]));
		return !hasBindingResult;
	}

	@Nullable
	// 如果参数是Optional类型的，则将参数值转为响应的Optional结果，否则直接返回参数值
	protected Object adaptArgumentIfNecessary(@Nullable Object arg, MethodParameter parameter) {
		if (parameter.getParameterType() == Optional.class) {
			if (arg == null || (arg instanceof Collection && ((Collection<?>) arg).isEmpty()) ||
					(arg instanceof Object[] && ((Object[]) arg).length == 0)) {
				return Optional.empty();
			}
			else {
				return Optional.of(arg);
			}
		}
		return arg;
	}


	private static class EmptyBodyCheckingHttpInputMessage implements HttpInputMessage {

		private final HttpHeaders headers;

		@Nullable
		private final InputStream body;

		public EmptyBodyCheckingHttpInputMessage(HttpInputMessage inputMessage) throws IOException {
			this.headers = inputMessage.getHeaders();
			InputStream inputStream = inputMessage.getBody();
			if (inputStream.markSupported()) {
				inputStream.mark(1);
				this.body = (inputStream.read() != -1 ? inputStream : null);
				inputStream.reset();
			}
			else {
				PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
				int b = pushbackInputStream.read();
				if (b == -1) {
					this.body = null;
				}
				else {
					this.body = pushbackInputStream;
					pushbackInputStream.unread(b);
				}
			}
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

		@Override
		public InputStream getBody() {
			return (this.body != null ? this.body : StreamUtils.emptyInput());
		}

		public boolean hasBody() {
			return (this.body != null);
		}
	}

}
```

[AbstractMessageConverterMethodArgumentResolver]只是提供了类型转换和参数校验的方法，没有实现[HandlerMethodArgumentResolverd]接口的两个方法，下面介绍一个[AbstractMessageConverterMethodArgumentResolver]的实现类[RequestResponseBodyMethodProcessor]来分析如何使用[AbstractMessageConverterMethodArgumentResolver]提供的方法实现[HandlerMethodArgumentResolverd]接口，[RequestResponseBodyMethodProcessor]对方法参数的[RequestBody]注解提供了支持，同时对方法上的[ResponseBody]注解提供支持，如果参数带有[RequestBody]注解和`javax.validation.Valid`注解，[RequestResponseBodyMethodProcessor]还会对该参数进行校验，和普通的校验过程不同的是，[RequestResponseBodyMethodProcessor]的校验抛出的异常是[MethodArgumentNotValidException]，而不是普通参数校验的[BindException]，关于普通的参数校验，可以看笔记[SpringMVC的参数验证实现原理.md](SpringMVC的参数验证实现原理.md)

[RequestResponseBodyMethodProcessor]类不直接继承[AbstractMessageConverterMethodArgumentResolver]，而是[AbstractMessageConverterMethodProcessor]，[RequestResponseBodyMethodProcessor]类继承结构：![RequestResponseBodyMethodProcessor继承结构](RequestResponseBodyMethodProcessor.png)

[HandlerMethodReturnValueHandler]接口定义了处理请求执行结果的方法，代码：
```java
public interface HandlerMethodReturnValueHandler {
	boolean supportsReturnType(MethodParameter returnType);

	void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception;
}
```

[AbstractMessageConverterMethodProcessor]类没有直接实现[HandlerMethodReturnValueHandler]接口和[HandlerMethodArgumentResolverd]接口，而是提供了将请求执行结果写到响应中的方法，代码：
```java
public abstract class AbstractMessageConverterMethodProcessor extends AbstractMessageConverterMethodArgumentResolver
		implements HandlerMethodReturnValueHandler {

	/* Extensions associated with the built-in message converters */
	private static final Set<String> WHITELISTED_EXTENSIONS = new HashSet<>(Arrays.asList(
			"txt", "text", "yml", "properties", "csv",
			"json", "xml", "atom", "rss",
			"png", "jpe", "jpeg", "jpg", "gif", "wbmp", "bmp"));

	private static final Set<String> WHITELISTED_MEDIA_BASE_TYPES = new HashSet<>(
			Arrays.asList("audio", "image", "video"));

	private static final MediaType MEDIA_TYPE_APPLICATION = new MediaType("application");

	// ParameterizedTypeReference对象用于辅助获取范型信息的
	private static final Type RESOURCE_REGION_LIST_TYPE =
			new ParameterizedTypeReference<List<ResourceRegion>>() { }.getType();


	private static final UrlPathHelper decodingUrlPathHelper = new UrlPathHelper();

	private static final UrlPathHelper rawUrlPathHelper = new UrlPathHelper();

	static {
		rawUrlPathHelper.setRemoveSemicolonContent(false);
		rawUrlPathHelper.setUrlDecode(false);
	}


	private final ContentNegotiationManager contentNegotiationManager;

	private final PathExtensionContentNegotiationStrategy pathStrategy;

	private final Set<String> safeExtensions = new HashSet<>();

	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters) {
		this(converters, null, null);
	}
	
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager contentNegotiationManager) {

		this(converters, contentNegotiationManager, null);
	}
	
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager manager, @Nullable List<Object> requestResponseBodyAdvice) {

		super(converters, requestResponseBodyAdvice);

		this.contentNegotiationManager = (manager != null ? manager : new ContentNegotiationManager());
		// pathStrategy默认实现为PathExtensionContentNegotiationStrategy
		this.pathStrategy = initPathStrategy(this.contentNegotiationManager);
		this.safeExtensions.addAll(this.contentNegotiationManager.getAllFileExtensions());
		// 添加SpringMVC本身支持的资源类型
		this.safeExtensions.addAll(WHITELISTED_EXTENSIONS);
	}

	private static PathExtensionContentNegotiationStrategy initPathStrategy(ContentNegotiationManager manager) {
		Class<PathExtensionContentNegotiationStrategy> clazz = PathExtensionContentNegotiationStrategy.class;
		PathExtensionContentNegotiationStrategy strategy = manager.getStrategy(clazz);
		return (strategy != null ? strategy : new PathExtensionContentNegotiationStrategy());
	}
	
	protected ServletServerHttpResponse createOutputMessage(NativeWebRequest webRequest) {
		// 从NativeWebRequest获取response并为其创建ServletServerHttpResponse对象
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		Assert.state(response != null, "No HttpServletResponse");
		return new ServletServerHttpResponse(response);
	}

	/**
	 * Writes the given return value to the given web request. Delegates to
	 * {@link #writeWithMessageConverters(Object, MethodParameter, ServletServerHttpRequest, ServletServerHttpResponse)}
	 */
	protected <T> void writeWithMessageConverters(T value, MethodParameter returnType, NativeWebRequest webRequest)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);
		writeWithMessageConverters(value, returnType, inputMessage, outputMessage);
	}

	/**
	 * Writes the given return type to the given output message.
	 * @param value the value to write to the output message
	 * @param returnType the type of the value
	 * @param inputMessage the input messages. Used to inspect the {@code Accept} header.
	 * @param outputMessage the output message to write to
	 * @throws IOException thrown in case of I/O errors
	 * @throws HttpMediaTypeNotAcceptableException thrown when the conditions indicated
	 * by the {@code Accept} header on the request cannot be met by the message converters
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	protected <T> void writeWithMessageConverters(@Nullable T value, MethodParameter returnType,
			ServletServerHttpRequest inputMessage, ServletServerHttpResponse outputMessage)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		Object outputValue;
		Class<?> valueType;
		Type declaredType;

		// 如果请求执行结果是CharSequence类型的，直接按照字符串处理
		if (value instanceof CharSequence) {
			outputValue = value.toString();
			valueType = String.class;
			declaredType = String.class;
		}
		else {
			outputValue = value;
			// 获取返回值类型，如果请求执行结果不为空，则返回其getClass，否则返回执行请求的方法的返回值的Class对象
			valueType = getReturnValueType(outputValue, returnType);
			// 获取执行请求的方法的返回值的Type对象
			declaredType = getGenericType(returnType);
		}

		// 如果返回值是Resource类型的
		if (isResourceType(value, returnType)) {
			// 响应用Accept-Ranges表示服务器支持范围请求（partial requests），这里的bytes表示范围请求的单位
			outputMessage.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
			if (value != null && inputMessage.getHeaders().getFirst(HttpHeaders.RANGE) != null) {
				// 将返回值强转成Resource对象，用于后续的范围处理
				Resource resource = (Resource) value;
				try {
					// 将请求的Range请求头转换为HttpRange对象便于使用
					List<HttpRange> httpRanges = inputMessage.getHeaders().getRange();
					// 设置响应的状态码为206，即Partial Content，表示当前响应只是数据的一部分，对于后续的部分数据，请求会在Range首部指定范围
					// 如果响应只有一个数据区间，则整个响应的Content-Type首部的值为所请求的文件的类型（如image/gif），同时包含Content-Range首部，显示了当前
					// 响应包含了整个数据区间
					// 如果包含多个数据区间，那么第一个响应的Content-Type首部的值为multipart/byteranges，之后的响应各包含一个片段，对应一个数据区间，
					// 并提供Content-Range（表示当前响应对应的区间）和Content-Type（表示资源的类型，如image/gif）描述信息。
					outputMessage.getServletResponse().setStatus(HttpStatus.PARTIAL_CONTENT.value());
					// 重新设置返回值，根据Range获取Resource对象的ResourceRegion对象，ResourceRegion对象表示Resource对象的一部分，这里的返回值
					// 为ResourceRegion对象的List
					outputValue = HttpRange.toResourceRegions(httpRanges, resource);
					// 重新设置返回值类型
					valueType = outputValue.getClass();
					// RESOURCE_REGION_LIST_TYPE包含了ParameterizedTypeReference<List<ResourceRegion>>(){}对象对应的范型类型，和HttpRange.toResourceRegions的
					// 返回值相对应
					declaredType = RESOURCE_REGION_LIST_TYPE;
				}
				catch (IllegalArgumentException ex) {
					outputMessage.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength());
					// 设置响应的状态码为416，表示当前的范围请求不合法
					outputMessage.getServletResponse().setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
				}
			}
		}


		List<MediaType> mediaTypesToUse;

		// 获取当前响应的contentType
		MediaType contentType = outputMessage.getHeaders().getContentType();
		// 如果contentType不为空并且是具体的类型，即不是*/*或不是xxx/*+
		if (contentType != null && contentType.isConcrete()) {
			mediaTypesToUse = Collections.singletonList(contentType);
		}
		else {
			HttpServletRequest request = inputMessage.getServletRequest();
			// 获取请求的MediaType，请求的MediaType可以以多用形式指定，如Accept请求头，每种形式对应一个ContentNegotiationStrategy接口的实现，
			// 这里根据contentNegotiationManager中保存的ContentNegotiationStrategy列表获取当前请求的MediaType，没获取到则返回*/*
			List<MediaType> requestedMediaTypes = getAcceptableMediaTypes(request);
			// 返回SpringMVC对当前请求支持的MediaType列表，默认返回请求中的PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE属性值，如果为空则遍历
			// messageConverters，返回所有的HttpMessageConverter的getSupportedMediaTypes结果
			List<MediaType> producibleMediaTypes = getProducibleMediaTypes(request, valueType, declaredType);

			if (outputValue != null && producibleMediaTypes.isEmpty()) {
				throw new HttpMessageNotWritableException(
						"No converter found for return value of type: " + valueType);
			}
			mediaTypesToUse = new ArrayList<>();
			// 遍历请求的MediaType和SpringMVC对当前请求支持的MediaType，获取交集
			for (MediaType requestedType : requestedMediaTypes) {
				for (MediaType producibleType : producibleMediaTypes) {
					if (requestedType.isCompatibleWith(producibleType)) {
						mediaTypesToUse.add(getMostSpecificMediaType(requestedType, producibleType));
					}
				}
			}
			if (mediaTypesToUse.isEmpty()) {
				if (outputValue != null) {
					throw new HttpMediaTypeNotAcceptableException(producibleMediaTypes);
				}
				return;
			}
			// 对MediaType进行排序，排序原理可以看org.springframework.http.MediaType.QUALITY_VALUE_COMPARATOR的实现
			MediaType.sortBySpecificityAndQuality(mediaTypesToUse);
		}

		MediaType selectedMediaType = null;
		for (MediaType mediaType : mediaTypesToUse) {
			// 如果MediaType是具体的，即不是*/*或xxx/*+，则选择该MediaType
			if (mediaType.isConcrete()) {
				selectedMediaType = mediaType;
				break;
			}
			// MediaType为application表示请求的是二进制数据，一般情况下情况下，对于text文件类型若没有特定的subtype，就使用text/plain，
			// 类似的，二进制文件没有特定或已知的subtype，即使用application/octet-stream
			else if (mediaType.equals(MediaType.ALL) || mediaType.equals(MEDIA_TYPE_APPLICATION)) {
				// application/octet-stream，表示所有其他情况的默认值。一种未知的文件类型应当使用此类型。浏览器在处理这些文件时会特别小心，试图防止、避免用户的危险行为
				selectedMediaType = MediaType.APPLICATION_OCTET_STREAM;
				break;
			}
		}

		if (selectedMediaType != null) {
			// 移除MediaType中的q属性
			selectedMediaType = selectedMediaType.removeQualityValue();
			// 遍历HttpMessageConverter转换结果
			for (HttpMessageConverter<?> converter : this.messageConverters) {
				GenericHttpMessageConverter genericConverter =
						(converter instanceof GenericHttpMessageConverter ? (GenericHttpMessageConverter<?>) converter : null);
				if (genericConverter != null ?
						((GenericHttpMessageConverter) converter).canWrite(declaredType, valueType, selectedMediaType) :
						converter.canWrite(valueType, selectedMediaType)) {
					// 转换前的回调
					outputValue = getAdvice().beforeBodyWrite(outputValue, returnType, selectedMediaType,
							(Class<? extends HttpMessageConverter<?>>) converter.getClass(),
							inputMessage, outputMessage);
					if (outputValue != null) {
						// Content-Disposition响应头的处理
						addContentDispositionHeader(inputMessage, outputMessage);
						if (genericConverter != null) {
							genericConverter.write(outputValue, declaredType, selectedMediaType, outputMessage);
						}
						else {
							((HttpMessageConverter) converter).write(outputValue, selectedMediaType, outputMessage);
						}
						if (logger.isDebugEnabled()) {
							logger.debug("Written [" + outputValue + "] as \"" + selectedMediaType +
									"\" using [" + converter + "]");
						}
					}
					return;
				}
			}
		}

		if (outputValue != null) {
			throw new HttpMediaTypeNotAcceptableException(this.allSupportedMediaTypes);
		}
	}
	
	protected Class<?> getReturnValueType(@Nullable Object value, MethodParameter returnType) {
		return (value != null ? value.getClass() : returnType.getParameterType());
	}
	
	protected boolean isResourceType(@Nullable Object value, MethodParameter returnType) {
		Class<?> clazz = getReturnValueType(value, returnType);
		return clazz != InputStreamResource.class && Resource.class.isAssignableFrom(clazz);
	}
	
	private Type getGenericType(MethodParameter returnType) {
		if (HttpEntity.class.isAssignableFrom(returnType.getParameterType())) {
			return ResolvableType.forType(returnType.getGenericParameterType()).getGeneric().getType();
		}
		else {
			return returnType.getGenericParameterType();
		}
	}
	@SuppressWarnings("unused")
	protected List<MediaType> getProducibleMediaTypes(HttpServletRequest request, Class<?> valueClass) {
		return getProducibleMediaTypes(request, valueClass, null);
	}

	@SuppressWarnings("unchecked")
	protected List<MediaType> getProducibleMediaTypes(HttpServletRequest request, Class<?> valueClass,
			@Nullable Type declaredType) {

		Set<MediaType> mediaTypes = (Set<MediaType>) request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
		if (!CollectionUtils.isEmpty(mediaTypes)) {
			return new ArrayList<>(mediaTypes);
		}
		else if (!this.allSupportedMediaTypes.isEmpty()) {
			List<MediaType> result = new ArrayList<>();
			for (HttpMessageConverter<?> converter : this.messageConverters) {
				if (converter instanceof GenericHttpMessageConverter && declaredType != null) {
					if (((GenericHttpMessageConverter<?>) converter).canWrite(declaredType, valueClass, null)) {
						result.addAll(converter.getSupportedMediaTypes());
					}
				}
				else if (converter.canWrite(valueClass, null)) {
					result.addAll(converter.getSupportedMediaTypes());
				}
			}
			return result;
		}
		else {
			return Collections.singletonList(MediaType.ALL);
		}
	}

	private List<MediaType> getAcceptableMediaTypes(HttpServletRequest request)
			throws HttpMediaTypeNotAcceptableException {

		return this.contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
	}

	private MediaType getMostSpecificMediaType(MediaType acceptType, MediaType produceType) {
		MediaType produceTypeToUse = produceType.copyQualityValue(acceptType);
		return (MediaType.SPECIFICITY_COMPARATOR.compare(acceptType, produceTypeToUse) <= 0 ? acceptType : produceTypeToUse);
	}

	/**
	 * Check if the path has a file extension and whether the extension is
	 * either {@link #WHITELISTED_EXTENSIONS whitelisted} or explicitly
	 * {@link ContentNegotiationManager#getAllFileExtensions() registered}.
	 * If not, and the status is in the 2xx range, a 'Content-Disposition'
	 * header with a safe attachment file name ("f.txt") is added to prevent
	 * RFD exploits.
	 */
	private void addContentDispositionHeader(ServletServerHttpRequest request, ServletServerHttpResponse response) {
		HttpHeaders headers = response.getHeaders();
		// HTTP应答中，Content-Disposition消息头指示回复的内容该以何种形式展示，是以内联的形式（即网页或者页面的一部分），还是以附件的形式下载并保存到本地
		// 如果响响应头已经包含了该属性，则直接返回
		if (headers.containsKey(HttpHeaders.CONTENT_DISPOSITION)) {
			return;
		}

		try {
			int status = response.getServletResponse().getStatus();
			// 非成功响应则直接返回
			if (status < 200 || status > 299) {
				return;
			}
		}
		catch (Throwable ex) {
			// ignore
		}

		HttpServletRequest servletRequest = request.getServletRequest();
		String requestUri = rawUrlPathHelper.getOriginatingRequestUri(servletRequest);

		int index = requestUri.lastIndexOf('/') + 1;
		// 获取请求路径中最后一个/后面的字符串
		String filename = requestUri.substring(index);
		String pathParams = "";

		// 如果包含请求参数，则将其与filename分开
		index = filename.indexOf(';');
		if (index != -1) {
			pathParams = filename.substring(index);
			filename = filename.substring(0, index);
		}

		// decode filename
		filename = decodingUrlPathHelper.decodeRequestString(servletRequest, filename);
		// 返回filename的扩展名，"myfile.txt" -> "txt"
		String ext = StringUtils.getFilenameExtension(filename);

		// decode pathParams
		pathParams = decodingUrlPathHelper.decodeRequestString(servletRequest, pathParams);
		// 返回pathParams的扩展名，"myfile.txt" -> "txt"
		String extInPathParams = StringUtils.getFilenameExtension(pathParams);

		// 判断获取到的文件扩展名是否是支持的
		if (!safeExtension(servletRequest, ext) || !safeExtension(servletRequest, extInPathParams)) {
			headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt");
		}
	}

	@SuppressWarnings("unchecked")
	private boolean safeExtension(HttpServletRequest request, @Nullable String extension) {
		if (!StringUtils.hasText(extension)) {
			return true;
		}
		extension = extension.toLowerCase(Locale.ENGLISH);
		if (this.safeExtensions.contains(extension)) {
			return true;
		}

		// 如果请求的属性中已经包含了扩展名，则返回
		String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (pattern != null && pattern.endsWith("." + extension)) {
			return true;
		}
		if (extension.equals("html")) {
			String name = HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE;
			Set<MediaType> mediaTypes = (Set<MediaType>) request.getAttribute(name);
			if (!CollectionUtils.isEmpty(mediaTypes) && mediaTypes.contains(MediaType.TEXT_HTML)) {
				return true;
			}
		}
		return safeMediaTypesForExtension(new ServletWebRequest(request), extension);
	}

	private boolean safeMediaTypesForExtension(NativeWebRequest request, String extension) {
		List<MediaType> mediaTypes = null;
		try {
			mediaTypes = this.pathStrategy.resolveMediaTypeKey(request, extension);
		}
		catch (HttpMediaTypeNotAcceptableException ex) {
			// Ignore
		}
		if (CollectionUtils.isEmpty(mediaTypes)) {
			return false;
		}
		for (MediaType mediaType : mediaTypes) {
			if (!safeMediaType(mediaType)) {
				return false;
			}
		}
		return true;
	}

	private boolean safeMediaType(MediaType mediaType) {
		return (WHITELISTED_MEDIA_BASE_TYPES.contains(mediaType.getType()) ||
				mediaType.getSubtype().endsWith("+xml"));
	}

}
```

[HandlerMethodArgumentResolverd]: aaa
[PathVariable]: aaa
[HandlerMethodArgumentResolver]: aaa
[AbstractNamedValueMethodArgumentResolver]: aaa