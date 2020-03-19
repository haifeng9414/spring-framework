[ServletInvocableHandlerMethod]继承自[InvocableHandlerMethod]，而[InvocableHandlerMethod]表示一个可被调用的用于执行请求的方法，[InvocableHandlerMethod]在执行请求前还解析了方法参数，具体解析过程看代码，下面是![ServletInvocableHandlerMethod的基础结构](../../img/ServletInvocableHandlerMethod.png)

[HandlerMethod]类包含了方法的各种信息，代码：
```java
public class HandlerMethod {

	/** Logger that is available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());

	private final Object bean;

	@Nullable
	private final BeanFactory beanFactory;

	private final Class<?> beanType;

	// 传入构造函数的method
	private final Method method;

	/*
	 传入构造函数的method可能是个桥接方法，bridgedMethod方法是解析桥接方法之后得到的其对应的原始方法
	 桥接方法是JDK 1.5引入泛型后，为了使Java的泛型方法生成的字节码和1.5版本前的字节码相兼容，由编译器自动生成的方法。可以通过
	 Method.isBridge()方法来判断一个方法是否是桥接方法，在字节码中桥接方法会被标记为ACC_BRIDGE和ACC_SYNTHETIC，其中ACC_BRIDGE用于
	 说明这个方法是由编译生成的桥接方法，ACC_SYNTHETIC说明这个方法是由编译器生成，并且不会在源代码中出现。

	 举个例子：一个子类在继承（或实现）一个父类（或接口）的泛型方法时，在子类中明确指定了泛型类型，那么在编译时编译器会自动生成桥接方法
	 public interface SuperClass<T> {
	 	T method(T param);
	 }

	 public class SubClass implements SuperClass<String> {
	 	public String method(String param) {
	 		return param;
	 	}
	 }

	子类在编译后会自动加上一个方法：
	public Object method(Object param) {
        return this.method(((String) param));
	}

	这个方法就是桥接方法，为什么需要桥接方法，在JDK 1.5之前声明一个集合类型：
	List list = new ArrayList();

	那么往list中可以添加任何类型的对象，但是在从集合中获取对象时，无法确定获取到的对象是什么具体的类型，所以在1.5的时候引入了泛型，
	在声明集合的时候就指定集合中存放的是什么类型的对象：
	List<String> list = new ArrayList<String>();

	那么在获取时就不必担心类型的问题，因为泛型在编译时编译器会检查往集合中添加的对象的类型是否匹配泛型类型，如果不正确会在编译时就会发现错误，
	而不必等到运行时才发现错误。因为泛型是在1.5引入的，为了向前兼容，所以会在编译时去掉泛型（泛型擦除），但是我们还是可以通过反射API来获取
	泛型的信息，在编译时可以通过泛型来保证类型的正确性，而不必等到运行时才发现类型不正确。由于泛型的擦除特性，SuperClass类的方法在运行时参数
	时Object类型的，如果SubClass不生成桥接方法，那么SubClass类就没有实现SuperClass类的方法，这就与1.5之前的字节码就不兼容了
	 */
	private final Method bridgedMethod;

	// 每个MethodParameter对象都代表一个方法参数，默认实现为HandlerMethodParameter，从MethodParameter可以获取参数本身的信息和其在方法
	// 中的信息，如参数位置
	private final MethodParameter[] parameters;

	@Nullable
	// 响应的状态码，从ResponseStatus注解获取
	private HttpStatus responseStatus;

	@Nullable
	// 响应的状态信息，从ResponseStatus注解获取
	private String responseStatusReason;

	@Nullable
	private HandlerMethod resolvedFromHandlerMethod;

	public HandlerMethod(Object bean, Method method) {
		Assert.notNull(bean, "Bean is required");
		Assert.notNull(method, "Method is required");
		this.bean = bean;
		this.beanFactory = null;
		this.beanType = ClassUtils.getUserClass(bean);
		this.method = method;
		this.bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
		this.parameters = initMethodParameters();
		evaluateResponseStatus();
	}
  
	public HandlerMethod(Object bean, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		Assert.notNull(bean, "Bean is required");
		Assert.notNull(methodName, "Method name is required");
		this.bean = bean;
		this.beanFactory = null;
		this.beanType = ClassUtils.getUserClass(bean);
		this.method = bean.getClass().getMethod(methodName, parameterTypes);
		this.bridgedMethod = BridgeMethodResolver.findBridgedMethod(this.method);
		this.parameters = initMethodParameters();
		evaluateResponseStatus();
	}
  
	public HandlerMethod(String beanName, BeanFactory beanFactory, Method method) {
		Assert.hasText(beanName, "Bean name is required");
		Assert.notNull(beanFactory, "BeanFactory is required");
		Assert.notNull(method, "Method is required");
		this.bean = beanName;
		this.beanFactory = beanFactory;
		Class<?> beanType = beanFactory.getType(beanName);
		if (beanType == null) {
			throw new IllegalStateException("Cannot resolve bean type for bean with name '" + beanName + "'");
		}
		this.beanType = ClassUtils.getUserClass(beanType);
		this.method = method;
		this.bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
		this.parameters = initMethodParameters();
		evaluateResponseStatus();
	}
  
	protected HandlerMethod(HandlerMethod handlerMethod) {
		Assert.notNull(handlerMethod, "HandlerMethod is required");
		this.bean = handlerMethod.bean;
		this.beanFactory = handlerMethod.beanFactory;
		this.beanType = handlerMethod.beanType;
		this.method = handlerMethod.method;
		this.bridgedMethod = handlerMethod.bridgedMethod;
		this.parameters = handlerMethod.parameters;
		this.responseStatus = handlerMethod.responseStatus;
		this.responseStatusReason = handlerMethod.responseStatusReason;
		this.resolvedFromHandlerMethod = handlerMethod.resolvedFromHandlerMethod;
	}
  
	private HandlerMethod(HandlerMethod handlerMethod, Object handler) {
		Assert.notNull(handlerMethod, "HandlerMethod is required");
		Assert.notNull(handler, "Handler object is required");
		this.bean = handler;
		this.beanFactory = handlerMethod.beanFactory;
		this.beanType = handlerMethod.beanType;
		this.method = handlerMethod.method;
		this.bridgedMethod = handlerMethod.bridgedMethod;
		this.parameters = handlerMethod.parameters;
		this.responseStatus = handlerMethod.responseStatus;
		this.responseStatusReason = handlerMethod.responseStatusReason;
		this.resolvedFromHandlerMethod = handlerMethod;
	}


	private MethodParameter[] initMethodParameters() {
		int count = this.bridgedMethod.getParameterCount();
		MethodParameter[] result = new MethodParameter[count];
		for (int i = 0; i < count; i++) {
			HandlerMethodParameter parameter = new HandlerMethodParameter(i);
			GenericTypeResolver.resolveParameterType(parameter, this.beanType);
			result[i] = parameter;
		}
		return result;
	}

	private void evaluateResponseStatus() {
		ResponseStatus annotation = getMethodAnnotation(ResponseStatus.class);
		if (annotation == null) {
			annotation = AnnotatedElementUtils.findMergedAnnotation(getBeanType(), ResponseStatus.class);
		}
		if (annotation != null) {
			this.responseStatus = annotation.code();
			this.responseStatusReason = annotation.reason();
		}
	}
  
	public Object getBean() {
		return this.bean;
	}
  
	public Method getMethod() {
		return this.method;
	}
  
	public Class<?> getBeanType() {
		return this.beanType;
	}
  
	protected Method getBridgedMethod() {
		return this.bridgedMethod;
	}
  
	public MethodParameter[] getMethodParameters() {
		return this.parameters;
	}
  
	@Nullable
	protected HttpStatus getResponseStatus() {
		return this.responseStatus;
	}
  
	@Nullable
	protected String getResponseStatusReason() {
		return this.responseStatusReason;
	}
  
	public MethodParameter getReturnType() {
		return new HandlerMethodParameter(-1);
	}
  
	public MethodParameter getReturnValueType(@Nullable Object returnValue) {
		return new ReturnValueMethodParameter(returnValue);
	}
  
	public boolean isVoid() {
		return Void.TYPE.equals(getReturnType().getParameterType());
	}

	@Nullable
	public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
		return AnnotatedElementUtils.findMergedAnnotation(this.method, annotationType);
	}
  
	public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
		return AnnotatedElementUtils.hasAnnotation(this.method, annotationType);
	}
  
	@Nullable
	public HandlerMethod getResolvedFromHandlerMethod() {
		return this.resolvedFromHandlerMethod;
	}
  
	public HandlerMethod createWithResolvedBean() {
		Object handler = this.bean;
		if (this.bean instanceof String) {
			Assert.state(this.beanFactory != null, "Cannot resolve bean name without BeanFactory");
			String beanName = (String) this.bean;
			handler = this.beanFactory.getBean(beanName);
		}
		return new HandlerMethod(this, handler);
	}
  
	public String getShortLogMessage() {
		int args = this.method.getParameterCount();
		return getBeanType().getName() + "#" + this.method.getName() + "[" + args + " args]";
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof HandlerMethod)) {
			return false;
		}
		HandlerMethod otherMethod = (HandlerMethod) other;
		return (this.bean.equals(otherMethod.bean) && this.method.equals(otherMethod.method));
	}

	@Override
	public int hashCode() {
		return (this.bean.hashCode() * 31 + this.method.hashCode());
	}

	@Override
	public String toString() {
		return this.method.toGenericString();
	}


	/**
	 * A MethodParameter with HandlerMethod-specific behavior.
	 */
	protected class HandlerMethodParameter extends SynthesizingMethodParameter {

		public HandlerMethodParameter(int index) {
			super(HandlerMethod.this.bridgedMethod, index);
		}

		protected HandlerMethodParameter(HandlerMethodParameter original) {
			super(original);
		}

		@Override
		public Class<?> getContainingClass() {
			return HandlerMethod.this.getBeanType();
		}

		@Override
		public <T extends Annotation> T getMethodAnnotation(Class<T> annotationType) {
			return HandlerMethod.this.getMethodAnnotation(annotationType);
		}

		@Override
		public <T extends Annotation> boolean hasMethodAnnotation(Class<T> annotationType) {
			return HandlerMethod.this.hasMethodAnnotation(annotationType);
		}

		@Override
		public HandlerMethodParameter clone() {
			return new HandlerMethodParameter(this);
		}
	}

	/**
	 * A MethodParameter for a HandlerMethod return type based on an actual return value.
	 */
	private class ReturnValueMethodParameter extends HandlerMethodParameter {

		@Nullable
		private final Object returnValue;

		public ReturnValueMethodParameter(@Nullable Object returnValue) {
			super(-1);
			this.returnValue = returnValue;
		}

		protected ReturnValueMethodParameter(ReturnValueMethodParameter original) {
			super(original);
			this.returnValue = original.returnValue;
		}

		@Override
		public Class<?> getParameterType() {
			return (this.returnValue != null ? this.returnValue.getClass() : super.getParameterType());
		}

		@Override
		public ReturnValueMethodParameter clone() {
			return new ReturnValueMethodParameter(this);
		}
	}

}
```

[InvocableHandlerMethod]在[HandlerMethod]基础上提供了方法调用的能力，同时在调用方法之前对方法参数进行解析，代码：
```java
public class InvocableHandlerMethod extends HandlerMethod {

	@Nullable
	private WebDataBinderFactory dataBinderFactory;

	// HandlerMethodArgumentResolverComposite对象包含了多个HandlerMethodArgumentResolver对象，能够对方法参数进行解析
	private HandlerMethodArgumentResolverComposite argumentResolvers = new HandlerMethodArgumentResolverComposite();

	// ParameterNameDiscoverer用于获取方法的参数名称
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	public InvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}
	
	public InvocableHandlerMethod(Object bean, Method method) {
		super(bean, method);
	}
	
	public InvocableHandlerMethod(Object bean, String methodName, Class<?>... parameterTypes)
			throws NoSuchMethodException {

		super(bean, methodName, parameterTypes);
	}
	
	public void setDataBinderFactory(WebDataBinderFactory dataBinderFactory) {
		this.dataBinderFactory = dataBinderFactory;
	}
	
	public void setHandlerMethodArgumentResolvers(HandlerMethodArgumentResolverComposite argumentResolvers) {
		this.argumentResolvers = argumentResolvers;
	}
	
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}
	
	@Nullable
	public Object invokeForRequest(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {

		// 先调用argumentResolvers对当前方法的参数进行解析
		Object[] args = getMethodArgumentValues(request, mavContainer, providedArgs);
		if (logger.isTraceEnabled()) {
			logger.trace("Invoking '" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType()) +
					"' with arguments " + Arrays.toString(args));
		}
		// 调用方法
		Object returnValue = doInvoke(args);
		if (logger.isTraceEnabled()) {
			logger.trace("Method [" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType()) +
					"] returned [" + returnValue + "]");
		}
		return returnValue;
	}
	
	private Object[] getMethodArgumentValues(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {

		MethodParameter[] parameters = getMethodParameters();
		Object[] args = new Object[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			MethodParameter parameter = parameters[i];
			// 为MethodParameter设置parameterNameDiscoverer
			parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
			// 先判断providedArgs中是否有当前参数类型的，如果有则直接使用，providedArgs中的参数和当前参数的匹配是通过参数
			// 类型实现的，但是传入的providedArgs都是很特殊的参数，而且都是SpringMVC传入的，用户无法控制providedArgs，如WebDataBinder，
			// 所以这么实现是可以的
			args[i] = resolveProvidedArgument(parameter, providedArgs);
			if (args[i] != null) {
				continue;
			}
			// 如果providedArgs中没有找到可用的参数，则使用argumentResolvers解析参数
			if (this.argumentResolvers.supportsParameter(parameter)) {
				try {
					args[i] = this.argumentResolvers.resolveArgument(
							parameter, mavContainer, request, this.dataBinderFactory);
					continue;
				}
				catch (Exception ex) {
					if (logger.isDebugEnabled()) {
						logger.debug(getArgumentResolutionErrorMessage("Failed to resolve", i), ex);
					}
					throw ex;
				}
			}
			if (args[i] == null) {
				throw new IllegalStateException("Could not resolve method parameter at index " +
						parameter.getParameterIndex() + " in " + parameter.getExecutable().toGenericString() +
						": " + getArgumentResolutionErrorMessage("No suitable resolver for", i));
			}
		}
		return args;
	}

	private String getArgumentResolutionErrorMessage(String text, int index) {
		Class<?> paramType = getMethodParameters()[index].getParameterType();
		return text + " argument " + index + " of type '" + paramType.getName() + "'";
	}
	
	@Nullable
	private Object resolveProvidedArgument(MethodParameter parameter, @Nullable Object... providedArgs) {
		if (providedArgs == null) {
			return null;
		}
		for (Object providedArg : providedArgs) {
			if (parameter.getParameterType().isInstance(providedArg)) {
				return providedArg;
			}
		}
		return null;
	}
	
	protected Object doInvoke(Object... args) throws Exception {
		ReflectionUtils.makeAccessible(getBridgedMethod());
		try {
			return getBridgedMethod().invoke(getBean(), args);
		}
		catch (IllegalArgumentException ex) {
			assertTargetBean(getBridgedMethod(), getBean(), args);
			String text = (ex.getMessage() != null ? ex.getMessage() : "Illegal argument");
			throw new IllegalStateException(getInvocationErrorMessage(text, args), ex);
		}
		catch (InvocationTargetException ex) {
			// Unwrap for HandlerExceptionResolvers ...
			Throwable targetException = ex.getTargetException();
			if (targetException instanceof RuntimeException) {
				throw (RuntimeException) targetException;
			}
			else if (targetException instanceof Error) {
				throw (Error) targetException;
			}
			else if (targetException instanceof Exception) {
				throw (Exception) targetException;
			}
			else {
				String text = getInvocationErrorMessage("Failed to invoke handler method", args);
				throw new IllegalStateException(text, targetException);
			}
		}
	}
	
	private void assertTargetBean(Method method, Object targetBean, Object[] args) {
		Class<?> methodDeclaringClass = method.getDeclaringClass();
		Class<?> targetBeanClass = targetBean.getClass();
		if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
			String text = "The mapped handler method class '" + methodDeclaringClass.getName() +
					"' is not an instance of the actual controller bean class '" +
					targetBeanClass.getName() + "'. If the controller requires proxying " +
					"(e.g. due to @Transactional), please use class-based proxying.";
			throw new IllegalStateException(getInvocationErrorMessage(text, args));
		}
	}

	private String getInvocationErrorMessage(String text, Object[] resolvedArgs) {
		StringBuilder sb = new StringBuilder(getDetailedErrorMessage(text));
		sb.append("Resolved arguments: \n");
		for (int i = 0; i < resolvedArgs.length; i++) {
			sb.append("[").append(i).append("] ");
			if (resolvedArgs[i] == null) {
				sb.append("[null] \n");
			}
			else {
				sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
				sb.append("[value=").append(resolvedArgs[i]).append("]\n");
			}
		}
		return sb.toString();
	}
	
	protected String getDetailedErrorMessage(String text) {
		StringBuilder sb = new StringBuilder(text).append("\n");
		sb.append("HandlerMethod details: \n");
		sb.append("Controller [").append(getBeanType().getName()).append("]\n");
		sb.append("Method [").append(getBridgedMethod().toGenericString()).append("]\n");
		return sb.toString();
	}

}
```

[ServletInvocableHandlerMethod]在[InvocableHandlerMethod]的基础上提供了设置请求状态码和解析请求返回值的过程，代码：
```java
public class ServletInvocableHandlerMethod extends InvocableHandlerMethod {

	private static final Method CALLABLE_METHOD = ClassUtils.getMethod(Callable.class, "call");

	@Nullable
	// HandlerMethodReturnValueHandlerComposite包含了多个HandlerMethodReturnValueHandler，能够对方法de执行结果进行处理
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	public ServletInvocableHandlerMethod(Object handler, Method method) {
		super(handler, method);
	}
	
	public ServletInvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}
	
	public void setHandlerMethodReturnValueHandlers(HandlerMethodReturnValueHandlerComposite returnValueHandlers) {
		this.returnValueHandlers = returnValueHandlers;
	}
	
	public void invokeAndHandle(ServletWebRequest webRequest, ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {

		Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);
		// 如果存在responseStatus则设置到response中
		setResponseStatus(webRequest);

		if (returnValue == null) {
			// 如果请求的资源没有被修改过，或者存在responseStatus，则设置requestHandled为true，表示请求已经处理完成了
			if (isRequestNotModified(webRequest) || getResponseStatus() != null || mavContainer.isRequestHandled()) {
				mavContainer.setRequestHandled(true);
				return;
			}
		}
		else if (StringUtils.hasText(getResponseStatusReason())) {
			mavContainer.setRequestHandled(true);
			return;
		}

		// requestHandled属性表示请求是否被处理完成了，该属性的值影响之后是否需要创建ModelAndView
		mavContainer.setRequestHandled(false);
		Assert.state(this.returnValueHandlers != null, "No return value handlers");
		try {
			// 处理返回值
			this.returnValueHandlers.handleReturnValue(
					returnValue, getReturnValueType(returnValue), mavContainer, webRequest);
		}
		catch (Exception ex) {
			if (logger.isTraceEnabled()) {
				logger.trace(getReturnValueHandlingErrorMessage("Error handling return value", returnValue), ex);
			}
			throw ex;
		}
	}

	/**
	 * Set the response status according to the {@link ResponseStatus} annotation.
	 */
	private void setResponseStatus(ServletWebRequest webRequest) throws IOException {
		// 获取HttpStatus，HttpStatus从方法上的ResponseStatus注解获取到的
		HttpStatus status = getResponseStatus();
		if (status == null) {
			return;
		}

		// 如果status不为空，这里为response设置状态码和异常信息
		HttpServletResponse response = webRequest.getResponse();
		if (response != null) {
			String reason = getResponseStatusReason();
			if (StringUtils.hasText(reason)) {
				response.sendError(status.value(), reason);
			}
			else {
				response.setStatus(status.value());
			}
		}

		// To be picked up by RedirectView
		webRequest.getRequest().setAttribute(View.RESPONSE_STATUS_ATTRIBUTE, status);
	}
	
	private boolean isRequestNotModified(ServletWebRequest webRequest) {
		return webRequest.isNotModified();
	}

	private String getReturnValueHandlingErrorMessage(String message, @Nullable Object returnValue) {
		StringBuilder sb = new StringBuilder(message);
		if (returnValue != null) {
			sb.append(" [type=").append(returnValue.getClass().getName()).append("]");
		}
		sb.append(" [value=").append(returnValue).append("]");
		return getDetailedErrorMessage(sb.toString());
	}
	
	ServletInvocableHandlerMethod wrapConcurrentResult(Object result) {
		return new ConcurrentResultHandlerMethod(result, new ConcurrentResultMethodParameter(result));
	}
	
	private class ConcurrentResultHandlerMethod extends ServletInvocableHandlerMethod {

		private final MethodParameter returnType;

		public ConcurrentResultHandlerMethod(final Object result, ConcurrentResultMethodParameter returnType) {
			super((Callable<Object>) () -> {
				if (result instanceof Exception) {
					throw (Exception) result;
				}
				else if (result instanceof Throwable) {
					throw new NestedServletException("Async processing failed", (Throwable) result);
				}
				return result;
			}, CALLABLE_METHOD);

			if (ServletInvocableHandlerMethod.this.returnValueHandlers != null) {
				setHandlerMethodReturnValueHandlers(ServletInvocableHandlerMethod.this.returnValueHandlers);
			}
			this.returnType = returnType;
		}
		
		@Override
		public Class<?> getBeanType() {
			return ServletInvocableHandlerMethod.this.getBeanType();
		}
		
		@Override
		public MethodParameter getReturnValueType(@Nullable Object returnValue) {
			return this.returnType;
		}
		
		@Override
		public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.getMethodAnnotation(annotationType);
		}
		
		@Override
		public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.hasMethodAnnotation(annotationType);
		}
	}
	
	private class ConcurrentResultMethodParameter extends HandlerMethodParameter {

		@Nullable
		private final Object returnValue;

		private final ResolvableType returnType;

		public ConcurrentResultMethodParameter(Object returnValue) {
			super(-1);
			this.returnValue = returnValue;
			this.returnType = (returnValue instanceof ReactiveTypeHandler.CollectedValuesList ?
					((ReactiveTypeHandler.CollectedValuesList) returnValue).getReturnType() :
					ResolvableType.forType(super.getGenericParameterType()).getGeneric(0));
		}

		public ConcurrentResultMethodParameter(ConcurrentResultMethodParameter original) {
			super(original);
			this.returnValue = original.returnValue;
			this.returnType = original.returnType;
		}

		@Override
		public Class<?> getParameterType() {
			if (this.returnValue != null) {
				return this.returnValue.getClass();
			}
			if (!ResolvableType.NONE.equals(this.returnType)) {
				return this.returnType.resolve(Object.class);
			}
			return super.getParameterType();
		}

		@Override
		public Type getGenericParameterType() {
			return this.returnType.getType();
		}

		@Override
		public <T extends Annotation> boolean hasMethodAnnotation(Class<T> annotationType) {
			return (super.hasMethodAnnotation(annotationType) ||
					(annotationType == ResponseBody.class &&
							this.returnValue instanceof ReactiveTypeHandler.CollectedValuesList));
		}

		@Override
		public ConcurrentResultMethodParameter clone() {
			return new ConcurrentResultMethodParameter(this);
		}
	}

}
```

[InvocableHandlerMethod]在执行方法之前对参数进行了解析，用到了[HandlerMethodArgumentResolver]，该接口的实现可以看笔记[HandlerMethodArgumentResolver的实现.md](HandlerMethodArgumentResolver的实现.md)

[HandlerMethod]: aaa
[ServletInvocableHandlerMethod]: aaa
[InvocableHandlerMethod]: aaa
[HandlerMethodArgumentResolver]: aaa